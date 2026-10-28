package io.github.cjwmdev.imgprev.image;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The animated-WebP RIFF chunks TwelveMonkeys reads but doesn't expose: canvas size (VP8X) and each frame's
 * placement, duration and blend/dispose flags (ANMF). Frame i lines up with {@code ImageReader#read(i)}.
 */
record WebPAnimation(int canvasW, int canvasH, List<Frame> frames) {
    record Frame(int x, int y, int w, int h, int durationMs, boolean blend, boolean dispose) {}

    /** {@code null} unless {@code data} is an animated WebP; a truncated file yields the frames before the cut. */
    static @Nullable WebPAnimation parse(byte[] data) {
        if (data.length < 12 || !tag(data, 0, "RIFF") || !tag(data, 8, "WEBP")) return null;
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        long end = Math.min(data.length, 8L + Integer.toUnsignedLong(b.getInt(4)));
        int canvasW = 0, canvasH = 0;
        boolean animated = false;
        List<Frame> frames = new ArrayList<>();
        for (long pos = 12; pos + 8 <= end; ) {
            int at = (int) pos;
            long size = Integer.toUnsignedLong(b.getInt(at + 4));
            int body = at + 8;
            if (body + size > end) break;
            if (tag(data, at, "VP8X") && size >= 10) {
                animated = (data[body] & 0x02) != 0;
                canvasW = u24(data, body + 4) + 1;
                canvasH = u24(data, body + 7) + 1;
            } else if (tag(data, at, "ANMF") && size >= 16) {
                int flags = data[body + 15];
                frames.add(new Frame(2 * u24(data, body), 2 * u24(data, body + 3), u24(data, body + 6) + 1, u24(data, body + 9) + 1,
                    u24(data, body + 12), (flags & 0x02) == 0, (flags & 0x01) != 0));
            }
            pos = body + size + (size & 1);   // chunks are padded to even length
        }
        return animated && !frames.isEmpty() ? new WebPAnimation(canvasW, canvasH, List.copyOf(frames)) : null;
    }

    private static boolean tag(byte[] d, int at, String fourCC) {
        return at + 4 <= d.length && new String(d, at, 4, StandardCharsets.US_ASCII).equals(fourCC);
    }

    private static int u24(byte[] d, int at) { return (d[at] & 0xFF) | (d[at + 1] & 0xFF) << 8 | (d[at + 2] & 0xFF) << 16; }
}
