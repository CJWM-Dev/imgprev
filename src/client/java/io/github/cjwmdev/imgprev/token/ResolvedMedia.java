package io.github.cjwmdev.imgprev.token;

import io.github.cjwmdev.imgprev.site.SiteCodec;

public record ResolvedMedia(SiteCodec codec, String payload) {
    public String key() { return codec.code() + payload; }
}
