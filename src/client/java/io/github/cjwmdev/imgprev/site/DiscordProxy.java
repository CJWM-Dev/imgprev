package io.github.cjwmdev.imgprev.site;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discord re-hosts links posted in chat as {@code images-ext-N.discordapp.net/external/<signature>/https/<host>/<path>}.
 * The original link is in the path, so it is unwrapped and handed to the site codecs like any other link; it still has
 * to pass their host checks.
 */
public final class DiscordProxy {
    static final Pattern HOST = Pattern.compile("^(?:images-ext-\\d{1,3}|media)\\.discordapp\\.net$");
    static final Pattern PATH = Pattern.compile("^/external/[A-Za-z0-9_-]+/https/([A-Za-z0-9.-]+)(/[^?#]*)$");

    private DiscordProxy() {}

    /** The proxied link, or empty if {@code u} isn't a proxy link. Discord's own query (width, format) is dropped. */
    public static Optional<URI> unwrap(URI u) {
        if (u.getHost() == null || !HOST.matcher(u.getHost().toLowerCase(Locale.ROOT)).matches() || u.getRawPath() == null) return Optional.empty();
        Matcher m = PATH.matcher(u.getRawPath());
        if (!m.matches()) return Optional.empty();
        try { return Optional.of(URI.create("https://" + m.group(1).toLowerCase(Locale.ROOT) + m.group(2))); }
        catch (IllegalArgumentException e) { return Optional.empty(); }
    }
}
