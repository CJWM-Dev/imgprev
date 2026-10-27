package dev.example.imgprev.token;

import dev.example.imgprev.site.SiteCodec;
import dev.example.imgprev.site.SiteRegistry;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/** Token format v1: {@code "<~" ver site payload check "~>"}. See handoff §4. */
public final class TokenCodec {
    public static final Pattern TOKEN = Pattern.compile("<~([0-9])([a-z])([A-Za-z0-9-]{1,200})([0-9A-Za-z])~>");
    static final int MAX_PAYLOAD = 200;

    private TokenCodec() {}

    static char check(String body) {
        CRC32 crc = new CRC32(); crc.update(body.getBytes(StandardCharsets.US_ASCII));
        return Base62.A.charAt((int) (crc.getValue() % 62));
    }

    public static Optional<String> encode(String url) {
        URI uri;
        try { uri = new URI(url); } catch (URISyntaxException e) { return Optional.empty(); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return Optional.empty();
        // Several codecs can share a host (Discord emoji + attachments), so try each until one accepts the path.
        for (SiteCodec c : SiteRegistry.byHost(uri.getHost().toLowerCase(Locale.ROOT))) {
            Optional<String> p = c.payloadFrom(uri).filter(s -> s.length() <= MAX_PAYLOAD && c.payloadPattern().matcher(s).matches());
            if (p.isPresent()) { String body = "1" + c.code() + p.get(); return Optional.of("<~" + body + check(body) + "~>"); }
        }
        return Optional.empty();
    }

    /** Whole-string variant of {@link #decode(Matcher)}. */
    public static Optional<ResolvedMedia> decode(String token) {
        Matcher m = TOKEN.matcher(token);
        return m.matches() ? decode(m) : Optional.empty();
    }

    /** Never derives a host from token content. */
    public static Optional<ResolvedMedia> decode(Matcher m) {
        String body = m.group(1) + m.group(2) + m.group(3);
        if (!"1".equals(m.group(1)) || check(body) != m.group(4).charAt(0)) return Optional.empty();
        return SiteRegistry.byCode(m.group(2).charAt(0))
            .filter(c -> c.payloadPattern().matcher(m.group(3)).matches())
            .map(c -> new ResolvedMedia(c, m.group(3)));
    }
}
