package dev.example.imgprev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

class ImgPrevConfigTest {
    static ImgPrevConfig parse(String json) { return new Gson().fromJson(json, ImgPrevConfig.class); }

    @Test void oldDefaultsMoveToTheNewOnes() {
        ImgPrevConfig cfg = parse("{\"cacheBudgetMB\": 128, \"maxDownloadMB\": 8, \"maxDrawPx\": 300}");   // as 0.1.x wrote it
        assertTrue(cfg.migrate());
        assertEquals(32, cfg.maxDownloadMB);
        assertEquals(256, cfg.cacheBudgetMB);
        assertEquals(300, cfg.maxDrawPx);
        assertEquals(ImgPrevConfig.VERSION, cfg.configVersion);
        assertFalse(cfg.migrate(), "only once");
    }

    @Test void chosenValuesStay() {
        ImgPrevConfig cfg = parse("{\"cacheBudgetMB\": 64, \"maxDownloadMB\": 4}");
        assertTrue(cfg.migrate());
        assertEquals(4, cfg.maxDownloadMB);
        assertEquals(64, cfg.cacheBudgetMB);
    }

    @Test void currentFilesAreLeftAlone() {
        ImgPrevConfig cfg = parse("{\"configVersion\": " + ImgPrevConfig.VERSION + ", \"maxDownloadMB\": 8}");
        assertFalse(cfg.migrate());
        assertEquals(8, cfg.maxDownloadMB);
    }

    @Test void coloursAcceptNamesPresetsAndHex() {
        assertEquals(TextColor.GOLD, ImgPrevConfig.parseColor("gold").orElseThrow());
        assertEquals(TextColor.DARK_RED, ImgPrevConfig.parseColor(" Dark Red ").orElseThrow());
        assertEquals(0xFF8800, ImgPrevConfig.parseColor("orange").orElseThrow().getValue());
        assertEquals(0x87CEEB, ImgPrevConfig.parseColor("Sky Blue").orElseThrow().getValue());
        assertEquals(0x123456, ImgPrevConfig.parseColor("#123456").orElseThrow().getValue());
        assertEquals(0x123456, ImgPrevConfig.parseColor("123456").orElseThrow().getValue());
        for (String bad : new String[] {"", "rainbow", "#ff88", "#ff88001", "#gg0000", "§c"}) assertTrue(ImgPrevConfig.parseColor(bad).isEmpty(), bad);
    }

    @Test void eachKindOfLabelHasItsOwnDefault() {
        ImgPrevConfig cfg = parse("{}");
        cfg.sanitize();
        assertEquals(TextColor.AQUA, cfg.labelColor("GIF"));
        assertEquals(TextColor.GREEN, cfg.labelColor("Image"));
        assertEquals(TextColor.YELLOW, cfg.labelColor("Emoji"));
        assertEquals(0x7289DA, cfg.labelColor("Discord").getValue());
    }

    @Test void labelColoursAreSavedReadably() {
        ImgPrevConfig cfg = parse("{\"labelColors\": {\"GIF\": \"#ff8800\", \"Image\": \"Light Purple\", \"Emoji\": \"mint\"}}");
        cfg.sanitize();
        assertEquals("#FF8800", cfg.labelColors.get("GIF"));
        assertEquals("light_purple", cfg.labelColors.get("Image"));
        assertEquals("#98FF98", cfg.labelColors.get("Emoji"));
        assertEquals("#7289DA", cfg.labelColors.get("Discord"), "missing kinds get their default");
    }

    @Test void badLabelColoursFallBackPerKind() {
        for (String json : new String[] {"{\"labelColors\": null}", "{\"labelColors\": {\"GIF\": \"rainbow\", \"Image\": null}}"}) {
            ImgPrevConfig cfg = parse(json);
            cfg.sanitize();
            assertEquals("aqua", cfg.labelColors.get("GIF"), json);
            assertEquals("green", cfg.labelColors.get("Image"), json);
        }
    }

    @Test void colourNamesReadNaturally() {
        assertEquals("Dark aqua", ImgPrevConfig.colorName(TextColor.DARK_AQUA));
        assertEquals("Orange", ImgPrevConfig.colorName(TextColor.fromRgb(0xFF8800)));
        assertEquals("Sky blue", ImgPrevConfig.colorName(TextColor.fromRgb(0x87CEEB)));
        assertEquals("#123456", ImgPrevConfig.colorName(TextColor.fromRgb(0x123456)));
    }

    @Test void removedKlipyKeySettingsAreDropped() {
        ImgPrevConfig cfg = parse("{\"klipyApiKey\": \"abcd1234efgh5678\", \"sites\": {\"k\": true, \"g\": false}}");   // as 0.2.0 wrote it
        cfg.sanitize();
        assertFalse(cfg.sites.containsKey("k"));
        assertFalse(cfg.siteEnabled('g'), "other choices stay");
        assertFalse(new Gson().toJson(cfg).contains("klipy"), "not written back");
    }

    @Test void downloadLimitIsClamped() {
        ImgPrevConfig cfg = parse("{\"maxDownloadMB\": 1000}");
        cfg.sanitize();
        assertEquals(ImgPrevConfig.MAX_DOWNLOAD_MB, cfg.maxDownloadMB);
    }
}
