package dev.example.imgprev.token;

import dev.example.imgprev.site.SiteCodec;

public record ResolvedMedia(SiteCodec codec, String payload) {
    public String key() { return codec.code() + payload; }
}
