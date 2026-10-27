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
import net.fabricmc.loader.api.FabricLoader;

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
    public String klipyApiKey = "";
    /** Off by default: people without ImgPrev see the token instead of the link. */
    public boolean autoConvertLinks = false;
    /** Commands whose text is chat (Hypixel's party/guild/officer chat, private messages), converted like chat. */
    public List<String> autoConvertCommands = defaultCommands();

    static Map<String, Boolean> defaultSites() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        for (String code : new String[] {"g", "i", "t", "e", "d", "z", "y"}) m.put(code, true);
        m.put("k", false);   // old Klipy slug tokens: every viewer needs their own API key
        return m;
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
        if (klipyApiKey == null) klipyApiKey = "";
        if (autoConvertCommands == null) autoConvertCommands = defaultCommands();
        autoConvertCommands.replaceAll(c -> c.strip().replaceFirst("^/", "").toLowerCase(Locale.ROOT));
        autoConvertCommands.removeIf(String::isEmpty);
    }

    public long cacheBudgetBytes() { return cacheBudgetMB * MB; }
    public int maxDownloadBytes() { return (int) (maxDownloadMB * MB); }

    public boolean siteEnabled(char code) { return sites.getOrDefault(String.valueOf(code), true); }
    public boolean allows(ResolvedMedia m) { return siteEnabled(m.codec().code()); }
}
