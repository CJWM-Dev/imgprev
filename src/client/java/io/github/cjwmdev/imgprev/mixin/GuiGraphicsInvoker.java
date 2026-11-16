package io.github.cjwmdev.imgprev.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The public texture-view blit always draws opaque; images in chat fade out with their message, so they need the colour. */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsInvoker {
    @Invoker("innerBlit")
    void imgprev$innerBlit(RenderPipeline pipeline, GpuTextureView view, GpuSampler sampler,
                           int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1, int color);
}
