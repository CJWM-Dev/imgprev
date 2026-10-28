package io.github.cjwmdev.imgprev.image;

import java.io.IOException;

/** A failure whose message is shown to the player as-is in the preview tooltip. */
public final class MediaException extends IOException {
    public MediaException(String userMessage) { super(userMessage); }
}
