package dev.example.imgprev.site;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GiphyCodec implements SiteCodec {
    static final Pattern ID = Pattern.compile("[A-Za-z0-9]{5,40}");
    static final Pattern SHORT = Pattern.compile("([A-Za-z0-9]{5,40})\\.(?i:gif|webp)");
    private static final Set<String> HOSTS = Set.of("giphy.com", "i.giphy.com", "media.giphy.com",
        "media0.giphy.com", "media1.giphy.com", "media2.giphy.com", "media3.giphy.com", "media4.giphy.com");
    private static final Set<String> DOWNLOAD = Set.of("i.giphy.com");

    public char code() { return 'g'; }
    public String name() { return "giphy"; }
    public String label() { return "GIF"; }
    public Set<String> acceptedHosts() { return HOSTS; }
    public Pattern payloadPattern() { return ID; }

    public Optional<String> payloadFrom(URI u) {
        String[] seg = u.getPath().split("/");
        // i.giphy.com/{id}.gif
        if (u.getHost().equalsIgnoreCase("i.giphy.com") && seg.length == 2) {
            Matcher m = SHORT.matcher(seg[1]);
            return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
        }
        // giphy.com/gifs/{slug-}{id}
        if (u.getHost().equalsIgnoreCase("giphy.com") && seg.length >= 3 && seg[1].equals("gifs")) {
            String id = seg[2].substring(seg[2].lastIndexOf('-') + 1);
            return ID.matcher(id).matches() ? Optional.of(id) : Optional.empty();
        }
        // media*.giphy.com/media/[v1.xxx/]{id}/giphy.gif
        if (seg.length >= 4 && seg[1].equals("media")) {
            String id = seg[seg.length - 2];
            return ID.matcher(id).matches() ? Optional.of(id) : Optional.empty();
        }
        return Optional.empty();
    }

    /** The original upload (usually 480 px wide); {@code 200.gif} is only 200 px tall. */
    public CompletableFuture<URI> mediaUri(String id) {
        return CompletableFuture.completedFuture(URI.create("https://i.giphy.com/media/" + id + "/giphy.gif"));
    }

    public Set<String> allowedDownloadHosts() { return DOWNLOAD; }
}
