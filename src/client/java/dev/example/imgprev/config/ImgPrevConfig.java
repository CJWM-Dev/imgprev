package dev.example.imgprev.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.example.imgprev.ImgPrevClient;
import dev.example.imgprev.site.Label;
import dev.example.imgprev.site.SiteCodec;
import dev.example.imgprev.site.SiteRegistry;
import dev.example.imgprev.token.ResolvedMedia;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
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
    /** Colour of each kind of label, keyed by {@link Label#text}: a chat colour name such as {@code aqua}, or {@code #RRGGBB}. */
    public Map<String, String> labelColors = defaultLabelColors();

    /** Minecraft's 16 chat colours, in the order the settings screen offers them. */
    public static final List<TextColor> CHAT_COLORS = List.of(TextColor.AQUA, TextColor.DARK_AQUA, TextColor.BLUE, TextColor.DARK_BLUE,
        TextColor.LIGHT_PURPLE, TextColor.DARK_PURPLE, TextColor.RED, TextColor.DARK_RED, TextColor.GOLD, TextColor.YELLOW,
        TextColor.GREEN, TextColor.DARK_GREEN, TextColor.WHITE, TextColor.GRAY, TextColor.DARK_GRAY, TextColor.BLACK);
    /** Extra named presets beyond the chat colours; saved as their hex value. Blurple is Discord's older, lighter one. */
    static final Map<String, Integer> EXTRA_COLORS = extraColors();
    /** Everything the settings screen offers in its colour lists: the chat colours, then the extras. */
    public static final List<TextColor> PRESET_COLORS = presetColors();

    static final Pattern BARE_HEX = Pattern.compile("[0-9a-f]{6}"), COLOR = Pattern.compile("#[0-9a-f]{6}|[a-z_]+");
    private static final Map<Label, TextColor> DEFAULT_COLORS = defaultColors();
    /** Label colours as saved text → parsed, so chat lines don't re-parse them; only ever holds a handful. */
    private static final Map<String, Optional<TextColor>> PARSED = new ConcurrentHashMap<>();

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

    private static Map<Label, TextColor> defaultColors() {
        Map<Label, TextColor> m = new EnumMap<>(Label.class);
        for (Label l : Label.values()) m.put(l, parseColor(l.defaultColor()).orElseThrow());
        return m;
    }

    static Map<String, Boolean> defaultSites() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        for (SiteCodec c : SiteRegistry.all()) m.put(String.valueOf(c.code()), true);
        return m;
    }

    public static Map<String, String> defaultLabelColors() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Label l : Label.values()) m.put(l.text(), l.defaultColor());
        return m;
    }

    public static TextColor defaultLabelColor(Label label) { return DEFAULT_COLORS.get(label); }

    static List<String> defaultCommands() { return new ArrayList<>(List.of("msg", "tell", "w", "r", "pc", "gc", "oc", "ac")); }

    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("imgprev.json"); }

    public static ImgPrevConfig load() { return load(path()); }

    static ImgPrevConfig load(Path p) {
        boolean exists = Files.exists(p);
        ImgPrevConfig cfg = null;
        if (exists) {
            try { cfg = GSON.fromJson(Files.readString(p), ImgPrevConfig.class); }
            catch (IOException | JsonParseException e) { ImgPrevClient.LOG.warn("Couldn't read {}, using defaults: {}", p, e.toString()); }
        }
        boolean parsed = cfg != null;
        if (cfg == null) cfg = new ImgPrevConfig();
        boolean migrated = cfg.migrate();
        cfg.sanitize();
        if (!exists || (parsed && migrated)) cfg.save(p);   // never overwrite a file we failed to parse
        return cfg;
    }

    /** Reads the file again into this object, so everything holding it (screens, lambdas) sees the new values. */
    public void reload() { copyFrom(load()); }

    void copyFrom(ImgPrevConfig other) {
        try {
            for (Field f : ImgPrevConfig.class.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers())) f.set(this, f.get(other));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 0.1.x's defaults were too small for most GIFs; files still on them move to the new ones, chosen values stay. */
    boolean migrate() {
        if (configVersion >= VERSION) return false;
        if (maxDownloadMB == 8) maxDownloadMB = 32;
        if (cacheBudgetMB == 128) cacheBudgetMB = 256;
        configVersion = VERSION;
        return true;
    }

    public void save() { save(path()); }

    /** Written beside the file, then moved over it, so a crash mid-write never leaves half a file. */
    void save(Path p) {
        Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(tmp, GSON.toJson(this));
            try {
                Files.move(tmp, p, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            ImgPrevClient.LOG.warn("Couldn't save {}: {}", p, e.toString());
        }
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
        for (Label label : Label.values()) {
            String chosen = labelColors == null ? null : labelColors.get(label.text());
            colors.put(label.text(), (chosen == null ? Optional.<TextColor>empty() : parseColor(chosen)).orElse(defaultLabelColor(label)).serialize());
        }
        labelColors = colors;
    }

    public long cacheBudgetBytes() { return cacheBudgetMB * MB; }
    public int maxDownloadBytes() { return (int) (maxDownloadMB * MB); }

    public boolean siteEnabled(char code) { return sites.getOrDefault(String.valueOf(code), true); }
    public void setSiteEnabled(char code, boolean on) { sites.put(String.valueOf(code), on); }
    public boolean allows(ResolvedMedia m) { return siteEnabled(m.codec().code()); }

    /** The colour for one kind of label. */
    public TextColor labelColor(Label label) {
        String chosen = labelColors.get(label.text());
        if (chosen == null) return defaultLabelColor(label);
        if (PARSED.size() > 64) PARSED.clear();
        return PARSED.computeIfAbsent(chosen, ImgPrevConfig::parseColor).orElse(defaultLabelColor(label));
    }

    public void setLabelColor(Label label, TextColor color) { labelColors.put(label.text(), color.serialize()); }

    /** A chat colour or preset name ({@code dark_red}, {@code Sky Blue}) or a hex colour with or without {@code #}. */
    public static Optional<TextColor> parseColor(String s) {
        String c = s.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
        Integer extra = EXTRA_COLORS.get(c);
        if (extra != null) return Optional.of(TextColor.fromRgb(extra));
        if (BARE_HEX.matcher(c).matches()) c = "#" + c;
        return COLOR.matcher(c).matches() ? TextColor.parseColor(c).result() : Optional.empty();
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
