package dev.example.imgprev.site;

import dev.example.imgprev.image.MediaException;
import dev.example.imgprev.token.TokenCodec;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sender side: pasted links → tokens. Most links convert on the spot; Postimages and ImgBB page links are looked up
 * first and become CDN tokens, so the people reading them need nothing. Klipy page links can't be looked up at all
 * (see {@link KlipyMediaCodec}), so they're refused with a hint instead of being sent as plain links.
 */
public final class LinkConverter {
    public static final String KLIPY_PAGE_LINK =
        "Klipy page links can't be converted. Right-click the GIF on the page and copy its image address instead.";
    static final Pattern LINK = Pattern.compile("https://\\S+");
    static final String TRAILING = ".,!?;:)]}'\"";

    record Page(IdPathCodec site, String id) {}

    private LinkConverter() {}

    /** True for links that can only become a token after a network lookup. */
    public static boolean needsLookup(String url) { return isKlipyPage(url) || page(url).isPresent(); }

    /** False for links that need a lookup ImgPrev can't do: Klipy page links. */
    public static boolean canLookUp(String url) { return !isKlipyPage(url); }

    /** Completes with the token, or fails with a {@link MediaException} whose message can be shown as-is. */
    public static CompletableFuture<String> convert(String url) {
        Optional<String> direct = TokenCodec.encode(url);
        if (direct.isPresent()) return CompletableFuture.completedFuture(direct.get());
        if (isKlipyPage(url)) return CompletableFuture.failedFuture(new MediaException(KLIPY_PAGE_LINK));
        Optional<Page> page = page(url);
        if (page.isEmpty()) return CompletableFuture.failedFuture(new MediaException("Not a supported image link"));
        IdPathCodec site = page.get().site();
        return site.lookUp(page.get().id()).thenApply(m -> TokenCodec.encode(m.toString())
            .orElseThrow(() -> new CompletionException(new MediaException(site.title() + " returned an address ImgPrev can't use"))));
    }

    /** {@code text} with every link that converts without a lookup replaced by its token. */
    public static String convertNow(String text) { return replaceLinks(text, TokenCodec::encode); }

    /** Page links in {@code text}, which {@link #convertNow} leaves alone. */
    public static List<String> lookupsIn(String text) {
        List<String> out = new ArrayList<>();
        replaceLinks(text, url -> { if (needsLookup(url) && !out.contains(url)) out.add(url); return Optional.empty(); });
        return out;
    }

    /** Converts every link in {@code text}, looking up page links first. */
    public static CompletableFuture<String> convertAll(String text) {
        Map<String, CompletableFuture<String>> pending = new LinkedHashMap<>();
        for (String url : lookupsIn(text)) pending.put(url, convert(url));
        return CompletableFuture.allOf(pending.values().toArray(CompletableFuture[]::new)).thenApply(done ->
            replaceLinks(text, url -> pending.containsKey(url) ? Optional.of(pending.get(url).join()) : TokenCodec.encode(url)));
    }

    /** A message for a failed {@link #convert}. */
    public static String describe(Throwable err) {
        Throwable t = err;
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) t = t.getCause();
        return t instanceof MediaException e ? e.getMessage() : "Couldn't look up the image";
    }

    /**
     * Replaces each https link that {@code tokens} has a token for; links without one stay as typed. Punctuation
     * right after a link ("…png).") is tried as sentence punctuation first, since some sites' paths would accept it.
     */
    static String replaceLinks(String text, Function<String, Optional<String>> tokens) {
        Matcher m = LINK.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String url = m.group();
            int end = url.length();
            while (end > 0 && TRAILING.indexOf(url.charAt(end - 1)) >= 0) end--;
            Optional<String> token = end < url.length() ? tokens.apply(url.substring(0, end)) : Optional.empty();
            if (token.isPresent()) url = url.substring(0, end);
            else token = tokens.apply(url);
            if (token.isEmpty()) continue;
            out.append(text, last, m.start()).append(token.get());
            last = m.start() + url.length();
        }
        return out.append(text, last, text.length()).toString();
    }

    static boolean isKlipyPage(String url) {
        try { return KlipyMediaCodec.isPageLink(new URI(url)); } catch (URISyntaxException e) { return false; }
    }

    static Optional<Page> page(String url) {
        URI u;
        try { u = new URI(url); } catch (URISyntaxException e) { return Optional.empty(); }
        if (!"https".equalsIgnoreCase(u.getScheme())) return Optional.empty();
        for (IdPathCodec site : SiteRegistry.PAGES) {
            Optional<String> id = site.pageId(u);
            if (id.isPresent()) return Optional.of(new Page(site, id.get()));
        }
        return Optional.empty();
    }
}
