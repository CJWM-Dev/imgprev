package dev.example.imgprev.site;

import dev.example.imgprev.token.Mixed;
import java.math.BigInteger;
import java.net.URI;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Direct media links only (tenor.com's "copy image address" gives media1.tenor.com/m/…); tenor.com/view pages need
 * the API, which shut down June 2026.
 * <p>
 * A media key is base64url of 12 bytes: an 8-byte post id, then a 4-byte rendition code (2 = full-size GIF, 12 =
 * medium GIF, …). Senders' keys are rewritten to the full-size GIF, so a token carries just the post id.
 */
final class TenorMediaCodec implements SiteCodec {
    static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{8,32}");
    static final Pattern PATH = Pattern.compile("^(?:/m)?/([A-Za-z0-9_-]{8,32})/[^/]+\\.gif$", Pattern.CASE_INSENSITIVE);
    static final String KEY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final Set<String> HOSTS = Set.of("media.tenor.com", "media1.tenor.com", "c.tenor.com");
    private static final Set<String> DOWNLOAD = Set.of("media.tenor.com");
    /** Key endings that pick a rendition: the 220 px medium GIF and the full-size one. */
    static final String MEDIUM = "AAAAM", FULL = "AAAAC";

    public char code() { return 't'; }
    public String name() { return "tenor"; }
    public String title() { return "Tenor"; }
    public String label() { return "GIF"; }
    public Set<String> acceptedHosts() { return HOSTS; }
    public Pattern payloadPattern() { return KEY; }

    public Optional<String> payloadFrom(URI u) {
        Matcher m = PATH.matcher(u.getPath());
        return m.matches() ? Optional.of(postId(m.group(1)).map(TenorMediaCodec::fullGif).orElse(m.group(1))) : Optional.empty();
    }

    /** The post id of a standard 16-character key. */
    static Optional<BigInteger> postId(String key) {
        if (key.length() != 16) return Optional.empty();
        byte[] b = Base64.getUrlDecoder().decode(key);
        return b[8] == 0 && b[9] == 0 && b[10] == 0 ? Optional.of(new BigInteger(1, Arrays.copyOf(b, 8))) : Optional.empty();
    }

    static String fullGif(BigInteger id) {
        byte[] b = new byte[12], v = id.toByteArray();
        int n = Math.min(v.length, 8);
        System.arraycopy(v, v.length - n, b, 8 - n, n);
        b[11] = 2;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Flag 0: a full-size GIF key as its post id. Flag 1: any other key, character by character. */
    @Override public String compact(String key) {
        Optional<BigInteger> id = postId(key).filter(i -> fullGif(i).equals(key));
        Mixed m = id.isPresent() ? new Mixed().putBits(id.get(), 64).put(0, 2) : new Mixed().putString(key, KEY_CHARS, 8, 32).put(1, 2);
        return m.toBase62();
    }

    @Override public Optional<String> expand(String s) {
        return Mixed.fromBase62(s).flatMap(m -> {
            String key = m.take(2) == 0 ? fullGif(m.takeBits(64)) : m.takeString(KEY_CHARS, 8, 32);
            return m.isEmpty() ? Optional.of(key) : Optional.empty();
        });
    }

    /** Tenor ignores the filename; the key's suffix selects the rendition, and medium GIFs are swapped for full-size ones. */
    public CompletableFuture<URI> mediaUri(String key) {
        String full = key.endsWith(MEDIUM) ? key.substring(0, key.length() - MEDIUM.length()) + FULL : key;
        return CompletableFuture.completedFuture(URI.create("https://media.tenor.com/" + full + "/tenor.gif"));
    }

    public Set<String> allowedDownloadHosts() { return DOWNLOAD; }
}
