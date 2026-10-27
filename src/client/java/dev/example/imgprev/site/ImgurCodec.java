package dev.example.imgprev.site;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ImgurCodec implements SiteCodec {
    static final Pattern P = Pattern.compile("[pjg][A-Za-z0-9]{5,7}");
    // Case-insensitive only matters for the extension; the hash class already covers both cases.
    static final Pattern PATH = Pattern.compile("^/([A-Za-z0-9]{5,7})(?:\\.(png|jpe?g|gifv?|webp|mp4))?$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> HOSTS = Set.of("imgur.com", "i.imgur.com");
    private static final Set<String> DOWNLOAD = Set.of("i.imgur.com");

    public char code() { return 'i'; }
    public String name() { return "imgur"; }
    public String label() { return "Image"; }
    public Set<String> acceptedHosts() { return HOSTS; }
    public Pattern payloadPattern() { return P; }

    public Optional<String> payloadFrom(URI u) {
        String path = u.getPath();
        if (path.startsWith("/a/") || path.startsWith("/gallery/")) return Optional.empty();
        Matcher m = PATH.matcher(path);
        if (!m.matches()) return Optional.empty();
        String ext = m.group(2) == null ? "png" : m.group(2).toLowerCase(Locale.ROOT);
        char e = ext.startsWith("gif") || ext.equals("mp4") ? 'g' : ext.startsWith("jp") ? 'j' : 'p';
        return Optional.of(e + m.group(1));
    }

    public CompletableFuture<URI> mediaUri(String p) {
        String ext = switch (p.charAt(0)) { case 'g' -> "gif"; case 'j' -> "jpg"; default -> "png"; };
        return CompletableFuture.completedFuture(URI.create("https://i.imgur.com/" + p.substring(1) + "." + ext));
    }

    public Set<String> allowedDownloadHosts() { return DOWNLOAD; }
}
