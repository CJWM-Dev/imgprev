package dev.example.imgprev.site;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SiteRegistry {
    static final IdPathCodec POSTIMAGES = new IdPathCodec('p', "postimages", "Postimages", "i.postimg.cc", "postimg.cc");
    static final IdPathCodec IMGBB = new IdPathCodec('b', "imgbb", "ImgBB", "i.ibb.co", "ibb.co");
    /** Sites whose share page links {@link LinkConverter} looks up. */
    static final List<IdPathCodec> PAGES = List.of(POSTIMAGES, IMGBB);
    private static final List<SiteCodec> ALL = List.of(new GiphyCodec(), new ImgurCodec(), new TenorMediaCodec(),
        new DiscordEmojiCodec(), new DiscordAttachmentCodec(), new GyazoCodec(), new KlipyMediaCodec(), POSTIMAGES, IMGBB, new CatboxCodec());
    private static final Map<Character, SiteCodec> BY_CODE = ALL.stream().collect(Collectors.toUnmodifiableMap(SiteCodec::code, Function.identity()));
    /** In {@link #ALL}'s order, so hosts shared by two codecs (Discord) try them the same way every time. */
    private static final Map<String, List<SiteCodec>> BY_HOST = ALL.stream()
        .flatMap(c -> c.acceptedHosts().stream().map(h -> Map.entry(h, c)))
        .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toUnmodifiableList())));

    private SiteRegistry() {}

    public static List<SiteCodec> all() { return ALL; }
    /** Every codec accepting this (lowercase) host; more than one may match. */
    public static List<SiteCodec> byHost(String h) { return BY_HOST.getOrDefault(h, List.of()); }
    public static Optional<SiteCodec> byCode(char c) { return Optional.ofNullable(BY_CODE.get(c)); }
}
