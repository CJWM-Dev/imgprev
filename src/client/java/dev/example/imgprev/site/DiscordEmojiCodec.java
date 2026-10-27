package dev.example.imgprev.site;

import dev.example.imgprev.token.Base62;
import java.math.BigInteger;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Emoji URLs are unsigned and don't expire. Payload: {@code a|s} (animated/static) + Base62(snowflake). */
final class DiscordEmojiCodec implements SiteCodec {
    static final Pattern PATH = Pattern.compile("^/emojis/(\\d{15,21})\\.(gif|png|webp)$", Pattern.CASE_INSENSITIVE);
    static final Pattern P = Pattern.compile("[as][0-9A-Za-z]{1,11}");
    private static final Set<String> HOSTS = Set.of("cdn.discordapp.com", "media.discordapp.net");
    private static final Set<String> DOWNLOAD = Set.of("cdn.discordapp.com");

    public char code() { return 'e'; }
    public String name() { return "emoji"; }
    public String label() { return "Emoji"; }
    public Set<String> acceptedHosts() { return HOSTS; }
    public Pattern payloadPattern() { return P; }

    public Optional<String> payloadFrom(URI u) {
        Matcher m = PATH.matcher(u.getPath());
        if (!m.matches()) return Optional.empty();
        // The Discord client links animated emoji as .webp?animated=true.
        boolean animated = m.group(2).equalsIgnoreCase("gif") || "true".equals(DiscordAttachmentCodec.query(u.getRawQuery()).get("animated"));
        return Optional.of((animated ? 'a' : 's') + Base62.encode(new BigInteger(m.group(1))));
    }

    public CompletableFuture<URI> mediaUri(String p) {
        String id = Base62.decode(p.substring(1)).toString();
        if (!DiscordAttachmentCodec.SNOWFLAKE.matcher(id).matches()) return CompletableFuture.failedFuture(new IllegalArgumentException("snowflake"));
        String ext = p.charAt(0) == 'a' ? "gif" : "png";
        return CompletableFuture.completedFuture(URI.create("https://cdn.discordapp.com/emojis/" + id + "." + ext + "?size=128"));
    }

    public Set<String> allowedDownloadHosts() { return DOWNLOAD; }
}
