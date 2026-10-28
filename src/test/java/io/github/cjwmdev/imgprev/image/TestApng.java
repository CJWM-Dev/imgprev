package io.github.cjwmdev.imgprev.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/**
 * APNG writer for tests: each frame is written as a PNG by ImageIO, and its IHDR and image data are rearranged into
 * acTL/fcTL/fdAT chunks. Frame 0 sets the canvas size. With {@code hiddenDefault}, a still that isn't part of the
 * animation comes first, as the spec allows.
 */
final class TestApng {
    private TestApng() {}

    /** {@code dispose}: 0 none, 1 background, 2 previous. {@code blend}: 0 source, 1 over. */
    record Frame(BufferedImage img, int x, int y, int delayNum, int delayDen, int dispose, int blend) {}

    static byte[] animated(Frame... frames) throws IOException { return animated(null, frames); }

    static byte[] animated(BufferedImage hiddenDefault, Frame... frames) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ApngAnimation.SIGNATURE);
        ApngAnimation.chunk(out, "IHDR", chunkData(FrameDecoderTest.png(frames[0].img()), "IHDR"));
        ApngAnimation.chunk(out, "acTL", ByteBuffer.allocate(8).putInt(frames.length).putInt(0).array());
        if (hiddenDefault != null) ApngAnimation.chunk(out, "IDAT", chunkData(FrameDecoderTest.png(hiddenDefault), "IDAT"));
        int seq = 0;
        for (int i = 0; i < frames.length; i++) {
            Frame f = frames[i];
            ApngAnimation.chunk(out, "fcTL", ByteBuffer.allocate(26).putInt(seq++).putInt(f.img().getWidth()).putInt(f.img().getHeight())
                .putInt(f.x()).putInt(f.y()).putShort((short) f.delayNum()).putShort((short) f.delayDen())
                .put((byte) f.dispose()).put((byte) f.blend()).array());
            byte[] idat = chunkData(FrameDecoderTest.png(f.img()), "IDAT");
            if (i == 0 && hiddenDefault == null) ApngAnimation.chunk(out, "IDAT", idat);
            else ApngAnimation.chunk(out, "fdAT", ByteBuffer.allocate(4 + idat.length).putInt(seq++).put(idat).array());
        }
        ApngAnimation.chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    /** The body of every {@code type} chunk in a PNG, joined. */
    static byte[] chunkData(byte[] png, String type) {
        ByteBuffer b = ByteBuffer.wrap(png);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int pos = 8; pos + 12 <= png.length; ) {
            int len = b.getInt(pos);
            if (new String(png, pos + 4, 4, StandardCharsets.US_ASCII).equals(type)) out.write(png, pos + 8, len);
            pos += len + 12;
        }
        return out.toByteArray();
    }

    static BufferedImage read(byte[] png) throws IOException { return ImageIO.read(new java.io.ByteArrayInputStream(png)); }
}
