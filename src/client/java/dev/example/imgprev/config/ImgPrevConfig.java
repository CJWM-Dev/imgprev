package dev.example.imgprev.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.example.imgprev.ImgPrevClient;
import dev.example.imgprev.token.ResolvedMedia;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.TextColor;

/** {@code config/imgprev.json}. Missing fields keep their defaults; out-of-range numbers are clamped. */
public final class ImgPrevConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long MB = 1024L * 1024L;
    static final int VERSION = 1;
    public static final int MAX_DOWNLOAD_MB = 128;

    /** 0 in files written by 0.1.x, which had no version. */
    public int configVersion;
    public boolean renderEnabled = true;
    public boolean requireShift = false;
    public int cacheBudgetMB = 256;
    public int maxDownloadMB = 32;
    public int maxDrawPx = 256;
    public Map<String, Boolean> sites = defaultSites();
    /** Off by default: people without ImgPrev see the token instead of the link. */
    public boolean autoConvertLinks = false;
    /** Commands whose text is chat (Hypixel's party/guild/officer chat, private messages), converted like chat. */
    public List<String> autoConvertCommands = defaultCommands();
    /** Colour of each kind of label ({@code GIF}, {@code Image}, …): a chat colour name such as {@code aqua}, or {@code #RRGGBB}. */
    public Map<String, String> labelColors = defaultLabelColors();

    /** The kinds of label, in the order the settings screen and {@code /imgprev colour} offer them. */
    public static final List<String> LABELS = List.of("GIF", "Image", "Emoji", "Discord");
    /** Minecraft's 16 chat colours, in the order the settings screen offers them. */
    public static final List<TextColor> CHAT_COLORS = List.of(TextColor.AQUA, TextColor.DARK_AQUA, TextColor.BLUE, TextColor.DARK_BLUE,
        TextColor.LIGHT_PURPLE, TextColor.DARK_PURPLE, TextColor.RED, TextColor.DARK_RED, TextColor.GOLD, TextColor.YELLOW,
        TextColor.GREEN, TextColor.DARK_GREEN, TextColor.WHITE, TextColor.GRAY, TextColor.DARK_GRAY, TextColor.BLACK);
    /** Extra named presets beyond the chat colours; saved as their hex value. Blurple is Discord's older, lighter one. */
    static final Map<String, Integer> EXTRA_COLORS = extraColors();
    /** Everything the settings screen offers in its colour lists: the chat colours, then the extras. */
    public static final List<TextColor> PRESET_COLORS = presetColors();

    private static Map<String, Integer> extraColors() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("orange", 0xFF8800);
        m.put("pink", 0xFF69B4);
        m.put("lavender", 0xB39DDB);
        m.put("mint", 0x98FF98);
        m.put("sky_blue", 0x87CEEB);
        m.put("blurple", 0x7289DA);
        return m;
    }

    private static List<TextColor> presetColors() {
        List<TextColor> all = new ArrayList<>(CHAT_COLORS);
        EXTRA_COLORS.values().forEach(rgb -> all.add(TextColor.fromRgb(rgb)));
        return List.copyOf(all);
    }

    static Map<String, Boolean> defaultSites() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        for (String code : new String[] {"g", "i", "t", "e", "d", "z", "y", "p", "b", "c"}) m.put(code, true);
        return m;
    }

    /** Discord's is its older, lighter blurple, which reads better on chat's dark background than the current one. */
    public static Map<String, String> defaultLabelColors() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("GIF", "aqua");
        m.put("Image", "green");
        m.put("Emoji", "yellow");
        m.put("Discord", "#7289DA");
        return m;
    }

    public static TextColor defaultLabelColor(String label) {
        return parseColor(defaultLabelColors().getOrDefault(label, "aqua")).orElse(TextColor.AQUA);
    }

    static List<String> defaultCommands() { return new ArrayList<>(List.of("msg", "tell", "w", "r", "pc", "gc", "oc", "ac")); }

    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("imgprev.json"); }

    public static ImgPrevConfig load() {
        Path p = path();
        ImgPrevConfig cfg = null;
        if (Files.exists(p)) {
            try { cfg = GSON.fromJson(Files.readString(p), ImgPrevConfig.class); }
            catch (IOException | JsonParseException e) { ImgPrevClient.LOG.warn("Couldn't read {}, using defaults: {}", p, e.toString()); }
        }
        boolean parsed = cfg != null, fresh = !parsed && !Files.exists(p);
        if (cfg == null) cfg = new ImgPrevConfig();
        boolean migrated = cfg.migrate();
        cfg.sanitize();
        if (fresh || (parsed && migrated)) cfg.save();   // never overwrite a file we failed to parse
        return cfg;
    }

    /** 0.1.x's defaults were too small for most GIFs; files still on them move to the new ones, chosen values stay. */
    boolean migrate() {
        if (configVersion >= VERSION) return false;
        if (maxDownloadMB == 8) maxDownloadMB = 32;
        if (cacheBudgetMB == 128) cacheBudgetMB = 256;
        configVersion = VERSION;
        return true;
    }

    public void save() {
        try { Files.createDirectories(path().getParent()); Files.writeString(path(), GSON.toJson(this)); }
        catch (IOException e) { ImgPrevClient.LOG.warn("Couldn't save {}: {}", path(), e.toString()); }
    }

    void sanitize() {
        cacheBudgetMB = Math.clamp(cacheBudgetMB, 16, 2048);
        maxDownloadMB = Math.clamp(maxDownloadMB, 1, MAX_DOWNLOAD_MB);
        maxDrawPx = Math.clamp(maxDrawPx, 32, 512);
        if (sites == null) sites = new LinkedHashMap<>();
        defaultSites().forEach(sites::putIfAbsent);
        sites.remove("k");   // Klipy slug tokens, removed after 0.2.0; the old klipyApiKey field is dropped on the next save
        if (autoConvertCommands == null) autoConvertCommands = defaultCommands();
        autoConvertCommands.replaceAll(c -> c.strip().replaceFirst("^/", "").toLowerCase(Locale.ROOT));
        autoConvertCommands.removeIf(String::isEmpty);
        Map<String, String> colors = new LinkedHashMap<>();
        for (String label : LABELS) {
            String chosen = labelColors == null ? null : labelColors.get(label);
            colors.put(label, (chosen == null ? Optional.<TextColor>empty() : parseColor(chosen)).orElse(defaultLabelColor(label)).serialize());
        }
        labelColors = colors;
    }

    public long cacheBudgetBytes() { return cacheBudgetMB * MB; }
    public int maxDownloadBytes() { return (int) (maxDownloadMB * MB); }

    public boolean siteEnabled(char code) { return sites.getOrDefault(String.valueOf(code), true); }
    public boolean allows(ResolvedMedia m) { return siteEnabled(m.codec().code()); }

    /** The colour for one kind of label, e.g. {@code "GIF"}. */
    public TextColor labelColor(String label) {
        String chosen = labelColors.get(label);
        return (chosen == null ? Optional.<TextColor>empty() : parseColor(chosen)).orElse(defaultLabelColor(label));
    }

    /** A chat colour or preset name ({@code dark_red}, {@code Sky Blue}) or a hex colour with or without {@code #}. */
    public static Optional<TextColor> parseColor(String s) {
        String c = s.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (EXTRA_COLORS.containsKey(c)) return Optional.of(TextColor.fromRgb(EXTRA_COLORS.get(c)));
        if (c.matches("[0-9a-f]{6}")) c = "#" + c;
        return c.matches("#[0-9a-f]{6}|[a-z_]+") ? TextColor.parseColor(c).result() : Optional.empty();
    }

    /** "dark_aqua" → "Dark aqua", #FF8800 → "Orange"; other hex colours as they are. */
    public static String colorName(TextColor c) {
        String s = c.serialize();
        if (s.startsWith("#")) {
            s = EXTRA_COLORS.entrySet().stream().filter(e -> e.getValue() == c.getValue()).map(Map.Entry::getKey).findFirst().orElse(s);
            if (s.startsWith("#")) return s;
        }
        s = s.replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
