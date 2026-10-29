package io.github.cjwmdev.imgprev.render;

import com.mojang.blaze3d.platform.Window;
import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.image.AnimatedTexture;
import io.github.cjwmdev.imgprev.image.FrameDecoder;
import io.github.cjwmdev.imgprev.image.ImageCache;
import io.github.cjwmdev.imgprev.token.ResolvedMedia;
import io.github.cjwmdev.imgprev.token.TokenCodec;
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
import org.jspecify.annotations.Nullable;

/**
 * Hover previews go through vanilla's deferred tooltip (positioning, screen clamping, background, top stratum);
 * the image itself is a custom tooltip component (docs/VERIFIED.md §2).
 */
public final class ImageTooltip {
    private ImageTooltip() {}

    /** The token under the cursor, decoded once rather than on every frame it stays there. */
    private record Hovered(String token, Optional<ResolvedMedia> media, OptionalLong expiresAt) {}

    private static @Nullable Hovered last;

    public static void register() {
        ClientTooltipComponentCallback.EVENT.register(data -> data instanceof Preview p ? p : null);
    }

    /** {@link TokenCodec#decode(String)}, remembering the last token. Render thread only. */
    public static Optional<ResolvedMedia> resolve(String token) { return hovered(token).media(); }

    private static Hovered hovered(String token) {
        Hovered h = last;
        if (h == null || !h.token().equals(token)) {
            Optional<ResolvedMedia> media = TokenCodec.decode(token);
            last = h = new Hovered(token, media, media.map(m -> m.codec().expiresAtEpochSec(m.payload())).orElse(OptionalLong.empty()));
        }
        return h;
    }

    public static void show(GuiGraphicsExtractor g, Font font, String token, ResolvedMedia media, int mx, int my) {
        OptionalLong exp = hovered(token).expiresAt();
        if (exp.isPresent() && exp.getAsLong() < System.currentTimeMillis() / 1000) {
            failed(g, font, media.codec().expiredMessage(), -1, mx, my);
            return;
        }
        int[] box = box();
        ImageCache cache = ImgPrevClient.CACHE;
        ImageCache.Entry e = cache.getOrStart(media, box[0], box[1], Minecraft.getInstance().getWindow().getGuiScale());
        switch (e.state()) {
            case LOADING -> text(g, font, loading(e.attempt() > 1, cache.isSlow(e), media.codec().siteName()), mx, my);
            case FAILED -> failed(g, font, e.error(), cache.secondsUntilRetry(e), mx, my);
            case READY -> g.setTooltipForNextFrame(font, List.of(), Optional.of(new Preview(e.texture())), mx, my);
        }
    }

    /** "Loading image…", or after a failure "Trying again…"; past five seconds, which site is being slow. */
    static String loading(boolean retrying, boolean slow, String site) {
        if (!slow) return retrying ? "Trying again…" : "Loading image…";
        return (retrying ? "Still trying… " : "Still loading… ") + site + " is slow to answer";
    }

    /** The reason in red; under it, when it'll be tried again ({@code retryIn} -1: it won't). */
    static void failed(GuiGraphicsExtractor g, Font font, String why, long retryIn, int mx, int my) {
        List<Component> lines = retryIn < 0 ? List.of(Component.literal(why).withStyle(ChatFormatting.RED))
            : List.of(Component.literal(why).withStyle(ChatFormatting.RED), Component.literal(retryIn > 0
                ? "Trying again in " + retryIn + " s" : "Trying again shortly").withStyle(ChatFormatting.GRAY));
        g.setTooltipForNextFrame(font, lines, Optional.empty(), mx, my);
    }

    public static void text(GuiGraphicsExtractor g, Font font, String message, int mx, int my) {
        g.setTooltipForNextFrame(font, Component.literal(message).withStyle(ChatFormatting.GRAY), mx, my);
    }

    /** Largest preview in GUI units: the configured size times the scale, but at most half the screen's width and 60% of its height. */
    static int[] box() {
        Window win = Minecraft.getInstance().getWindow();
        int max = (int) Math.round(ImgPrevClient.CONFIG.maxDrawPx * ImgPrevClient.CONFIG.previewScale());
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

        /** The size it was loaded for, times the scale setting, shrunk if the window or settings have made the box smaller. */
        private int[] size() {
            int[] box = box();
            double scale = ImgPrevClient.CONFIG.previewScale();
            int w = Math.max(1, (int) Math.round(texture.displayW() * scale)), h = Math.max(1, (int) Math.round(texture.displayH() * scale));
            return FrameDecoder.fit(w, h, box[0], box[1]);
        }
    }
}
