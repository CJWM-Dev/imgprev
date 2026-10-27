package dev.example.imgprev.site;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.example.imgprev.image.MediaException;
import dev.example.imgprev.image.SafeHttp;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * klipy.com/gifs/{slug}. Payload = slug; the media URL comes from Klipy's Items API with the player's own key
 * (docs.klipy.com/gifs-api/gifs-items-api) and must be on Klipy's CDN (docs.klipy.com/network-requirements).
 */
final class KlipyCodec implements SiteCodec {
    static final Pattern SLUG = Pattern.compile("[A-Za-z0-9-]{3,100}");
    static final Pattern PATH = Pattern.compile("^/gifs/([A-Za-z0-9-]{3,100})/?$");
    /** The key goes into the request path, so only URL-safe characters. */
    static final Pattern API_KEY = Pattern.compile("[A-Za-z0-9_-]{8,128}");
    static final String[] SIZES = {"sm", "md", "xs", "hd"};   // sm ≈ 220 px, closest to the preview size
    static final int MAX_JSON_BYTES = 1 << 20;
    private static final Set<String> HOSTS = Set.of("klipy.com", "www.klipy.com");
    private static final Set<String> API = Set.of("api.klipy.com");
    private static final Set<String> CDN = Set.of("static.klipy.com", "static1.klipy.com", "static2.klipy.com");

    static volatile Supplier<String> apiKey = () -> "";
    /** slug → media URL; test keys allow 100 API calls an hour. */
    private final Map<String, URI> resolved = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, URI> eldest) { return size() > 256; }
    });

    public char code() { return 'k'; }
    public String name() { return "klipy"; }
    public String label() { return "GIF"; }
    public Set<String> acceptedHosts() { return HOSTS; }
    public Pattern payloadPattern() { return SLUG; }
    public Set<String> allowedDownloadHosts() { return CDN; }

    public Optional<String> payloadFrom(URI u) {
        Matcher m = PATH.matcher(u.getPath());
        return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
    }

    public CompletableFuture<URI> mediaUri(String slug) {
        URI cached = resolved.get(slug);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        String key = apiKey.get().strip();
        if (key.isEmpty()) return CompletableFuture.failedFuture(new MediaException("Klipy needs an API key (config)"));
        if (!API_KEY.matcher(key).matches()) return CompletableFuture.failedFuture(new MediaException("Klipy API key looks wrong"));
        URI api = URI.create("https://api.klipy.com/api/v1/" + key + "/gifs/items?slugs=" + slug);
        return SafeHttp.get(api, API, MAX_JSON_BYTES, "application/json").thenApply(body -> {
            try {
                URI media = pick(new String(body, StandardCharsets.UTF_8), slug);
                resolved.put(slug, media);
                return media;
            } catch (MediaException e) {
                throw new CompletionException(e);
            }
        });
    }

    /** The item's GIF, smallest-first from {@link #SIZES}; only HTTPS URLs on Klipy's CDN are accepted. */
    static URI pick(String json, String slug) throws MediaException {
        JsonArray items;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            items = root.getAsJsonObject("data").getAsJsonArray("data");
        } catch (RuntimeException e) {
            throw new MediaException("Unexpected Klipy response");
        }
        for (JsonElement el : items) {
            if (!el.isJsonObject() || !slug.equals(str(el.getAsJsonObject(), "slug"))) continue;
            JsonObject file = el.getAsJsonObject().getAsJsonObject("file");
            for (String size : SIZES) {
                if (file == null || !(file.get(size) instanceof JsonObject s) || !(s.get("gif") instanceof JsonObject gif)) continue;
                String url = str(gif, "url");
                if (url == null) continue;
                try {
                    URI u = URI.create(url);
                    if ("https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null && CDN.contains(u.getHost().toLowerCase(Locale.ROOT))) return u;
                } catch (IllegalArgumentException ignored) { /* try the next size */ }
            }
        }
        throw new MediaException("Klipy GIF not found");
    }

    private static String str(JsonObject o, String field) {
        JsonElement e = o.get(field);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
}
