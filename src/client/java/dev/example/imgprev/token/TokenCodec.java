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

/**
 * Tokens are {@code "<~" header payload check "~>"}. Version 2 (sent): the header is the site code in upper case and the
 * payload is the site's {@link SiteCodec#compact} form, Base62 only. Version 1 (still read): the header is {@code "1"}
 * plus the lower-case site code, and the payload is the site's payload as-is. See docs/VERIFIED.md, "Token v2".
 */
public final class TokenCodec {
    public static final Pattern TOKEN = Pattern.compile("<~([0-9]?)([A-Za-z])([A-Za-z0-9-]{1,200})([0-9A-Za-z])~>");
    static final Pattern V2_PAYLOAD = Pattern.compile("[A-Za-z0-9]+");
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
            Optional<String> p = c.payloadFrom(uri).filter(s -> c.payloadPattern().matcher(s).matches()).flatMap(s -> compact(c, s));
            if (p.isPresent()) { String body = Character.toUpperCase(c.code()) + p.get(); return Optional.of("<~" + body + check(body) + "~>"); }
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
        String ver = m.group(1), site = m.group(2), payload = m.group(3);
        if (check(ver + site + payload) != m.group(4).charAt(0)) return Optional.empty();
        boolean v1 = ver.equals("1") && Character.isLowerCase(site.charAt(0));
        boolean v2 = ver.isEmpty() && Character.isUpperCase(site.charAt(0)) && V2_PAYLOAD.matcher(payload).matches();
        if (!v1 && !v2) return Optional.empty();
        return SiteRegistry.byCode(Character.toLowerCase(site.charAt(0))).flatMap(c -> (v1 ? Optional.of(payload) : expand(c, payload))
            .filter(p -> c.payloadPattern().matcher(p).matches())
            .map(p -> new ResolvedMedia(c, p)));
    }

    /** Empty when the compact form is too long or doesn't survive the trip back. */
    static Optional<String> compact(SiteCodec c, String payload) {
        try {
            String s = c.compact(payload);
            boolean ok = s.length() <= MAX_PAYLOAD && V2_PAYLOAD.matcher(s).matches() && expand(c, s).filter(payload::equals).isPresent();
            return ok ? Optional.of(s) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Hand-made payloads can be any Base62 text, so a codec that throws on one just doesn't decode it. */
    static Optional<String> expand(SiteCodec c, String compact) {
        try { return c.expand(compact); } catch (RuntimeException e) { return Optional.empty(); }
    }
}
