package dev.example.imgprev.chat;

import dev.example.imgprev.token.ResolvedMedia;
import dev.example.imgprev.token.TokenCodec;
import java.util.Optional;
import dev.example.imgprev.config.ImgPrevConfig;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Turns tokens in a chat line into {@code [▶ GIF]}-style labels whose ShowText hover carries the raw token. */
public final class TokenStyler {
    private TokenStyler() {}

    /** "[▶ GIF]", "[⧈ Image]", "[☺ Emoji]", "[✉ Discord]". */
    public static String labelText(String label) { return "[" + icon(label) + " " + label + "]"; }

    /** One symbol per kind of label, all from Minecraft's own bitmap font (include/default.json) so they stay crisp. */
    public static String icon(String label) {
        return switch (label) {
            case "GIF" -> "▶";
            case "Emoji" -> "☺";
            case "Discord" -> "✉";
            default -> "⧈";   // a framed picture, for "Image"
        };
    }

    /** {@link #apply(Component, Predicate, Function)} in the default colours. */
    public static Component apply(Component in, Predicate<ResolvedMedia> allowed) { return apply(in, allowed, ImgPrevConfig::defaultLabelColor); }

    /**
     * Returns {@code in} itself when nothing was replaced. A token split across two styled segments won't match;
     * acceptable since tokens are typed as plain text.
     */
    public static Component apply(Component in, Predicate<ResolvedMedia> allowed, Function<String, TextColor> colors) {
        if (!TokenCodec.TOKEN.matcher(in.getString()).find()) return in;
        MutableComponent out = Component.empty();
        boolean[] changed = {false};
        in.visit((style, text) -> {
            Matcher m = TokenCodec.TOKEN.matcher(text);
            int last = 0;
            while (m.find()) {
                Optional<ResolvedMedia> media = TokenCodec.decode(m).filter(allowed);
                if (media.isEmpty()) continue;
                if (m.start() > last) out.append(Component.literal(text.substring(last, m.start())).setStyle(style));
                out.append(label(m.group(), media.get(), style, colors.apply(media.get().codec().label())));
                last = m.end();
                changed[0] = true;
            }
            if (last < text.length()) out.append(Component.literal(text.substring(last)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? out : in;
    }

    static Component label(String token, ResolvedMedia media, Style style, TextColor color) {
        return Component.literal(labelText(media.codec().label()))
            .setStyle(style.withColor(color).withHoverEvent(new HoverEvent.ShowText(Component.literal(token))));
    }
}
