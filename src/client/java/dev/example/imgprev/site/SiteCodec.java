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
    String label();                                     // shown in chat, e.g. "GIF", "Image", "Discord"
    Set<String> acceptedHosts();                        // sender side, exact match
    Pattern payloadPattern();
    Optional<String> payloadFrom(URI uri);
    CompletableFuture<URI> mediaUri(String payload);    // constant host template
    Set<String> allowedDownloadHosts();
    default OptionalLong expiresAtEpochSec(String payload) { return OptionalLong.empty(); }
}
