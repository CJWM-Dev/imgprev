package dev.example.imgprev.image;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** One GPU texture holding every frame as a vertical sprite sheet; drawn only through GuiGraphicsExtractor.blit. */
public final class AnimatedTexture {
    private final Identifier id;
    private final int frameW, frameH, count;
    private final int[] endsMs;   // cumulative frame end times
    private final long bytes;

    private AnimatedTexture(Identifier id, FrameDecoder.Frames f) {
        this.id = id;
        this.frameW = f.w(); this.frameH = f.h(); this.count = f.count();
        this.endsMs = f.delaysMs().clone();
        Arrays.parallelPrefix(endsMs, Integer::sum);
        this.bytes = (long) f.w() * f.sheetH() * 4;
    }

    /** Off-thread: copies the frames into native memory so the render thread only has to upload. */
    public static NativeImage toSheet(FrameDecoder.Frames f) {
        NativeImage img = new NativeImage(f.w(), f.sheetH(), false);
        int[] px = f.argb();
        for (int y = 0; y < f.sheetH(); y++) for (int x = 0; x < f.w(); x++) img.setPixel(x, y, px[y * f.w() + x]);   // ARGB in
        return img;
    }

    /** Render thread only (DynamicTexture uploads in its constructor). Takes ownership of {@code sheet}. */
    public static AnimatedTexture upload(String key, FrameDecoder.Frames f, NativeImage sheet) {
        Identifier id = Identifier.fromNamespaceAndPath("imgprev", "preview/" + sha1(key));   // paths must be lowercase
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "imgprev preview " + key, sheet));
        return new AnimatedTexture(id, f);
    }

    public int frameAt(long millis) {
        int total = endsMs[count - 1];
        if (count == 1 || total <= 0) return 0;
        long t = millis % total;
        int i = Arrays.binarySearch(endsMs, (int) t + 1);
        return Math.min(count - 1, i >= 0 ? i : -i - 1);
    }

    public void release() { Minecraft.getInstance().getTextureManager().release(id); }

    public Identifier id() { return id; }
    public int frameW() { return frameW; }
    public int frameH() { return frameH; }
    public int frameCount() { return count; }
    public int sheetW() { return frameW; }
    public int sheetH() { return frameH * count; }
    public long bytes() { return bytes; }

    static String sha1(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
