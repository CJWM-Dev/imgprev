package dev.example.imgprev.image;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One GPU texture per frame, each uploaded the first time it's drawn so a long animation doesn't stall a single
 * frame. Frames are decoded at screen resolution and sampled linearly, so previews stay sharp at any GUI scale.
 * Render thread only, apart from {@link #toImages}.
 */
public final class AnimatedTexture {
    private final String label;
    private final int texW, texH, displayW, displayH, count;
    private final int[] endsMs;   // cumulative frame end times
    private final NativeImage[] pending;   // frames not uploaded yet
    private final GpuTexture[] textures;
    private final GpuTextureView[] views;
    private final long bytes;

    /** Takes ownership of {@code images}; {@code displayW×displayH} is the preview's size in GUI units. */
    public AnimatedTexture(String key, FrameDecoder.Frames f, NativeImage[] images, int displayW, int displayH) {
        this.label = "imgprev preview " + key;
        this.texW = f.w(); this.texH = f.h(); this.count = f.count();
        this.displayW = displayW; this.displayH = displayH;
        this.endsMs = f.delaysMs().clone();
        Arrays.parallelPrefix(endsMs, Integer::sum);
        this.pending = images;
        this.textures = new GpuTexture[count];
        this.views = new GpuTextureView[count];
        this.bytes = (long) texW * texH * 4 * count;
    }

    /** Off-thread: copies the frames into native memory so the render thread only has to upload. */
    public static NativeImage[] toImages(FrameDecoder.Frames f) {
        List<int[]> frames = f.frames();
        NativeImage[] out = new NativeImage[frames.size()];
        for (int i = 0; i < out.length; i++) {
            int[] px = frames.get(i);
            out[i] = new NativeImage(f.w(), f.h(), false);
            for (int y = 0; y < f.h(); y++) for (int x = 0; x < f.w(); x++) out[i].setPixel(x, y, px[y * f.w() + x]);   // ARGB in
        }
        return out;
    }

    public static void close(NativeImage[] images) { for (NativeImage img : images) if (img != null) img.close(); }

    /** Draws the frame showing at {@code millis} into {@code w×h} GUI units. */
    public void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, long millis) {
        g.blit(view(frameAt(millis)), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, x + w, y + h, 0f, 1f, 0f, 1f);
    }

    private GpuTextureView view(int i) {
        if (views[i] == null) {
            GpuDevice device = RenderSystem.getDevice();
            textures[i] = device.createTexture(label, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, texW, texH, 1, 1);
            device.createCommandEncoder().writeToTexture(textures[i], pending[i]);   // copied out straight away (GL and Vulkan)
            views[i] = device.createTextureView(textures[i]);
            pending[i].close();
            pending[i] = null;
        }
        return views[i];
    }

    public int frameAt(long millis) {
        int total = endsMs[count - 1];
        if (count == 1 || total <= 0) return 0;
        long t = millis % total;
        int i = Arrays.binarySearch(endsMs, (int) t + 1);
        return Math.min(count - 1, i >= 0 ? i : -i - 1);
    }

    public void release() {
        for (int i = 0; i < count; i++) {
            if (textures[i] != null) { textures[i].close(); views[i].close(); }
            if (pending[i] != null) pending[i].close();
        }
    }

    public int displayW() { return displayW; }
    public int displayH() { return displayH; }
    public int textureW() { return texW; }
    public int textureH() { return texH; }
    public int frameCount() { return count; }
    public long bytes() { return bytes; }
}
