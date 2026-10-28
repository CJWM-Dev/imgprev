package dev.example.imgprev.site;

import java.util.Set;

/** The fixed facts about a site; each codec adds what its links and payloads look like. */
abstract class BaseCodec implements SiteCodec {
    private final char code;
    private final String name, title;
    private final Label label;
    private final Set<String> acceptedHosts, downloadHosts;

    BaseCodec(char code, String name, String title, Label label, Set<String> acceptedHosts, Set<String> downloadHosts) {
        this.code = code;
        this.name = name;
        this.title = title;
        this.label = label;
        this.acceptedHosts = acceptedHosts;
        this.downloadHosts = downloadHosts;
    }

    @Override public final char code() { return code; }
    @Override public final String name() { return name; }
    @Override public final String title() { return title; }
    @Override public final Label label() { return label; }
    @Override public final Set<String> acceptedHosts() { return acceptedHosts; }
    @Override public final Set<String> allowedDownloadHosts() { return downloadHosts; }
}
