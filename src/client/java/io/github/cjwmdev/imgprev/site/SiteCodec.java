package io.github.cjwmdev.imgprev.site;

import java.net.URI;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;

public interface SiteCodec {
    char code();
    /** For commands, e.g. "giphy". */
    String name();
    /** For people, e.g. "Giphy". */
    String title();
    /** The site itself in messages ("Couldn't connect to Discord"), where {@link #title} names a part of it. */
    default String siteName() { return title(); }
    /** What its tokens show as in chat. */
    Label label();
    /** Sender side, exact match. */
    Set<String> acceptedHosts();
    Set<String> allowedDownloadHosts();
    Pattern payloadPattern();
    Optional<String> payloadFrom(URI uri);
    /** The download address, on a constant host; throws {@link IllegalArgumentException} for a payload that can't be one. */
    URI mediaUri(String payload);

    default OptionalLong expiresAtEpochSec(String payload) { return OptionalLong.empty(); }
    /** Shown instead of loading once {@link #expiresAtEpochSec} has passed. */
    default String expiredMessage() { return "Link expired"; }
    /** What an HTTP error from this site means, when it means something particular here. */
    default Optional<String> statusMessage(int status) { return Optional.empty(); }

    /** Token v2 form of a valid payload: Base62 only, as short as the site allows. The default suits Base62 payloads. */
    default String compact(String payload) { return payload; }
    /** Inverse of {@link #compact}; empty when nothing compacts to {@code compact}. The result is checked against {@link #payloadPattern}. */
    default Optional<String> expand(String compact) { return Optional.of(compact); }
}
