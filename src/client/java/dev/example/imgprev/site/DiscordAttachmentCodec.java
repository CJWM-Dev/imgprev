package dev.example.imgprev.site;

import dev.example.imgprev.token.Base62;
import dev.example.imgprev.token.Mixed;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Signed attachment links: {@code ex}/{@code is} (8 hex) and {@code hm} (64 hex) are required, else Discord 404s.
 * Links expire (~24 h). Payload: {@code ch-att-ex-is-hm-name}, each field Base62.
 */
final class DiscordAttachmentCodec extends BaseCodec {
    static final Pattern PATH = Pattern.compile("^/attachments/(\\d{15,21})/(\\d{15,21})/([A-Za-z0-9._-]{1,100})$");
    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]{0,99}");
    static final Pattern SNOWFLAKE = Pattern.compile("\\d{15,21}");
    static final Pattern HEX = Pattern.compile("[0-9a-f]+");
    static final Pattern P = Pattern.compile("([0-9A-Za-z]+-){5}[0-9A-Za-z]+");
    private static final Set<String> HOSTS = Set.of("cdn.discordapp.com", "media.discordapp.net");
    /** Pasted images are named image.png (unknown.png before 2023). */
    static final List<String> DEFAULT_NAMES = List.of("image.png", "unknown.png");
    static final List<String> EXTS = List.of("png", "jpg", "gif", "PNG", "jpeg", "webp", "JPG", "mp4");
    static final String NAME_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz._-";
    /** Usual {@code ex - is}: 24 hours, and 14 days in early signed links. */
    static final List<Long> LIFETIMES = List.of(86_400L, 1_209_600L);
    /** {@code is} is stored as 31 bits from here (September 2020), which lasts until 2088. */
    static final long IS_EPOCH = 1_600_000_000L;
    static final String EXPIRED = "Discord link expired";

    DiscordAttachmentCodec() { super('d', "discord", "Discord attachments", Label.DISCORD, HOSTS, HOSTS); }

    @Override public Pattern payloadPattern() { return P; }

    @Override public Optional<String> payloadFrom(URI u) {
        Matcher m = PATH.matcher(u.getPath());
        if (!m.matches() || !safeName(m.group(3))) return Optional.empty();
        Map<String, String> q = query(u.getRawQuery());
        String ex = q.get("ex"), is = q.get("is"), hm = q.get("hm");
        if (!hex(ex, 8) || !hex(is, 8) || !hex(hm, 64)) return Optional.empty();
        return Optional.of(String.join("-", b62(m.group(1), 10), b62(m.group(2), 10),
            b62(ex, 16), b62(is, 16), b62(hm, 16), nameEnc(m.group(3))));
    }

    /** Always rebuilt on cdn.discordapp.com, whichever host the sender copied from. */
    @Override public URI mediaUri(String p) {
        String[] f = p.split("-");
        String ch = dec(f[0], 10, 0), att = dec(f[1], 10, 0), ex = dec(f[2], 16, 8), is = dec(f[3], 16, 8), hm = dec(f[4], 16, 64);
        if (!SNOWFLAKE.matcher(ch).matches() || !SNOWFLAKE.matcher(att).matches() || !hex(ex, 8) || !hex(is, 8) || !hex(hm, 64))
            throw new IllegalArgumentException("payload");
        String name = nameDec(f[5]).filter(DiscordAttachmentCodec::safeName).orElseThrow(() -> new IllegalArgumentException("name"));
        return URI.create("https://cdn.discordapp.com/attachments/" + ch + "/" + att + "/" + name + "?ex=" + ex + "&is=" + is + "&hm=" + hm + "&");
    }

    @Override public OptionalLong expiresAtEpochSec(String p) { return OptionalLong.of(Base62.decode(p.split("-")[2]).longValue()); }
    @Override public String expiredMessage() { return EXPIRED; }
    /** Discord answers an expired signed link with 403 or 404. */
    @Override public Optional<String> statusMessage(int status) { return status == 403 || status == 404 ? Optional.of(EXPIRED) : Optional.empty(); }

    /**
     * One number: the two snowflakes, {@code is} since 2020, {@code ex} as a lifetime when it's a usual one, {@code hm},
     * then the filename (Discord's default names as one symbol, else the name with a common extension split off).
     */
    @Override public String compact(String p) {
        String[] f = p.split("-");
        String name = nameDec(f[5]).orElseThrow(() -> new IllegalArgumentException("name"));
        long ex = Base62.decode(f[2]).longValue(), is = Base62.decode(f[3]).longValue();
        Mixed m = new Mixed();
        int known = DEFAULT_NAMES.indexOf(name);
        if (known < 0) {
            int dot = name.lastIndexOf('.'), ext = dot > 0 ? EXTS.indexOf(name.substring(dot + 1)) : -1;
            m.putString(ext < 0 ? name : name.substring(0, dot), NAME_CHARS, 1, 100).put(ext < 0 ? EXTS.size() : ext, EXTS.size() + 1);
        }
        m.put(known < 0 ? DEFAULT_NAMES.size() : known, DEFAULT_NAMES.size() + 1).putBits(Base62.decode(f[4]), 256);
        int life = LIFETIMES.indexOf(ex - is);
        if (life < 0) m.putBits(BigInteger.valueOf(ex), 32);
        return m.put(life < 0 ? LIFETIMES.size() : life, LIFETIMES.size() + 1).putBits(BigInteger.valueOf(is - IS_EPOCH), 31)
            .putBits(Base62.decode(f[1]), 63).putBits(Base62.decode(f[0]), 63).toBase62();
    }

    @Override public Optional<String> expand(String s) {
        return Mixed.fromBase62(s).flatMap(m -> {
            BigInteger ch = m.takeBits(63), att = m.takeBits(63), is = m.takeBits(31).add(BigInteger.valueOf(IS_EPOCH));
            int life = m.take(LIFETIMES.size() + 1);
            BigInteger ex = life < LIFETIMES.size() ? is.add(BigInteger.valueOf(LIFETIMES.get(life))) : m.takeBits(32);
            BigInteger hm = m.takeBits(256);
            int known = m.take(DEFAULT_NAMES.size() + 1);
            String name;
            if (known < DEFAULT_NAMES.size()) name = DEFAULT_NAMES.get(known);
            else {
                int ext = m.take(EXTS.size() + 1);
                String base = m.takeString(NAME_CHARS, 1, 100);
                name = ext < EXTS.size() ? base + "." + EXTS.get(ext) : base;
            }
            if (!m.isEmpty() || !safeName(name)) return Optional.empty();
            return Optional.of(String.join("-", Base62.encode(ch), Base62.encode(att), Base62.encode(ex), Base62.encode(is),
                Base62.encode(hm), nameEnc(name)));
        });
    }

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

    static boolean hex(String s, int len) { return s != null && s.length() == len && HEX.matcher(s).matches(); }

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
