package dev.example.imgprev.site;

import dev.example.imgprev.token.Base62;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Signed attachment links: {@code ex}/{@code is} (8 hex) and {@code hm} (64 hex) are required, else Discord 404s.
 * Links expire (~24 h). Payload: {@code ch-att-ex-is-hm-name}, each field Base62.
 */
final class DiscordAttachmentCodec implements SiteCodec {
    static final Pattern PATH = Pattern.compile("^/attachments/(\\d{15,21})/(\\d{15,21})/([A-Za-z0-9._-]{1,100})$");
    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]{0,99}");
    static final Pattern SNOWFLAKE = Pattern.compile("\\d{15,21}");
    static final Pattern P = Pattern.compile("([0-9A-Za-z]+-){5}[0-9A-Za-z]+");
    private static final Set<String> HOSTS = Set.of("cdn.discordapp.com", "media.discordapp.net");

    public char code() { return 'd'; }
    public String name() { return "discord"; }
    public String label() { return "Discord"; }
    public Set<String> acceptedHosts() { return HOSTS; }
    public Set<String> allowedDownloadHosts() { return HOSTS; }
    public Pattern payloadPattern() { return P; }

    public Optional<String> payloadFrom(URI u) {
        Matcher m = PATH.matcher(u.getPath());
        if (!m.matches() || !safeName(m.group(3))) return Optional.empty();
        Map<String, String> q = query(u.getRawQuery());
        String ex = q.get("ex"), is = q.get("is"), hm = q.get("hm");
        if (!hex(ex, 8) || !hex(is, 8) || !hex(hm, 64)) return Optional.empty();
        return Optional.of(String.join("-", b62(m.group(1), 10), b62(m.group(2), 10),
            b62(ex, 16), b62(is, 16), b62(hm, 16), nameEnc(m.group(3))));
    }

    /** Always rebuilt on cdn.discordapp.com, whichever host the sender copied from. */
    public CompletableFuture<URI> mediaUri(String p) {
        String[] f = p.split("-");
        String ch = dec(f[0], 10, 0), att = dec(f[1], 10, 0), ex = dec(f[2], 16, 8), is = dec(f[3], 16, 8), hm = dec(f[4], 16, 64);
        if (!SNOWFLAKE.matcher(ch).matches() || !SNOWFLAKE.matcher(att).matches() || !hex(ex, 8) || !hex(is, 8) || !hex(hm, 64))
            return CompletableFuture.failedFuture(new IllegalArgumentException("payload"));
        Optional<String> name = nameDec(f[5]).filter(DiscordAttachmentCodec::safeName);
        if (name.isEmpty()) return CompletableFuture.failedFuture(new SecurityException("name"));
        return CompletableFuture.completedFuture(URI.create("https://cdn.discordapp.com/attachments/"
            + ch + "/" + att + "/" + name.get() + "?ex=" + ex + "&is=" + is + "&hm=" + hm + "&"));
    }

    @Override public OptionalLong expiresAtEpochSec(String p) { return OptionalLong.of(Base62.decode(p.split("-")[2]).longValue()); }

    static String b62(String digits, int radix) { return Base62.encode(new BigInteger(digits, radix)); }
    static String dec(String b62, int radix, int pad) { String s = Base62.decode(b62).toString(radix); return pad > 0 ? Base62.leftPad(s, pad) : s; }
    static boolean safeName(String n) { return NAME.matcher(n).matches() && !n.contains(".."); }

    /** A leading 0x01 byte keeps leading NULs/digits from vanishing in the number. */
    static String nameEnc(String n) {
        byte[] b = n.getBytes(StandardCharsets.US_ASCII); byte[] w = new byte[b.length + 1]; w[0] = 1;
        System.arraycopy(b, 0, w, 1, b.length); return Base62.encode(new BigInteger(1, w));
    }
    static Optional<String> nameDec(String s) {
        byte[] w = Base62.decode(s).toByteArray();
        if (w.length < 2 || w[0] != 1) return Optional.empty();
        return Optional.of(new String(w, 1, w.length - 1, StandardCharsets.US_ASCII));
    }

    static boolean hex(String s, int len) { return s != null && s.length() == len && s.matches("[0-9a-f]+"); }

    static Map<String, String> query(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null) return out;
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            try { out.putIfAbsent(URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8), URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8)); }
            catch (IllegalArgumentException ignored) { /* malformed %-escape: skip the pair */ }
        }
        return out;
    }
}
