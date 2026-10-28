package dev.example.imgprev.site;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class SiteRegistry {
    static final KlipyCodec KLIPY_API = new KlipyCodec();
    /** Sites whose share page links {@link LinkConverter} looks up. */
    static final List<IdPathCodec> PAGES = List.of(
        new IdPathCodec('p', "postimages", "Postimages", "i.postimg.cc", "postimg.cc"),
        new IdPathCodec('b', "imgbb", "ImgBB", "i.ibb.co", "ibb.co"));
    private static final List<SiteCodec> ALL = List.of(new GiphyCodec(), new ImgurCodec(), new TenorMediaCodec(),
        new DiscordEmojiCodec(), new DiscordAttachmentCodec(), new GyazoCodec(), new KlipyMediaCodec(), KLIPY_API,
        PAGES.get(0), PAGES.get(1), new CatboxCodec());

    private SiteRegistry() {}

    public static List<SiteCodec> all() { return ALL; }
    /** Every codec accepting this (lowercase) host; more than one may match. */
    public static List<SiteCodec> byHost(String h) { return ALL.stream().filter(c -> c.acceptedHosts().contains(h)).toList(); }
    public static Optional<SiteCodec> byCode(char c) { return ALL.stream().filter(s -> s.code() == c).findFirst(); }
    public static Optional<SiteCodec> byName(String n) { return ALL.stream().filter(s -> s.name().equals(n)).findFirst(); }

    /** Read on every Klipy lookup, so config reloads apply immediately. */
    public static void klipyApiKey(Supplier<String> key) { KlipyCodec.apiKey = key; }
}
