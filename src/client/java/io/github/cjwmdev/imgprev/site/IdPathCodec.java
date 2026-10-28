package io.github.cjwmdev.imgprev.site;

import io.github.cjwmdev.imgprev.image.MediaException;
import io.github.cjwmdev.imgprev.image.SafeHttp;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hosts whose CDN addresses are {@code {cdn}/{id}/{filename}} and serve the file by id alone, ignoring the filename
 * and its extension (Postimages, ImgBB). Payload: the id. Their share pages, {@code {page}/{id}}, use a different
 * id, so {@link LinkConverter} looks up the page's {@code og:image} on the sender's side; viewers need nothing.
 */
final class IdPathCodec extends BaseCodec {
    static final Pattern ID = Pattern.compile("[A-Za-z0-9]{6,12}");
    static final Pattern PATH = Pattern.compile("^/([A-Za-z0-9]{6,12})/[^/]+$");
    static final Pattern PAGE_PATH = Pattern.compile("^/([A-Za-z0-9]{6,12})/?$");
    /** Both sites write {@code property} before {@code content}. */
    static final Pattern OG_IMAGE = Pattern.compile("<meta\\s+property=\"og:image\"\\s+content=\"([^\"]+)\"");
    static final int MAX_PAGE_BYTES = 1 << 20;

    /** Fetches a share page's HTML; tests stand in for the network. */
    @FunctionalInterface
    interface PageFetcher { CompletableFuture<byte[]> fetch(URI page, Set<String> allowedHosts); }

    static final PageFetcher NETWORK = (page, hosts) -> SafeHttp.get(page, hosts, MAX_PAGE_BYTES, "text/html");

    private final String cdn, page;

    IdPathCodec(char code, String name, String title, String cdn, String page) {
        super(code, name, title, Label.IMAGE, Set.of(cdn), Set.of(cdn));
        this.cdn = cdn;
        this.page = page;
    }

    @Override public Pattern payloadPattern() { return ID; }

    @Override public Optional<String> payloadFrom(URI u) {
        Matcher m = PATH.matcher(u.getPath());
        return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** Any filename works; the content type comes from the file itself. */
    @Override public URI mediaUri(String id) { return URI.create("https://" + cdn + "/" + id + "/image.png"); }

    /** The id of a share page link; {@code www.} and a trailing slash only redirect. */
    Optional<String> pageId(URI u) {
        String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
        if (!host.equals(page) && !host.equals("www." + page)) return Optional.empty();
        Matcher m = PAGE_PATH.matcher(u.getPath());
        return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** The page's full-size CDN address. Failures are {@link MediaException}s that can be shown as-is. */
    CompletableFuture<URI> lookUp(String pageId, PageFetcher fetcher) {
        URI pageUri = URI.create("https://" + page + "/" + pageId);
        return fetcher.fetch(pageUri, Set.of(page)).handle((body, err) -> {
            if (err != null) {
                throw new CompletionException(SafeHttp.unwrap(err) instanceof SafeHttp.HttpStatusException e && e.status == 404
                    ? new MediaException(title() + " page not found") : new MediaException("Couldn't look up the " + title() + " image"));
            }
            return ogImage(new String(body, StandardCharsets.UTF_8))
                .orElseThrow(() -> new CompletionException(new MediaException("No image found on that " + title() + " page")));
        });
    }

    /** The page's {@code og:image}, only when it is an HTTPS address on this site's CDN. */
    Optional<URI> ogImage(String html) {
        Matcher m = OG_IMAGE.matcher(html);
        if (!m.find()) return Optional.empty();
        try {
            URI u = URI.create(m.group(1));
            boolean ok = "https".equalsIgnoreCase(u.getScheme()) && cdn.equalsIgnoreCase(u.getHost()) && payloadFrom(u).isPresent();
            return ok ? Optional.of(u) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
