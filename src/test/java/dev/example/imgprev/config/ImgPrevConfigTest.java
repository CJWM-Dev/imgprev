package dev.example.imgprev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
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
