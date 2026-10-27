package dev.example.imgprev.chat;

import dev.example.imgprev.token.ResolvedMedia;
import dev.example.imgprev.token.TokenCodec;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Turns tokens in a chat line into {@code [🖼 GIF]}-style labels whose ShowText hover carries the raw token. */
public final class TokenStyler {
    static final int LABEL_COLOR = 0x55FFFF;

    private TokenStyler() {}

    /**
     * Returns {@code in} itself when nothing was replaced. A token split across two styled segments won't match;
     * acceptable since tokens are typed as plain text.
     */
    public static Component apply(Component in, Predicate<ResolvedMedia> allowed) {
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
                out.append(label(m.group(), media.get(), style));
                last = m.end();
                changed[0] = true;
            }
            if (last < text.length()) out.append(Component.literal(text.substring(last)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? out : in;
    }

    static Component label(String token, ResolvedMedia media, Style style) {
        return Component.literal("[🖼 " + media.codec().label() + "]")
            .setStyle(style.withColor(LABEL_COLOR).withHoverEvent(new HoverEvent.ShowText(Component.literal(token))));
    }
}
