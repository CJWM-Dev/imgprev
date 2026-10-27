package dev.example.imgprev.site;

import dev.example.imgprev.image.MediaException;
import dev.example.imgprev.image.SafeHttp;
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
 * Sender side: pasted links → tokens. Most links convert on the spot; Klipy page links are looked up with the
 * sender's own API key and become keyless CDN tokens, so the people reading them need nothing.
 */
public final class LinkConverter {
    public static final String KLIPY_NEEDS_KEY =
        "Klipy page links need your Klipy API key (/imgprev klipy key <key>). Or copy the GIF's image address instead.";
    static final Pattern LINK = Pattern.compile("https://\\S+");
    static final String TRAILING = ".,!?;:)]}'\"";

    private LinkConverter() {}

    /** True for links that can only become a token after a network lookup. */
    public static boolean needsLookup(String url) { return klipySlug(url).isPresent(); }

    public static boolean canLookUp() { return KlipyCodec.hasApiKey(); }

    /** Completes with the token, or fails with a {@link MediaException} whose message can be shown as-is. */
    public static CompletableFuture<String> convert(String url) {
        Optional<String> direct = TokenCodec.encode(url);
        if (direct.isPresent()) return CompletableFuture.completedFuture(direct.get());
        Optional<String> slug = klipySlug(url);
        if (slug.isEmpty()) return CompletableFuture.failedFuture(new MediaException("Not a supported image link"));
        if (!canLookUp()) return CompletableFuture.failedFuture(new MediaException(KLIPY_NEEDS_KEY));
        return SiteRegistry.KLIPY_API.mediaUri(slug.get()).thenApply(media -> TokenCodec.encode(media.toString())
            .orElseThrow(() -> new CompletionException(new MediaException("Klipy returned an address ImgPrev can't use"))));
    }

    /** {@code text} with every link that converts without a lookup replaced by its token. */
    public static String convertNow(String text) { return replaceLinks(text, TokenCodec::encode); }

    /** Klipy page links in {@code text}, which {@link #convertNow} leaves alone. */
    public static List<String> lookupsIn(String text) {
        List<String> out = new ArrayList<>();
        replaceLinks(text, url -> { if (needsLookup(url) && !out.contains(url)) out.add(url); return Optional.empty(); });
        return out;
    }

    /** Converts every link in {@code text}, looking up Klipy page links first. */
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
        return switch (t) {
            case MediaException e -> e.getMessage();
            case SafeHttp.HttpStatusException e when e.status == 401 || e.status == 403 -> "Klipy API key rejected";
            case SafeHttp.HttpStatusException e when e.status == 429 -> "Klipy rate limit reached, try again later";
            default -> "Couldn't look up the Klipy GIF";
        };
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

    static Optional<String> klipySlug(String url) {
        try { return KlipyCodec.pageSlug(new URI(url)); } catch (URISyntaxException e) { return Optional.empty(); }
    }
}
