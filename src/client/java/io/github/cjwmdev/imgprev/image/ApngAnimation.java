package io.github.cjwmdev.imgprev.image;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import org.jspecify.annotations.Nullable;

/**
 * The APNG chunks ImageIO's PNG reader skips: canvas size (IHDR), each frame's placement, delay and dispose/blend ops
 * (fcTL) and its image data (IDAT for a first frame that is also the default image, fdAT after). {@link #png} rebuilds
 * one frame as a plain PNG for ImageIO to decode. Discord's animated stickers are APNG.
 */
record ApngAnimation(int canvasW, int canvasH, byte[] data, Range ihdr, List<Range> shared, List<Frame> frames) {
    static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** Bytes {@code [start, start + length)} of the source file. */
    record Range(int start, int length) {}

    /** {@code dispose}: 0 none, 1 background, 2 previous. {@code blend}: 0 replaces the region, 1 draws over it. */
    record Frame(int x, int y, int w, int h, int delayMs, int dispose, int blend, List<Range> parts) {}

    /** {@code null} unless {@code data} is an animated PNG; a truncated file yields the frames before the cut. */
    static @Nullable ApngAnimation parse(byte[] data) {
        if (data.length < 8 || !ByteBuffer.wrap(data, 0, 8).equals(ByteBuffer.wrap(SIGNATURE))) return null;
        ByteBuffer b = ByteBuffer.wrap(data);
        Range ihdr = null;
        List<Range> shared = new ArrayList<>();
        boolean animated = false, seenIdat = false;
        List<Frame> frames = new ArrayList<>();
        Frame current = null;
        for (int pos = 8; pos + 12 <= data.length; ) {
            long size = Integer.toUnsignedLong(b.getInt(pos));
            int body = pos + 8;
            if (body + size + 4 > data.length) break;
            int len = (int) size;
            String type = new String(data, pos + 4, 4, StandardCharsets.US_ASCII);
            switch (type) {
                case "IHDR" -> { if (len == 13) ihdr = new Range(body, len); }
                case "acTL" -> animated = !seenIdat;   // only an acTL before the image data makes it animated
                case "fcTL" -> {
                    if (len < 26) break;
                    if (current != null && !current.parts().isEmpty()) frames.add(current);
                    int num = b.getShort(body + 20) & 0xFFFF, den = b.getShort(body + 22) & 0xFFFF;
                    current = new Frame(b.getInt(body + 12), b.getInt(body + 16), b.getInt(body + 4), b.getInt(body + 8),
                        (int) Math.min(Integer.MAX_VALUE, num * 1000L / (den == 0 ? 100 : den)), data[body + 24], data[body + 25], new ArrayList<>());
                }
                // IDAT is the first frame only when that frame's fcTL came first; otherwise it's a still that isn't part of the animation.
                case "IDAT" -> { seenIdat = true; if (current != null && frames.isEmpty()) current.parts().add(new Range(body, len)); }
                case "fdAT" -> { if (current != null && len > 4) current.parts().add(new Range(body + 4, len - 4)); }
                case "IEND" -> pos = data.length;
                default -> { if (!seenIdat) shared.add(new Range(pos, len + 12)); }   // PLTE, tRNS, gAMA, …: the whole chunk
            }
            if (pos < data.length) pos = body + len + 4;
        }
        if (current != null && !current.parts().isEmpty()) frames.add(current);
        if (!animated || ihdr == null || frames.isEmpty()) return null;
        return new ApngAnimation(b.getInt(ihdr.start()), b.getInt(ihdr.start() + 4), data, ihdr, List.copyOf(shared), List.copyOf(frames));
    }

    /** Frame {@code i} as a PNG of its own: the file's header chunks with the frame's size, then its image data. */
    byte[] png(int i) {
        Frame f = frames.get(i);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(SIGNATURE);
        byte[] head = new byte[13];
        System.arraycopy(data, ihdr.start(), head, 0, 13);
        ByteBuffer.wrap(head).putInt(0, f.w()).putInt(4, f.h());
        chunk(out, "IHDR", head);
        for (Range r : shared) out.write(data, r.start(), r.length());
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        for (Range r : f.parts()) idat.write(data, r.start(), r.length());
        chunk(out, "IDAT", idat.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    static void chunk(ByteArrayOutputStream out, String type, byte[] body) {
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(body);
        out.writeBytes(ByteBuffer.allocate(4).putInt(body.length).array());
        out.writeBytes(t);
        out.writeBytes(body);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
