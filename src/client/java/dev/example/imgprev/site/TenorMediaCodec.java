package dev.example.imgprev.site;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Direct media links only (tenor.com's "copy image address" gives media1.tenor.com/m/…); tenor.com/view pages need
 * the API, which shut down June 2026.
 */
final class TenorMediaCodec implements SiteCodec {
    static final Pattern KEY = Pattern.compile("[A-Za-z0-9-]{8,32}");
    static final Pattern PATH = Pattern.compile("^(?:/m)?/([A-Za-z0-9_-]{8,32})/[^/]+\\.gif$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> HOSTS = Set.of("media.tenor.com", "media1.tenor.com", "c.tenor.com");
    private static final Set<String> DOWNLOAD = Set.of("media.tenor.com");
    /** Key endings that pick a rendition: the 220 px medium GIF and the full-size one. */
    static final String MEDIUM = "AAAAM", FULL = "AAAAC";

    public char code() { return 't'; }
    public String name() { return "tenor"; }
    public String label() { return "GIF"; }
    public Set<String> acceptedHosts() { return HOSTS; }
    public Pattern payloadPattern() { return KEY; }

    public Optional<String> payloadFrom(URI u) {
        Matcher m = PATH.matcher(u.getPath());
        // Keys with '_' exist but can't be carried in the payload alphabet.
        return m.matches() && KEY.matcher(m.group(1)).matches() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** Tenor ignores the filename; the key's suffix selects the rendition, and medium GIFs are swapped for full-size ones. */
    public CompletableFuture<URI> mediaUri(String key) {
        String full = key.endsWith(MEDIUM) ? key.substring(0, key.length() - MEDIUM.length()) + FULL : key;
        return CompletableFuture.completedFuture(URI.create("https://media.tenor.com/" + full + "/tenor.gif"));
    }

    public Set<String> allowedDownloadHosts() { return DOWNLOAD; }
}
