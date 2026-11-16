package io.github.cjwmdev.imgprev.image;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.cjwmdev.imgprev.mixin.GuiGraphicsInvoker;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * One GPU texture per frame, each uploaded the first time it's drawn so a long animation doesn't stall a single
 * frame. Frames are decoded at screen resolution and sampled linearly, so previews stay sharp at any GUI scale.
 * Render thread only.
 */
public final class AnimatedTexture {
    private final String label;
    private final int texW, texH, displayW, displayH, count;
    private final int[] endsMs;   // cumulative frame end times
    private final NativeImage[] pending;   // frames not uploaded yet
    private final GpuTexture[] textures;
    private final GpuTextureView[] views;
    private final long bytes;
    private boolean released;

    /** Takes ownership of {@code images}; {@code displayW×displayH} is the preview's size in GUI units. */
    public AnimatedTexture(String key, int texW, int texH, int[] delaysMs, NativeImage[] images, int displayW, int displayH) {
        this.label = "imgprev preview " + key;
        this.texW = texW; this.texH = texH; this.count = images.length;
        this.displayW = displayW; this.displayH = displayH;
        this.endsMs = new int[count];
        for (int i = 0, end = 0; i < count; i++) endsMs[i] = end += delaysMs[i];   // delays are capped, so no overflow
        this.pending = images;
        this.textures = new GpuTexture[count];
        this.views = new GpuTextureView[count];
        this.bytes = (long) texW * texH * 4 * count;
    }

    public static void close(NativeImage[] images) { for (NativeImage img : images) if (img != null) img.close(); }

    /** Draws the frame showing at {@code millis} into {@code w×h} GUI units. */
    public void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, long millis) {
        if (released) return;
        g.blit(view(frameAt(millis)), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, x + w, y + h, 0f, 1f, 0f, 1f);
    }

    /**
     * Draws the band {@code v0..v1} (0 top, 1 bottom) of the frame showing at {@code millis} into the GUI rectangle
     * {@code x0,y0..x1,y1}, tinted by {@code argb} (white with alpha to fade it).
     */
    public void drawPart(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float v0, float v1, int argb, long millis) {
        drawPart(g, x0, y0, x1, y1, v0, v1, argb, millis, true);
    }

    /** {@code smooth} false samples the nearest pixel, so zoomed-in images show their pixels sharply instead of blurred. */
    public void drawPart(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float v0, float v1, int argb, long millis, boolean smooth) {
        if (released) return;
        ((GuiGraphicsInvoker) g).imgprev$innerBlit(RenderPipelines.GUI_TEXTURED, view(frameAt(millis)),
            RenderSystem.getSamplerCache().getClampToEdge(smooth ? FilterMode.LINEAR : FilterMode.NEAREST), x0, y0, x1, y1, 0f, 1f, v0, v1, argb);
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

    /** When frame {@code i} starts within one loop, in ms; for stepping through a paused animation frame by frame. */
    public int frameStartMs(int i) { return i <= 0 ? 0 : endsMs[Math.min(i, count) - 1]; }

    public int frameAt(long millis) {
        int total = endsMs[count - 1];
        if (count == 1 || total <= 0) return 0;
        long t = millis % total;
        int i = Arrays.binarySearch(endsMs, (int) t + 1);
        return Math.min(count - 1, i >= 0 ? i : -i - 1);
    }

    /** Frees the GPU textures and any frames not yet uploaded; safe to call more than once. */
    public void release() {
        if (released) return;
        released = true;
        for (int i = 0; i < count; i++) {
            if (textures[i] != null) textures[i].close();   // same order as vanilla's AbstractTexture
            if (views[i] != null) views[i].close();
            if (pending[i] != null) pending[i].close();
        }
    }

    public boolean released() { return released; }
    public int displayW() { return displayW; }
    public int displayH() { return displayH; }
    public int textureW() { return texW; }
    public int textureH() { return texH; }
    public int frameCount() { return count; }
    public long bytes() { return bytes; }
}
