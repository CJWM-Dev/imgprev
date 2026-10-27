package dev.example.imgprev.render;

import com.mojang.blaze3d.platform.Window;
import dev.example.imgprev.ImgPrevClient;
import dev.example.imgprev.image.AnimatedTexture;
import dev.example.imgprev.image.ImageCache;
import dev.example.imgprev.token.ResolvedMedia;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Hover previews go through vanilla's deferred tooltip (positioning, screen clamping, background, top stratum);
 * the image itself is a custom tooltip component (docs/VERIFIED.md §2).
 */
public final class ImageTooltip {
    private ImageTooltip() {}

    public static void register() {
        ClientTooltipComponentCallback.EVENT.register(data -> data instanceof Preview p ? p : null);
    }

    public static void show(GuiGraphicsExtractor g, Font font, ResolvedMedia media, int mx, int my) {
        OptionalLong exp = media.codec().expiresAtEpochSec(media.payload());
        if (exp.isPresent() && exp.getAsLong() < System.currentTimeMillis() / 1000) { text(g, font, "Discord link expired", mx, my); return; }
        ImageCache.Entry e = ImgPrevClient.CACHE.getOrStart(media);
        switch (e.state()) {
            case LOADING -> text(g, font, "Loading image…", mx, my);
            case FAILED -> text(g, font, e.error(), mx, my);
            case READY -> g.setTooltipForNextFrame(font, List.of(), Optional.of(new Preview(e.texture())), mx, my);
        }
    }

    public static void text(GuiGraphicsExtractor g, Font font, String message, int mx, int my) {
        g.setTooltipForNextFrame(font, Component.literal(message).withStyle(ChatFormatting.GRAY), mx, my);
    }

    /** Tooltip data and its renderer in one; {@link #register} maps it for vanilla. */
    public record Preview(AnimatedTexture texture) implements TooltipComponent, ClientTooltipComponent {
        @Override public int getWidth(Font font) { return drawW(); }
        @Override public int getHeight(Font font) { return drawH() + 2; }   // vanilla takes 2 off single-line tooltips

        @Override public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor g) {
            int frame = texture.frameAt(Util.getMillis());
            g.blit(RenderPipelines.GUI_TEXTURED, texture.id(), x, y, 0f, (float) (frame * texture.frameH()),
                drawW(), drawH(), texture.frameW(), texture.frameH(), texture.sheetW(), texture.sheetH());
        }

        /** Never upscaled past the decoded size; shrunk to fit half the screen width and 60% of its height. */
        private double scale() {
            Window win = Minecraft.getInstance().getWindow();
            return Math.min(1.0, Math.min(win.getGuiScaledWidth() * 0.5 / texture.frameW(), win.getGuiScaledHeight() * 0.6 / texture.frameH()));
        }
        private int drawW() { return Math.max(1, (int) (texture.frameW() * scale())); }
        private int drawH() { return Math.max(1, (int) (texture.frameH() * scale())); }
    }
}
