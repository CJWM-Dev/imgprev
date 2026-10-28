package dev.example.imgprev.site;

import java.net.URI;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public interface SiteCodec {
    char code();
    String name();                                      // for commands, e.g. "giphy"
    String title();                                     // for people, e.g. "Giphy"
    String label();                                     // shown in chat, e.g. "GIF", "Image", "Discord"
    Set<String> acceptedHosts();                        // sender side, exact match
    Pattern payloadPattern();
    Optional<String> payloadFrom(URI uri);
    CompletableFuture<URI> mediaUri(String payload);    // constant host template
    Set<String> allowedDownloadHosts();
    default OptionalLong expiresAtEpochSec(String payload) { return OptionalLong.empty(); }
    /** Token v2 form of a valid payload: Base62 only, as short as the site allows. The default suits Base62 payloads. */
    default String compact(String payload) { return payload; }
    /** Inverse of {@link #compact}; empty when nothing compacts to {@code compact}. The result is checked against {@link #payloadPattern}. */
    default Optional<String> expand(String compact) { return Optional.of(compact); }
}
