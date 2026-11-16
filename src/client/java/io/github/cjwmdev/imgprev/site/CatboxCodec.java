package io.github.cjwmdev.imgprev.site;

import io.github.cjwmdev.imgprev.token.Mixed;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code files.catbox.moe/{6 lowercase/digits}.{ext}}, and the same form on {@code litter.catbox.moe}, Catbox's
 * temporary host. Catbox is case-sensitive and 404s on any other extension, so the payload keeps the exact one:
 * ext code {@code p|j|e|g|w} (png, jpg, jpeg, gif, webp) + id.
 */
final class CatboxCodec extends BaseCodec {
    static final Pattern PATH = Pattern.compile("^/([a-z0-9]{6})\\.(png|jpg|jpeg|gif|webp)$");
    static final Pattern P = Pattern.compile("[pjegw][a-z0-9]{6}");
    static final String EXT = "pjegw", ID_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz";
    private final String host;
    private final boolean temporary;

    private CatboxCodec(char code, String name, String title, String host, boolean temporary) {
        super(code, name, title, Label.IMAGE, Set.of(host), Set.of(host));
        this.host = host;
        this.temporary = temporary;
    }

    static CatboxCodec catbox() { return new CatboxCodec('c', "catbox", "Catbox", "files.catbox.moe", false); }

    /** Files are deleted after the 1 to 72 hours chosen when they were uploaded. */
    static CatboxCodec litterbox() { return new CatboxCodec('l', "litterbox", "Litterbox", "litter.catbox.moe", true); }

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
        return URI.create("https://" + host + "/" + p.substring(1) + "." + ext);
    }

    /** Temporary files 404 once deleted, which is far likelier than a wrong link. */
    @Override public Optional<String> statusMessage(int status) {
        return temporary && status == 404 ? Optional.of("Link expired (temporary upload)") : Optional.empty();
    }
}
