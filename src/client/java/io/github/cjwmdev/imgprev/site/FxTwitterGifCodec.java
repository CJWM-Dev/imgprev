package io.github.cjwmdev.imgprev.site;

import io.github.cjwmdev.imgprev.token.Mixed;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code gif.fxtwitter.com/tweet_video/{id}.webp}: FixTweet's animated WebP of a tweet's GIF (Twitter itself only
 * serves MP4). The id is Twitter's media key, 15 base64url characters; only the {@code .webp} form is known to work.
 */
final class FxTwitterGifCodec extends BaseCodec {
    static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{8,32}");
    static final Pattern PATH = Pattern.compile("^/tweet_video/(" + ID.pattern() + ")\\.webp$");
    static final String ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final Set<String> HOSTS = Set.of("gif.fxtwitter.com");

    FxTwitterGifCodec() { super('f', "fxtwitter", "FixTweet", Label.GIF, HOSTS, HOSTS); }

    @Override public Pattern payloadPattern() { return ID; }

    @Override public Optional<String> payloadFrom(URI u) {
        Matcher m = PATH.matcher(u.getPath());
        return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
    }

    @Override public String compact(String id) { return new Mixed().putString(id, ID_CHARS, 8, 32).toBase62(); }

    @Override public Optional<String> expand(String s) {
        return Mixed.fromBase62(s).flatMap(m -> {
            String id = m.takeString(ID_CHARS, 8, 32);
            return m.isEmpty() ? Optional.of(id) : Optional.empty();
        });
    }

    @Override public URI mediaUri(String id) { return URI.create("https://gif.fxtwitter.com/tweet_video/" + id + ".webp"); }
}
