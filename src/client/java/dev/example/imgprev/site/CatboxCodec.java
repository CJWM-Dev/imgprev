package dev.example.imgprev.site;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code files.catbox.moe/{6 lowercase/digits}.{ext}}. Catbox is case-sensitive and 404s on any other extension,
 * so the payload keeps the exact one: ext code {@code p|j|e|g|w} (png, jpg, jpeg, gif, webp) + id.
 */
final class CatboxCodec implements SiteCodec {
    static final Pattern PATH = Pattern.compile("^/([a-z0-9]{6})\\.(png|jpg|jpeg|gif|webp)$");
    static final Pattern P = Pattern.compile("[pjegw][a-z0-9]{6}");
    private static final Set<String> HOSTS = Set.of("files.catbox.moe");

    public char code() { return 'c'; }
    public String name() { return "catbox"; }
    public String label() { return "Image"; }
    public Set<String> acceptedHosts() { return HOSTS; }
    public Pattern payloadPattern() { return P; }
    public Set<String> allowedDownloadHosts() { return HOSTS; }

    public Optional<String> payloadFrom(URI u) {
        Matcher m = PATH.matcher(u.getPath());
        if (!m.matches()) return Optional.empty();
        char e = switch (m.group(2)) { case "png" -> 'p'; case "jpg" -> 'j'; case "jpeg" -> 'e'; case "gif" -> 'g'; default -> 'w'; };
        return Optional.of(e + m.group(1));
    }

    public CompletableFuture<URI> mediaUri(String p) {
        String ext = switch (p.charAt(0)) { case 'p' -> "png"; case 'j' -> "jpg"; case 'e' -> "jpeg"; case 'g' -> "gif"; default -> "webp"; };
        return CompletableFuture.completedFuture(URI.create("https://files.catbox.moe/" + p.substring(1) + "." + ext));
    }
}
