package dev.example.imgprev.site;

import dev.example.imgprev.token.Mixed;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code files.catbox.moe/{6 lowercase/digits}.{ext}}. Catbox is case-sensitive and 404s on any other extension,
 * so the payload keeps the exact one: ext code {@code p|j|e|g|w} (png, jpg, jpeg, gif, webp) + id.
 */
final class CatboxCodec extends BaseCodec {
    static final Pattern PATH = Pattern.compile("^/([a-z0-9]{6})\\.(png|jpg|jpeg|gif|webp)$");
    static final Pattern P = Pattern.compile("[pjegw][a-z0-9]{6}");
    static final String EXT = "pjegw", ID_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final Set<String> HOSTS = Set.of("files.catbox.moe");

    CatboxCodec() { super('c', "catbox", "Catbox", Label.IMAGE, HOSTS, HOSTS); }

    @Override public Pattern payloadPattern() { return P; }

    @Override public Optional<String> payloadFrom(URI u) {
        Matcher m = PATH.matcher(u.getPath());
        if (!m.matches()) return Optional.empty();
        char e = switch (m.group(2)) { case "png" -> 'p'; case "jpg" -> 'j'; case "jpeg" -> 'e'; case "gif" -> 'g'; default -> 'w'; };
        return Optional.of(e + m.group(1));
    }

    /** The six base-36 id characters and ext code in one number. */
    @Override public String compact(String p) {
        return new Mixed().putString(p.substring(1), ID_CHARS, 6, 6).put(EXT.indexOf(p.charAt(0)), EXT.length()).toBase62();
    }

    @Override public Optional<String> expand(String s) {
        return Mixed.fromBase62(s).flatMap(m -> {
            char ext = EXT.charAt(m.take(EXT.length()));
            String id = m.takeString(ID_CHARS, 6, 6);
            return m.isEmpty() ? Optional.of(ext + id) : Optional.empty();
        });
    }

    @Override public URI mediaUri(String p) {
        String ext = switch (p.charAt(0)) { case 'p' -> "png"; case 'j' -> "jpg"; case 'e' -> "jpeg"; case 'g' -> "gif"; default -> "webp"; };
        return URI.create("https://files.catbox.moe/" + p.substring(1) + "." + ext);
    }
}
