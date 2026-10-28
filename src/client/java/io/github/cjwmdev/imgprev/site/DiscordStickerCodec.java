package io.github.cjwmdev.imgprev.site;

import io.github.cjwmdev.imgprev.token.Base62;
import io.github.cjwmdev.imgprev.token.Mixed;
import java.math.BigInteger;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sticker URLs are unsigned and don't expire. Payload: {@code g|p} (GIF/other) + Base62(snowflake).
 * <p>
 * PNG and APNG stickers are both {@code .png}, and APNG comes back animated whatever {@code size} asks for; the
 * {@code .webp} rendition is always a still, so it's read as {@code .png}. GIF stickers are only served from
 * media.discordapp.net (cdn.discordapp.com 404s them). Lottie stickers are JSON, with no image to preview.
 */
final class DiscordStickerCodec extends BaseCodec {
    static final Pattern PATH = Pattern.compile("^/stickers/(\\d{15,21})\\.(?i:(gif|png|webp))$");
    static final Pattern P = Pattern.compile("[gp][0-9A-Za-z]{1,11}");

    DiscordStickerCodec() {
        super('s', "sticker", "Discord stickers", Label.STICKER, Set.of("media.discordapp.net", "cdn.discordapp.com"), Set.of("media.discordapp.net"));
    }

    @Override public String siteName() { return "Discord"; }
    @Override public Pattern payloadPattern() { return P; }

    @Override public Optional<String> payloadFrom(URI u) {
        Matcher m = PATH.matcher(u.getPath());
        if (!m.matches()) return Optional.empty();
        return Optional.of((m.group(2).equalsIgnoreCase("gif") ? 'g' : 'p') + Base62.encode(new BigInteger(m.group(1))));
    }

    /** Snowflake and GIF flag in one number. */
    @Override public String compact(String p) {
        return new Mixed().putBits(Base62.decode(p.substring(1)), 64).put(p.charAt(0) == 'g' ? 1 : 0, 2).toBase62();
    }

    @Override public Optional<String> expand(String s) {
        return Mixed.fromBase62(s).flatMap(m -> {
            char kind = m.take(2) == 1 ? 'g' : 'p';
            String id = Base62.encode(m.takeBits(64));
            return m.isEmpty() ? Optional.of(kind + id) : Optional.empty();
        });
    }

    /** 320 px is the size Discord makes stickers at; larger custom ones are scaled down to it. */
    @Override public URI mediaUri(String p) {
        String id = Base62.decode(p.substring(1)).toString();
        if (!DiscordAttachmentCodec.SNOWFLAKE.matcher(id).matches()) throw new IllegalArgumentException("snowflake");
        String ext = p.charAt(0) == 'g' ? "gif" : "png";
        return URI.create("https://media.discordapp.net/stickers/" + id + "." + ext + "?size=320");
    }
}
