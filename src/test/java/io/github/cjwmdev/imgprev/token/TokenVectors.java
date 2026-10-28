package io.github.cjwmdev.imgprev.token;

import io.github.cjwmdev.imgprev.site.SiteCodec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Writes every corpus link, plus links that must not convert, as tab-separated
 * {@code link, token, v1 token, site, download URL, expiry}, for ports of the token format (the Discord chat bridge's
 * JavaScript one) to check themselves against: {@code ./gradlew tokenVectors}. Links that don't convert have only the
 * first column.
 */
public final class TokenVectors {
    static final List<String> SITES = List.of("catbox", "discord", "emoji", "giphy", "gyazo", "imgbb", "imgur", "klipy", "postimages", "sticker", "tenor");
    /** Supported hosts with paths that aren't images, other hosts, and plain http. */
    static final List<String> REFUSED = List.of(
        "https://imgur.com/a/AbC12xY", "https://imgur.com/gallery/AbC12xY", "https://klipy.com/gifs/cat-dance",
        "https://tenor.com/view/cat-gif-12345", "https://cdn.discordapp.com/stickers/796140620111544330.json",
        "https://cdn.discordapp.com/emojis/123.png", "https://cdn.discordapp.com/attachments/1/2/x.png",
        "https://cdn.discordapp.com/attachments/951417646191083551/1099925527403446302/image.png",
        "https://example.com/cat.gif", "http://i.imgur.com/AbC12xY.png", "https://files.catbox.moe/sa4ue3.bmp",
        "https://postimg.cc/XqRyT9nn", "https://ibb.co/M5f2dMkG", "not a link");

    private TokenVectors() {}

    public static void main(String[] args) throws IOException {
        List<String> out = new ArrayList<>();
        for (String site : SITES) for (String url : links(site)) out.add(row(url));
        for (String url : REFUSED) out.add(row(url));
        Path file = Path.of(args[0]);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, String.join("\n", out) + "\n", StandardCharsets.UTF_8);   // "\n" on every OS, so the file diffs cleanly
        System.out.println(out.size() + " vectors written to " + file.toAbsolutePath());
    }

    static String row(String url) {
        Optional<String> token = TokenCodec.encode(url);
        if (token.isEmpty()) return url;
        ResolvedMedia m = TokenCodec.decode(token.get()).orElseThrow();
        SiteCodec c = m.codec();
        String body = "1" + c.code() + m.payload();
        // Only where v1 could carry the payload: it has no room for '_' (some Tenor keys) or payloads over 200 characters.
        String v1 = "<~" + body + TokenCodec.check(body) + "~>";
        if (TokenCodec.decode(v1).isEmpty()) v1 = "";
        String expires = c.expiresAtEpochSec(m.payload()).isPresent() ? String.valueOf(c.expiresAtEpochSec(m.payload()).getAsLong()) : "";
        return String.join("\t", url, token.get(), v1, c.name(), c.mediaUri(m.payload()).toString(), expires);
    }

    static List<String> links(String site) throws IOException {
        String path = "/corpus/" + site + ".txt";
        try (InputStream in = Objects.requireNonNull(TokenVectors.class.getResourceAsStream(path), "missing test resource " + path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).toList();
        }
    }
}
