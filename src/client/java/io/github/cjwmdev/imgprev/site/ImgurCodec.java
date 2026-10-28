package io.github.cjwmdev.imgprev.site;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ImgurCodec extends BaseCodec {
    static final Pattern P = Pattern.compile("[pjg][A-Za-z0-9]{5,7}");
    static final Pattern PATH = Pattern.compile("^/([A-Za-z0-9]{5,7})(?:\\.(?i:(png|jpe?g|gifv?|webp|mp4)))?$");

    ImgurCodec() { super('i', "imgur", "Imgur", Label.IMAGE, Set.of("imgur.com", "i.imgur.com"), Set.of("i.imgur.com")); }

    @Override public Pattern payloadPattern() { return P; }

    @Override public Optional<String> payloadFrom(URI u) {
        String path = u.getPath();
        if (path.startsWith("/a/") || path.startsWith("/gallery/")) return Optional.empty();
        Matcher m = PATH.matcher(path);
        if (!m.matches()) return Optional.empty();
        String ext = m.group(2) == null ? "png" : m.group(2).toLowerCase(Locale.ROOT);
        char e = ext.startsWith("gif") || ext.equals("mp4") ? 'g' : ext.startsWith("jp") ? 'j' : 'p';
        return Optional.of(e + m.group(1));
    }

    @Override public URI mediaUri(String p) {
        String ext = switch (p.charAt(0)) { case 'g' -> "gif"; case 'j' -> "jpg"; default -> "png"; };
        return URI.create("https://i.imgur.com/" + p.substring(1) + "." + ext);
    }
}
