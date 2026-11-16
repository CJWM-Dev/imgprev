package io.github.cjwmdev.imgprev.upload;

import java.util.List;

/** Where {@code /img upload} sends files: Litterbox deletes them after a while, Catbox keeps them. */
public enum UploadMode {
    TEMPORARY("Temporary", "Litterbox"),
    PERMANENT("Permanent", "Catbox");

    /** Litterbox's choices, shortest first. */
    public static final List<String> LIFETIMES = List.of("1h", "12h", "24h", "72h");
    public static final String DEFAULT_LIFETIME = "24h";

    private final String title, site;

    UploadMode(String title, String site) { this.title = title; this.site = site; }

    /** For the settings screen: "Temporary (Litterbox)". */
    public String title() { return title + " (" + site + ")"; }
    public String site() { return site; }

    /** "deleted after 24h" or "kept, and can't be deleted". */
    public String lifetimeText(String lifetime) {
        return this == TEMPORARY ? "deleted after " + lifetime : "kept, and can't be deleted";
    }
}
