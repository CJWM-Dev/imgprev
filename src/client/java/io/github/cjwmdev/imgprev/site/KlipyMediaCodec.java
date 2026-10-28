package io.github.cjwmdev.imgprev.site;

import io.github.cjwmdev.imgprev.token.Base62;
import io.github.cjwmdev.imgprev.token.Mixed;
import java.math.BigInteger;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Klipy CDN addresses, {@code static.klipy.com/ii/{32 hex}/{2 hex}/{2 hex}/{name}.{ext}}: viewers need no API key.
 * Payload: ext code {@code g|w|p|j} + Base62(hash) - Base62(the two 2-hex dirs) - name. static2 mirrors static
 * byte for byte, so everything is rebuilt on static.klipy.com.
 * <p>
 * klipy.com/gifs page links can't become tokens: the pages sit behind a Cloudflare bot check and the only other
 * route is Klipy's API, which needs a key (docs/VERIFIED.md).
 */
final class KlipyMediaCodec extends BaseCodec {
    static final Pattern PATH = Pattern.compile("^/ii/([0-9a-f]{32})/([0-9a-f]{2})/([0-9a-f]{2})/([A-Za-z0-9]{4,32})\\.(?i:(gif|webp|png|jpe?g))$");
    static final Pattern P = Pattern.compile("[gwpj][0-9A-Za-z]{1,22}-[0-9A-Za-z]{1,3}-[A-Za-z0-9]{4,32}");
    static final String EXT = "gwpj";
    static final Pattern PAGE_PATH = Pattern.compile("^/gifs/[A-Za-z0-9-]{3,100}/?$");
    private static final Set<String> PAGE_HOSTS = Set.of("klipy.com", "www.klipy.com");

    KlipyMediaCodec() {
        super('y', "klipy", "Klipy", Label.GIF, Set.of("static.klipy.com", "static1.klipy.com", "static2.klipy.com"),
            Set.of("static.klipy.com", "static2.klipy.com"));
    }

    static boolean isPageLink(URI u) {
        return u.getHost() != null && PAGE_HOSTS.contains(u.getHost().toLowerCase(Locale.ROOT)) && PAGE_PATH.matcher(u.getPath()).matches();
    }

    @Override public Pattern payloadPattern() { return P; }

    @Override public Optional<String> payloadFrom(URI u) {
        Matcher m = PATH.matcher(u.getPath());
        if (!m.matches()) return Optional.empty();
        String ext = m.group(5).toLowerCase(Locale.ROOT);
        char e = switch (ext) { case "gif" -> 'g'; case "webp" -> 'w'; case "png" -> 'p'; default -> 'j'; };
        return Optional.of(e + Base62.encode(new BigInteger(m.group(1), 16)) + "-"
            + Base62.encode(new BigInteger(m.group(2) + m.group(3), 16)) + "-" + m.group(4));
    }

    /** Name, the two dirs, the 128-bit hash and the ext code in one number. */
    @Override public String compact(String p) {
        String[] f = p.substring(1).split("-");
        return new Mixed().putString(f[2], Base62.A, 4, 32).putBits(Base62.decode(f[1]), 16).putBits(Base62.decode(f[0]), 128)
            .put(EXT.indexOf(p.charAt(0)), EXT.length()).toBase62();
    }

    @Override public Optional<String> expand(String s) {
        return Mixed.fromBase62(s).flatMap(m -> {
            char ext = EXT.charAt(m.take(EXT.length()));
            String hash = Base62.encode(m.takeBits(128)), dirs = Base62.encode(m.takeBits(16)), name = m.takeString(Base62.A, 4, 32);
            return m.isEmpty() ? Optional.of(ext + hash + "-" + dirs + "-" + name) : Optional.empty();
        });
    }

    @Override public URI mediaUri(String p) {
        String[] f = p.substring(1).split("-");
        String hash = Base62.decode(f[0]).toString(16), dirs = Base62.decode(f[1]).toString(16);
        if (hash.length() > 32 || dirs.length() > 4) throw new IllegalArgumentException("payload");
        hash = Base62.leftPad(hash, 32);
        dirs = Base62.leftPad(dirs, 4);
        String ext = switch (p.charAt(0)) { case 'g' -> "gif"; case 'w' -> "webp"; case 'p' -> "png"; default -> "jpg"; };
        return URI.create("https://static.klipy.com/ii/" + hash + "/" + dirs.substring(0, 2) + "/" + dirs.substring(2) + "/" + f[2] + "." + ext);
    }
}
