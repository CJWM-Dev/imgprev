package dev.example.imgprev.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.example.imgprev.ImgPrevClient;
import dev.example.imgprev.token.ResolvedMedia;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/** {@code config/imgprev.json}. Missing fields keep their defaults; out-of-range numbers are clamped. */
public final class ImgPrevConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long MB = 1024L * 1024L;

    public boolean renderEnabled = true;
    public boolean requireShift = false;
    public int cacheBudgetMB = 128;
    public int maxDownloadMB = 8;
    public int maxDrawPx = 256;
    public Map<String, Boolean> sites = defaultSites();
    public String klipyApiKey = "";

    static Map<String, Boolean> defaultSites() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        for (String code : new String[] {"g", "i", "t", "e", "d", "z"}) m.put(code, true);
        m.put("k", false);
        return m;
    }

    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("imgprev.json"); }

    public static ImgPrevConfig load() {
        Path p = path();
        ImgPrevConfig cfg = null;
        if (Files.exists(p)) {
            try { cfg = GSON.fromJson(Files.readString(p), ImgPrevConfig.class); }
            catch (IOException | JsonParseException e) { ImgPrevClient.LOG.warn("Couldn't read {}, using defaults: {}", p, e.toString()); }
        }
        boolean fresh = cfg == null && !Files.exists(p);
        if (cfg == null) cfg = new ImgPrevConfig();
        cfg.sanitize();
        if (fresh) cfg.save();   // never overwrite a file we failed to parse
        return cfg;
    }

    public void save() {
        try { Files.createDirectories(path().getParent()); Files.writeString(path(), GSON.toJson(this)); }
        catch (IOException e) { ImgPrevClient.LOG.warn("Couldn't save {}: {}", path(), e.toString()); }
    }

    void sanitize() {
        cacheBudgetMB = Math.clamp(cacheBudgetMB, 16, 2048);
        maxDownloadMB = Math.clamp(maxDownloadMB, 1, 64);
        maxDrawPx = Math.clamp(maxDrawPx, 32, 512);
        if (sites == null) sites = new LinkedHashMap<>();
        defaultSites().forEach(sites::putIfAbsent);
        if (klipyApiKey == null) klipyApiKey = "";
    }

    public long cacheBudgetBytes() { return cacheBudgetMB * MB; }
    public int maxDownloadBytes() { return (int) (maxDownloadMB * MB); }

    public boolean siteEnabled(char code) { return sites.getOrDefault(String.valueOf(code), true); }
    public boolean allows(ResolvedMedia m) { return siteEnabled(m.codec().code()); }
}
