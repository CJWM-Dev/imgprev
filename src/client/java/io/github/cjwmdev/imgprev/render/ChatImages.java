package io.github.cjwmdev.imgprev.render;

import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.config.ImgPrevConfig;
import io.github.cjwmdev.imgprev.image.AnimatedTexture;
import io.github.cjwmdev.imgprev.image.FrameDecoder;
import io.github.cjwmdev.imgprev.image.ImageCache;
import io.github.cjwmdev.imgprev.token.ResolvedMedia;
import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Vector2fc;
import org.jspecify.annotations.Nullable;

/**
 * Images shown in chat (the "Show images in chat" setting). Chat is a list of fixed-height lines, so a message with
 * images gets blank {@link Row}s added under its text, enough to hold each image at the configured height; as chat
 * draws each row, the band of the image that falls on it is drawn there. Everything chat does with lines (scrolling,
 * fading, the background, the 100-line limit, re-wrapping when chat is resized) then works for images too, and the
 * messages around them move aside rather than being drawn over.
 */
public final class ChatImages {
    private ChatImages() {}

    /** Images shown under one message at most; any more tokens in it are still labels. */
    static final int MAX_PER_MESSAGE = 4;

    /** One blank chat line reserved for an image: {@code index} of {@code rows}, top first. Draws no text itself. */
    public record Row(String token, ResolvedMedia media, int index, int rows) implements FormattedCharSequence {
        @Override public boolean accept(FormattedCharSink output) { return true; }
    }

    /** Rows to add under a message whose labels {@code TokenStyler} has made; empty while the setting is off. */
    public static List<Row> rowsFor(Component content, ImgPrevConfig cfg, long nowEpochSec) {
        if (!cfg.renderEnabled || !cfg.imagesInChat) return List.of();
        List<Row> rows = new ArrayList<>();
        int images = 0;
        for (String token : tokens(content)) {
            Optional<ResolvedMedia> media = TokenCodec.decode(token).filter(cfg::allows);
            if (media.isEmpty() || expired(media.get(), nowEpochSec)) continue;
            for (int i = 0; i < cfg.chatImageLines; i++) rows.add(new Row(token, media.get(), i, cfg.chatImageLines));
            if (++images == MAX_PER_MESSAGE) break;
        }
        return rows;
    }

    /** The tokens behind a styled message's labels (carried in their hover text), in order, each once. */
    static Set<String> tokens(Component content) {
        Set<String> out = new LinkedHashSet<>();
        content.visit((style, text) -> {
            if (style.getHoverEvent() instanceof HoverEvent.ShowText(Component value)) {
                String token = value.getString();
                if (TokenCodec.TOKEN.matcher(token).matches()) out.add(token);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static boolean expired(ResolvedMedia m, long nowEpochSec) {
        OptionalLong exp = m.codec().expiresAtEpochSec(m.payload());
        return exp.isPresent() && exp.getAsLong() < nowEpochSec;
    }

    /**
     * The part of an image {@code imageH} tall, drawn from the top of its rows, that row {@code index} covers when
     * rows are {@code rowH} tall: {from, to} measured down from the image's top, or null if the row is below it.
     */
    static int @Nullable [] band(int index, int rowH, int imageH) {
        int from = index * rowH, to = Math.min(from + rowH, imageH);
        return from < to ? new int[] {from, to} : null;
    }

    /**
     * Called as chat draws a {@link Row}, under chat's own transform (scaled, origin at the line's left edge). Starts
     * the image loading if needed and draws this row's band of it at {@code opacity}; returns the text chat should
     * draw on the row instead: "Loading image…" or the error on the top row until it's ready, otherwise nothing.
     * {@code mouse} is the cursor in the same coordinates while chat is open (hovering the image shows the full
     * preview at {@code mx, my}), or null for the in-game HUD chat.
     */
    public static FormattedCharSequence draw(GuiGraphicsExtractor g, Row row, int textTop, float opacity,
                                             @Nullable Vector2fc mouse, Font font, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        double spacing = mc.options.chatLineSpacing().get();
        int rowH = (int) (9 * (spacing + 1.0));   // as ChatComponent lays lines out
        int rowTop = textTop + (int) Math.round(8.0 * (spacing + 1.0) - 4.0 * spacing) - rowH;
        int top = rowTop - row.index() * rowH;
        int slotW = Math.max(1, Mth.floor(ChatComponent.getWidth(mc.options.chatWidth().get()) / mc.options.chatScale().get()));
        int slotH = row.rows() * rowH;

        ImageCache.Entry e = ImgPrevClient.CACHE.getOrStart(row.media(), slotW, slotH, mc.getWindow().getGuiScale(), false);
        AnimatedTexture tex = e.texture();
        if (e.state() != ImageCache.State.READY || tex == null) return row.index() == 0 ? status(e, row.media()) : FormattedCharSequence.EMPTY;

        int[] size = FrameDecoder.fit(tex.displayW(), tex.displayH(), slotW, slotH);
        int[] band = band(row.index(), rowH, size[1]);
        if (band == null) return FormattedCharSequence.EMPTY;
        int y0 = top + band[0], y1 = top + band[1];
        tex.drawPart(g, 0, y0, size[0], y1, (float) band[0] / size[1], (float) band[1] / size[1], ARGB.color(opacity, 0xFFFFFF), Util.getMillis());

        ImgPrevConfig cfg = ImgPrevClient.CONFIG;
        if (mouse != null && mouse.x() >= 0 && mouse.x() < size[0] && mouse.y() >= y0 && mouse.y() < y1
                && (!cfg.requireShift || mc.hasShiftDown())) {
            ImageTooltip.show(g, font, row.token(), row.media(), mx, my);
        }
        return FormattedCharSequence.EMPTY;
    }

    private static FormattedCharSequence status(ImageCache.Entry e, ResolvedMedia m) {
        Component text = e.state() == ImageCache.State.FAILED ? Component.literal(e.error()).withStyle(ChatFormatting.RED)
            : Component.literal(ImageTooltip.loading(e.attempt() > 1, ImgPrevClient.CACHE.isSlow(e), m.codec().siteName())).withStyle(ChatFormatting.GRAY);
        return text.getVisualOrderText();
    }

    /** Lays chat out again so a changed setting applies to messages already there, not just new ones. */
    public static void refreshChat() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null && mc.gui.hud != null) mc.gui.hud.getChat().rescaleChat();
    }
}
