package dev.example.imgprev.render;

import com.mojang.blaze3d.platform.Window;
import dev.example.imgprev.ImgPrevClient;
import dev.example.imgprev.image.AnimatedTexture;
import dev.example.imgprev.image.FrameDecoder;
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
        int[] box = box();
        ImageCache.Entry e = ImgPrevClient.CACHE.getOrStart(media, box[0], box[1], Minecraft.getInstance().getWindow().getGuiScale());
        switch (e.state()) {
            case LOADING -> text(g, font, "Loading image…", mx, my);
            case FAILED -> text(g, font, e.error(), mx, my);
            case READY -> g.setTooltipForNextFrame(font, List.of(), Optional.of(new Preview(e.texture())), mx, my);
        }
    }

    public static void text(GuiGraphicsExtractor g, Font font, String message, int mx, int my) {
        g.setTooltipForNextFrame(font, Component.literal(message).withStyle(ChatFormatting.GRAY), mx, my);
    }

    /** Largest preview in GUI units: the configured size, but at most half the screen's width and 60% of its height. */
    static int[] box() {
        Window win = Minecraft.getInstance().getWindow();
        int max = ImgPrevClient.CONFIG.maxDrawPx;
        return new int[] {Math.clamp(win.getGuiScaledWidth() / 2, 1, max), Math.clamp(win.getGuiScaledHeight() * 3 / 5, 1, max)};
    }

    /** Tooltip data and its renderer in one; {@link #register} maps it for vanilla. */
    public record Preview(AnimatedTexture texture) implements TooltipComponent, ClientTooltipComponent {
        @Override public int getWidth(Font font) { return size()[0]; }
        @Override public int getHeight(Font font) { return size()[1] + 2; }   // vanilla takes 2 off single-line tooltips

        @Override public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor g) {
            int[] size = size();
            texture.draw(g, x, y, size[0], size[1], Util.getMillis());
        }

        /** The size it was loaded for, shrunk if the window or settings have since made the box smaller. */
        private int[] size() {
            int[] box = box();
            return FrameDecoder.fit(texture.displayW(), texture.displayH(), box[0], box[1]);
        }
    }
}
