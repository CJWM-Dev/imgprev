package dev.example.imgprev.image;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.spi.IIORegistry;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * GIF/PNG/JPEG/WebP bytes → frames scaled to fit {@code maxPx}, stacked into one vertical sheet. Runs off-thread.
 * Pure Java: javax.imageio (+ the bundled TwelveMonkeys WebP reader) for decoding, plain int[] for
 * compositing/scaling (no AWT drawing, no Toolkit).
 */
public final class FrameDecoder {
    public static final int MAX_SOURCE_PX = 2048;
    static final int MAX_FRAMES = 64, MAX_SHEET_H = 8192;
    /** Bounds compositing work for long, large animations (frames × canvas pixels). */
    static final long MAX_COMPOSITE_PIXELS = 400_000_000L;
    static final int MIN_DELAY_MS = 20, SLOW_DELAY_MS = 100;
    static final String GIF_STREAM = "javax_imageio_gif_stream_1.0", GIF_IMAGE = "javax_imageio_gif_image_1.0";

    static {
        // ImageIO's own plugin scan can't be relied on to see jars nested in a Fabric mod, so register by hand.
        IIORegistry.getDefaultInstance().registerServiceProvider(new WebPImageReaderSpi());
    }

    private FrameDecoder() {}

    /** {@code count} frames of {@code w×h} stacked top to bottom in {@code argb}; frame i shows for {@code delaysMs[i]}. */
    public record Frames(int w, int h, int count, int[] argb, int[] delaysMs) {
        public int sheetH() { return h * count; }
    }

    enum Dispose { NONE, BACKGROUND, PREVIOUS }

    /** Where a source frame goes on the canvas, how long it shows, and what happens to it afterwards. */
    record Placement(int left, int top, int delayMs, Dispose dispose, boolean blend) {}

    @FunctionalInterface
    interface Placements { Placement at(int index) throws IOException; }

    public static Frames decode(byte[] data, int maxPx) throws IOException {
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new MediaException("Unsupported image format");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, false, false);
                checkSize(reader.getWidth(0), reader.getHeight(0));   // header only, before any pixels are decoded
                return switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "gif" -> gif(reader, maxPx);
                    case "webp" -> {
                        WebPAnimation anim = WebPAnimation.parse(data);
                        yield anim == null ? still(reader.read(0), maxPx) : webp(reader, anim, maxPx);
                    }
                    default -> still(reader.read(0), maxPx);
                };
            } finally {
                reader.dispose();
            }
        }
    }

    static Frames still(BufferedImage img, int maxPx) {
        int w = img.getWidth(), h = img.getHeight();
        int[] size = fit(w, h, maxPx);
        return new Frames(size[0], size[1], 1, resize(img.getRGB(0, 0, w, h, null, 0, w), w, h, size[0], size[1]), new int[] {0});
    }

    static Frames gif(ImageReader reader, int maxPx) throws IOException {
        int[] screen = logicalScreen(reader);
        int total;
        try { total = reader.getNumImages(true); } catch (IOException e) { total = -1; }   // -1: unknown, read until it stops
        return animate(reader, screen[0], screen[1], total, maxPx, i -> gifPlacement((IIOMetadataNode) reader.getImageMetadata(i).getAsTree(GIF_IMAGE)));
    }

    static Frames webp(ImageReader reader, WebPAnimation anim, int maxPx) throws IOException {
        List<WebPAnimation.Frame> frames = anim.frames();
        return animate(reader, anim.canvasW(), anim.canvasH(), frames.size(), maxPx, i -> {
            WebPAnimation.Frame f = frames.get(i);
            return new Placement(f.x(), f.y(), normalizeDelay(f.durationMs()), f.dispose() ? Dispose.BACKGROUND : Dispose.NONE, f.blend());
        });
    }

    /** Composites source frames on a canvas; long animations keep every step-th frame and fold in the dropped frames' time. */
    static Frames animate(ImageReader reader, int cw, int ch, int total, int maxPx, Placements placements) throws IOException {
        checkSize(cw, ch);
        int budget = (int) Math.max(1, Math.min(Integer.MAX_VALUE, MAX_COMPOSITE_PIXELS / ((long) cw * ch)));
        int sourceFrames = total > 0 ? Math.min(total, budget) : budget;
        int[] out = fit(cw, ch, maxPx);
        int keep = Math.max(1, Math.min(MAX_FRAMES, MAX_SHEET_H / out[1]));
        int step = total > 0 ? Math.ceilDiv(sourceFrames, keep) : 1;

        int[] canvas = new int[cw * ch];
        List<int[]> frames = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        for (int i = 0; i < sourceFrames; i++) {
            BufferedImage img;
            Placement p;
            try {
                checkSize(reader.getWidth(i), reader.getHeight(i));
                img = reader.read(i);
                p = placements.at(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            } catch (IOException e) {
                if (i == 0) throw e;
                break;   // truncated file: keep what decoded
            }
            int[] previous = p.dispose() == Dispose.PREVIOUS ? canvas.clone() : null;

            paint(canvas, cw, ch, img, p.left(), p.top(), p.blend());
            if (i % step == 0) {
                if (frames.size() == keep) break;
                frames.add(resize(canvas, cw, ch, out[0], out[1]));
                delays.add(0);
            }
            delays.set(delays.size() - 1, delays.getLast() + p.delayMs());   // dropped frames add to the shown one

            if (p.dispose() == Dispose.BACKGROUND) clear(canvas, cw, ch, p.left(), p.top(), img.getWidth(), img.getHeight());
            else if (previous != null) canvas = previous;
        }

        int[] sheet = new int[out[0] * out[1] * frames.size()];
        for (int i = 0; i < frames.size(); i++) System.arraycopy(frames.get(i), 0, sheet, i * out[0] * out[1], out[0] * out[1]);
        return new Frames(out[0], out[1], frames.size(), sheet, delays.stream().mapToInt(Integer::intValue).toArray());
    }

    /** GIF transparency is 1-bit, so blending reduces to copying opaque pixels. */
    static Placement gifPlacement(IIOMetadataNode md) {
        IIOMetadataNode desc = child(md, "ImageDescriptor"), gce = child(md, "GraphicControlExtension");
        Dispose dispose = switch (gce == null ? "none" : gce.getAttribute("disposalMethod")) {
            case "restoreToBackgroundColor" -> Dispose.BACKGROUND;
            case "restoreToPrevious" -> Dispose.PREVIOUS;
            default -> Dispose.NONE;
        };
        return new Placement(intAttr(desc, "imageLeftPosition"), intAttr(desc, "imageTopPosition"), delayMs(gce), dispose, true);
    }

    /** GIF delays are in 1/100 s. */
    static int delayMs(IIOMetadataNode gce) { return normalizeDelay(gce == null ? 0 : intAttr(gce, "delayTime") * 10); }

    /** Like browsers, near-zero delays play at 100 ms. */
    static int normalizeDelay(int ms) { return ms < MIN_DELAY_MS ? SLOW_DELAY_MS : ms; }

    static int[] logicalScreen(ImageReader reader) throws IOException {
        int w = 0, h = 0;
        IIOMetadata sm = reader.getStreamMetadata();
        if (sm != null) {
            IIOMetadataNode lsd = child((IIOMetadataNode) sm.getAsTree(GIF_STREAM), "LogicalScreenDescriptor");
            if (lsd != null) { w = intAttr(lsd, "logicalScreenWidth"); h = intAttr(lsd, "logicalScreenHeight"); }
        }
        // Some encoders write a screen smaller than the first frame; grow to fit it.
        return new int[] {Math.max(w, reader.getWidth(0)), Math.max(h, reader.getHeight(0))};
    }

    /** Draws a frame onto the canvas: alpha-blended (source-over) or, with {@code blend} off, replacing pixels outright. */
    static void paint(int[] canvas, int cw, int ch, BufferedImage img, int left, int top, boolean blend) {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        for (int y = Math.max(0, -top); y < h && top + y < ch; y++) {
            for (int x = Math.max(0, -left); x < w && left + x < cw; x++) {
                int i = (top + y) * cw + left + x;
                canvas[i] = blend ? over(px[y * w + x], canvas[i]) : px[y * w + x];
            }
        }
    }

    /** Source-over in non-premultiplied ARGB. */
    static int over(int src, int dst) {
        int sa = src >>> 24;
        if (sa == 255) return src;
        if (sa == 0) return dst;
        int dw = (dst >>> 24) * (255 - sa) / 255;   // how much of dst shows through
        int a = sa + dw;
        int r = (((src >> 16) & 0xFF) * sa + ((dst >> 16) & 0xFF) * dw) / a;
        int g = (((src >> 8) & 0xFF) * sa + ((dst >> 8) & 0xFF) * dw) / a;
        int b = ((src & 0xFF) * sa + (dst & 0xFF) * dw) / a;
        return a << 24 | r << 16 | g << 8 | b;
    }

    static void clear(int[] canvas, int cw, int ch, int left, int top, int w, int h) {
        for (int y = Math.max(0, top); y < Math.min(ch, top + h); y++) {
            int from = y * cw + Math.max(0, left), to = y * cw + Math.min(cw, left + w);
            if (from < to) Arrays.fill(canvas, from, to, 0);
        }
    }

    static void checkSize(int w, int h) throws MediaException {
        if (w <= 0 || h <= 0 || w > MAX_SOURCE_PX || h > MAX_SOURCE_PX) throw new MediaException("Image too large (" + w + "×" + h + ")");
    }

    static int[] fit(int w, int h, int maxPx) {
        if (w <= maxPx && h <= maxPx) return new int[] {w, h};
        double s = (double) maxPx / Math.max(w, h);
        return new int[] {Math.max(1, (int) Math.round(w * s)), Math.max(1, (int) Math.round(h * s))};
    }

    /** Box-filter downscale with alpha-weighted colour (transparent pixels don't darken edges). */
    static int[] resize(int[] src, int sw, int sh, int dw, int dh) {
        if (sw == dw && sh == dh) return src.clone();
        int[] dst = new int[dw * dh];
        for (int dy = 0; dy < dh; dy++) {
            int y0 = (int) ((long) dy * sh / dh), y1 = Math.max(y0 + 1, (int) ((long) (dy + 1) * sh / dh));
            for (int dx = 0; dx < dw; dx++) {
                int x0 = (int) ((long) dx * sw / dw), x1 = Math.max(x0 + 1, (int) ((long) (dx + 1) * sw / dw));
                long a = 0, r = 0, g = 0, b = 0;
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        int p = src[y * sw + x], pa = p >>> 24;
                        a += pa; r += (long) ((p >> 16) & 0xFF) * pa; g += (long) ((p >> 8) & 0xFF) * pa; b += (long) (p & 0xFF) * pa;
                    }
                }
                if (a == 0) continue;
                int n = (y1 - y0) * (x1 - x0);
                dst[dy * dw + dx] = (int) (a / n) << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
            }
        }
        return dst;
    }

    static IIOMetadataNode child(IIOMetadataNode parent, String name) {
        for (var n = parent.getFirstChild(); n != null; n = n.getNextSibling()) if (name.equals(n.getNodeName())) return (IIOMetadataNode) n;
        return null;
    }

    static int intAttr(IIOMetadataNode node, String name) {
        if (node == null) return 0;
        try { return Integer.parseInt(node.getAttribute(name)); } catch (NumberFormatException e) { return 0; }
    }
}
