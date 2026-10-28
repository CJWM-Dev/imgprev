package io.github.cjwmdev.imgprev.chat;

import io.github.cjwmdev.imgprev.config.ImgPrevConfig;
import io.github.cjwmdev.imgprev.site.Label;
import io.github.cjwmdev.imgprev.token.ResolvedMedia;
import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.util.Optional;
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

    /** {@link #apply(Component, Predicate, Function)} in the default colours. */
    public static Component apply(Component in, Predicate<ResolvedMedia> allowed) { return apply(in, allowed, ImgPrevConfig::defaultLabelColor); }

    /**
     * Returns {@code in} itself when nothing was replaced. A token split across two styled segments won't match;
     * acceptable since tokens are typed as plain text.
     */
    public static Component apply(Component in, Predicate<ResolvedMedia> allowed, Function<Label, TextColor> colors) {
        if (!TokenCodec.TOKEN.matcher(in.getString()).find()) return in;
        MutableComponent out = Component.empty();
        boolean[] changed = {false};   // set inside the visitor
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
        return Component.literal(media.codec().label().labelText())
            .setStyle(style.withColor(color).withHoverEvent(new HoverEvent.ShowText(Component.literal(token))));
    }
}
