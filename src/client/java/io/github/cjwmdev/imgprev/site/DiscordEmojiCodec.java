package io.github.cjwmdev.imgprev.site;

import io.github.cjwmdev.imgprev.token.Base62;
import io.github.cjwmdev.imgprev.token.Mixed;
import java.math.BigInteger;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Emoji URLs are unsigned and don't expire. Payload: {@code a|s} (animated/static) + Base62(snowflake). */
final class DiscordEmojiCodec extends BaseCodec {
    static final Pattern PATH = Pattern.compile("^/emojis/(\\d{15,21})\\.(?i:(gif|png|webp))$");
    static final Pattern P = Pattern.compile("[as][0-9A-Za-z]{1,11}");

    DiscordEmojiCodec() {
        super('e', "emoji", "Discord emoji", Label.EMOJI, Set.of("cdn.discordapp.com", "media.discordapp.net"), Set.of("cdn.discordapp.com"));
    }

    @Override public String siteName() { return "Discord"; }
    @Override public Pattern payloadPattern() { return P; }

    @Override public Optional<String> payloadFrom(URI u) {
        Matcher m = PATH.matcher(u.getPath());
        if (!m.matches()) return Optional.empty();
        // The Discord client links animated emoji as .webp?animated=true.
        boolean animated = m.group(2).equalsIgnoreCase("gif") || "true".equals(DiscordAttachmentCodec.query(u.getRawQuery()).get("animated"));
        return Optional.of((animated ? 'a' : 's') + Base62.encode(new BigInteger(m.group(1))));
    }

    /** Snowflake and animated flag in one number. */
    @Override public String compact(String p) {
        return new Mixed().putBits(Base62.decode(p.substring(1)), 64).put(p.charAt(0) == 'a' ? 1 : 0, 2).toBase62();
    }

    @Override public Optional<String> expand(String s) {
        return Mixed.fromBase62(s).flatMap(m -> {
            char kind = m.take(2) == 1 ? 'a' : 's';
            String id = Base62.encode(m.takeBits(64));
            return m.isEmpty() ? Optional.of(kind + id) : Optional.empty();
        });
    }

    @Override public URI mediaUri(String p) {
        String id = Base62.decode(p.substring(1)).toString();
        if (!DiscordAttachmentCodec.SNOWFLAKE.matcher(id).matches()) throw new IllegalArgumentException("snowflake");
        String ext = p.charAt(0) == 'a' ? "gif" : "png";
        return URI.create("https://cdn.discordapp.com/emojis/" + id + "." + ext + "?size=128");
    }
}
