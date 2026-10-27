package dev.example.imgprev.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.junit.jupiter.api.Test;

class FrameDecoderTest {
    static final int RED = 0xFFFF0000, BLUE = 0xFF0000FF, GREEN = 0xFF00FF00, CLEAR = 0;

    record Frame(BufferedImage img, int left, int top, int delayCs, String disposal) {}

    static BufferedImage solid(int w, int h, int argb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, argb);
        return img;
    }

    static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    /** Animated GIF via ImageIO's writer; the first frame sets the logical screen size. */
    static byte[] gif(Frame... frames) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bos)) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (Frame f : frames) {
                ImageWriteParam p = writer.getDefaultWriteParam();
                IIOMetadata md = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(f.img()), p);
                IIOMetadataNode root = (IIOMetadataNode) md.getAsTree(FrameDecoder.GIF_IMAGE);
                IIOMetadataNode gce = FrameDecoder.child(root, "GraphicControlExtension");
                gce.setAttribute("delayTime", String.valueOf(f.delayCs()));
                gce.setAttribute("disposalMethod", f.disposal());
                IIOMetadataNode desc = FrameDecoder.child(root, "ImageDescriptor");
                desc.setAttribute("imageLeftPosition", String.valueOf(f.left()));
                desc.setAttribute("imageTopPosition", String.valueOf(f.top()));
                md.setFromTree(FrameDecoder.GIF_IMAGE, root);
                writer.writeToSequence(new IIOImage(f.img(), null, md), p);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return bos.toByteArray();
    }

    static int px(FrameDecoder.Frames f, int frame, int x, int y) { return f.frames().get(frame)[y * f.w() + x]; }

    @Test void stillImageIsScaledToFit() throws IOException {
        FrameDecoder.Frames f = FrameDecoder.decode(png(solid(600, 300, RED)), 256, 256);
        assertEquals(256, f.w());
        assertEquals(128, f.h());
        assertEquals(1, f.count());
        assertTrue(Arrays.stream(f.frames().getFirst()).allMatch(p -> p == RED));
    }

    @Test void smallImagesAreNotUpscaled() throws IOException {
        FrameDecoder.Frames f = FrameDecoder.decode(png(solid(40, 20, BLUE)), 256, 256);
        assertEquals(40, f.w());
        assertEquals(20, f.h());
    }

    @Test void fitsTheNarrowerSideOfTheBox() throws IOException {
        FrameDecoder.Frames f = FrameDecoder.decode(png(solid(600, 300, RED)), 1024, 100);
        assertEquals(200, f.w());
        assertEquals(100, f.h());
        assertEquals(600, f.srcW());
        assertEquals(300, f.srcH());
    }

    @Test void largeSourcesAreAccepted() throws IOException {
        FrameDecoder.Frames f = FrameDecoder.decode(png(solid(4000, 3000, RED)), 1024, 1024);   // phone photo size
        assertEquals(1024, f.w());
        assertEquals(768, f.h());
    }

    @Test void oversizeRejectedFromHeader() throws IOException {
        byte[] wide = png(solid(5000, 2, RED));
        MediaException e = assertThrows(MediaException.class, () -> FrameDecoder.decode(wide, 256, 256));
        assertTrue(e.getMessage().startsWith("Image too large"), e.getMessage());
    }

    @Test void garbageRejected() {
        MediaException e = assertThrows(MediaException.class, () -> FrameDecoder.decode("<html>nope</html>".getBytes(), 256, 256));
        assertEquals("Unsupported image format", e.getMessage());
    }

    @Test void gifCompositesOffsetsDelaysAndRestoreToBackground() throws IOException {
        FrameDecoder.Frames f = FrameDecoder.decode(gif(
            new Frame(solid(4, 4, RED), 0, 0, 5, "none"),
            new Frame(solid(2, 2, BLUE), 2, 2, 1, "restoreToBackgroundColor"),
            new Frame(solid(1, 1, GREEN), 0, 0, 20, "none")), 256, 256);
        assertEquals(3, f.count());
        assertEquals(4, f.w());
        assertArrayEquals(new int[] {50, 100, 200}, f.delaysMs());   // 10 ms is too fast: played at 100 ms
        assertEquals(RED, px(f, 0, 3, 3));
        assertEquals(BLUE, px(f, 1, 3, 3));
        assertEquals(RED, px(f, 1, 1, 1));
        assertEquals(CLEAR, px(f, 2, 3, 3));   // frame 1's area was cleared after it showed
        assertEquals(GREEN, px(f, 2, 0, 0));
        assertEquals(RED, px(f, 2, 1, 1));
    }

    @Test void gifRestoreToPrevious() throws IOException {
        FrameDecoder.Frames f = FrameDecoder.decode(gif(
            new Frame(solid(4, 4, RED), 0, 0, 10, "none"),
            new Frame(solid(4, 4, BLUE), 0, 0, 10, "restoreToPrevious"),
            new Frame(solid(1, 1, GREEN), 0, 0, 10, "none")), 256, 256);
        assertEquals(BLUE, px(f, 1, 2, 2));
        assertEquals(RED, px(f, 2, 2, 2));
        assertEquals(GREEN, px(f, 2, 0, 0));
    }

    @Test void longGifsDropFramesButKeepTotalDuration() throws IOException {
        Frame[] frames = new Frame[2 * FrameDecoder.MAX_FRAMES];
        for (int i = 0; i < frames.length; i++) frames[i] = new Frame(solid(2, 2, i % 2 == 0 ? RED : BLUE), 0, 0, 3, "none");
        FrameDecoder.Frames f = FrameDecoder.decode(gif(frames), 256, 256);
        assertEquals(FrameDecoder.MAX_FRAMES, f.count());   // every 2nd frame
        assertTrue(Arrays.stream(f.delaysMs()).allMatch(d -> d == 60));
        assertEquals(frames.length * 30, Arrays.stream(f.delaysMs()).sum());
    }

    @Test void largeFramesAreAllKept() throws IOException {
        Frame[] frames = new Frame[40];
        for (int i = 0; i < frames.length; i++) frames[i] = new Frame(solid(300, 300, i % 2 == 0 ? RED : BLUE), 0, 0, 10, "none");
        FrameDecoder.Frames f = FrameDecoder.decode(gif(frames), 1024, 1024);
        assertEquals(300, f.h());
        assertEquals(40, f.count());
        assertEquals(BLUE, px(f, 39, 299, 299));
    }

    @Test void bigLongAnimationsShrinkToThePixelBudget() {
        int[] size = FrameDecoder.withinBudget(1000, 500, FrameDecoder.MAX_FRAMES);
        assertTrue((long) size[0] * size[1] * FrameDecoder.MAX_FRAMES <= FrameDecoder.MAX_PIXELS, size[0] + "×" + size[1]);
        assertTrue(size[0] > 300, "still well above the old 256 px cap: " + size[0]);
        assertEquals(2.0, (double) size[0] / size[1], 0.01);
        assertArrayEquals(new int[] {480, 270}, FrameDecoder.withinBudget(480, 270, 100));   // a typical GIF fits as is
    }

    @Test void downscaleWeightsColourByAlpha() {
        int[] out = FrameDecoder.resize(new int[] {0xFFFFFFFF, 0x00000000}, 2, 1, 1, 1);
        assertEquals(127, out[0] >>> 24);
        assertEquals(0xFFFFFF, out[0] & 0xFFFFFF);   // the transparent black pixel doesn't darken it
    }
}
