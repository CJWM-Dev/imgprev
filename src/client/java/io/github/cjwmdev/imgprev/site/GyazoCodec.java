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

/** Payload: ext code {@code p|j|g} + Base62(32-hex id); page links without an extension assume png. */
final class GyazoCodec extends BaseCodec {
    static final Pattern PATH = Pattern.compile("^/([0-9a-fA-F]{32})(?:\\.(?i:(png|jpe?g|gif)))?$");
    static final Pattern P = Pattern.compile("[pjg][0-9A-Za-z]{1,22}");
    static final String EXT = "pjg";

    GyazoCodec() { super('z', "gyazo", "Gyazo", Label.IMAGE, Set.of("gyazo.com", "i.gyazo.com"), Set.of("i.gyazo.com")); }

    @Override public Pattern payloadPattern() { return P; }

    @Override public Optional<String> payloadFrom(URI u) {
        Matcher m = PATH.matcher(u.getPath());
        if (!m.matches()) return Optional.empty();
        String ext = m.group(2) == null ? "png" : m.group(2).toLowerCase(Locale.ROOT);
        char e = ext.startsWith("jp") ? 'j' : ext.equals("gif") ? 'g' : 'p';
        return Optional.of(e + Base62.encode(new BigInteger(m.group(1), 16)));
    }

    /** The 128-bit id and ext code in one number. */
    @Override public String compact(String p) {
        return new Mixed().putBits(Base62.decode(p.substring(1)), 128).put(EXT.indexOf(p.charAt(0)), EXT.length()).toBase62();
    }

    @Override public Optional<String> expand(String s) {
        return Mixed.fromBase62(s).flatMap(m -> {
            char ext = EXT.charAt(m.take(EXT.length()));
            String id = Base62.encode(m.takeBits(128));
            return m.isEmpty() ? Optional.of(ext + id) : Optional.empty();
        });
    }

    @Override public URI mediaUri(String p) {
        String hex = Base62.decode(p.substring(1)).toString(16);
        if (hex.length() > 32) throw new IllegalArgumentException("id");
        String ext = switch (p.charAt(0)) { case 'j' -> "jpg"; case 'g' -> "gif"; default -> "png"; };
        return URI.create("https://i.gyazo.com/" + Base62.leftPad(hex, 32) + "." + ext);
    }
}
