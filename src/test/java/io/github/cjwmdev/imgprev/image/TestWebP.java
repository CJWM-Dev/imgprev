package io.github.cjwmdev.imgprev.image;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Minimal WebP writer for tests: solid-colour lossless (VP8L) frames, still or animated. With a single symbol in
 * every prefix code, pixels take zero bits, so a frame is just its header and five tiny codes.
 */
final class TestWebP {
    private TestWebP() {}

    record Frame(int x, int y, int w, int h, int argb, int durationMs, boolean blend, boolean dispose) {}

    static byte[] still(int w, int h, int argb) { return riff(chunk("VP8L", vp8l(w, h, argb))); }

    static byte[] animated(int canvasW, int canvasH, Frame... frames) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] vp8x = new byte[10];
        vp8x[0] = 0x02 | 0x10;   // animation + alpha
        put24(vp8x, 4, canvasW - 1);
        put24(vp8x, 7, canvasH - 1);
        body.writeBytes(chunk("VP8X", vp8x));
        body.writeBytes(chunk("ANIM", new byte[6]));   // transparent background, loop forever
        for (Frame f : frames) {
            ByteArrayOutputStream anmf = new ByteArrayOutputStream();
            byte[] head = new byte[16];
            put24(head, 0, f.x() / 2);
            put24(head, 3, f.y() / 2);
            put24(head, 6, f.w() - 1);
            put24(head, 9, f.h() - 1);
            put24(head, 12, f.durationMs());
            head[15] = (byte) ((f.blend() ? 0 : 0x02) | (f.dispose() ? 0x01 : 0));
            anmf.writeBytes(head);
            anmf.writeBytes(chunk("VP8L", vp8l(f.w(), f.h(), f.argb())));
            body.writeBytes(chunk("ANMF", anmf.toByteArray()));
        }
        return riff(body.toByteArray());
    }

    static byte[] vp8l(int w, int h, int argb) {
        Bits bits = new Bits();
        bits.write(0x2f, 8);                       // signature
        bits.write(w - 1, 14);
        bits.write(h - 1, 14);
        bits.write((argb >>> 24) == 0xFF ? 0 : 1, 1);   // alpha_is_used
        bits.write(0, 3);                          // version
        bits.write(0, 1);                          // no transforms
        bits.write(0, 1);                          // no colour cache
        bits.write(0, 1);                          // no meta prefix codes
        for (int symbol : new int[] {(argb >> 8) & 0xFF, (argb >> 16) & 0xFF, argb & 0xFF, argb >>> 24, 0}) {
            bits.write(1, 1);                      // simple code: green, red, blue, alpha, distance
            bits.write(0, 1);                      // one symbol
            bits.write(1, 1);                      // 8-bit symbol
            bits.write(symbol, 8);
        }
        return bits.toBytes();
    }

    static byte[] riff(byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le32(4 + body.length));
        out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(body);
        return out.toByteArray();
    }

    static byte[] chunk(String fourCC, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(fourCC.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le32(payload.length));
        out.writeBytes(payload);
        if ((payload.length & 1) != 0) out.write(0);
        return out.toByteArray();
    }

    static byte[] le32(int v) { return new byte[] {(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)}; }
    static void put24(byte[] b, int at, int v) { b[at] = (byte) v; b[at + 1] = (byte) (v >> 8); b[at + 2] = (byte) (v >> 16); }

    /** VP8L packs bits least-significant first. */
    static final class Bits {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private long acc;
        private int n;

        void write(int value, int count) {
            acc |= (long) (value & ((1 << count) - 1)) << n;
            n += count;
            while (n >= 8) { out.write((int) (acc & 0xFF)); acc >>>= 8; n -= 8; }
        }

        byte[] toBytes() {
            if (n > 0) { out.write((int) (acc & 0xFF)); acc = 0; n = 0; }
            return out.toByteArray();
        }
    }
}
