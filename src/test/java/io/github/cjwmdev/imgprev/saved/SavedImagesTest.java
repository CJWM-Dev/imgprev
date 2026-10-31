package io.github.cjwmdev.imgprev.saved;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SavedImagesTest {
    static final String GIPHY = "https://media.giphy.com/media/XJtM2nNFCzT3etvzOB/giphy.gif";
    static final String GIF = TokenCodec.encode(GIPHY).orElseThrow();
    static final String IMG = TokenCodec.encode("https://i.imgur.com/AbC12xY.png").orElseThrow();

    @TempDir Path dir;

    SavedImages fresh() { return new SavedImages(dir.resolve("imgprev-saved.json")); }

    @Test void namesAreLetterNumbersUnderscoresAndDashes() {
        for (String ok : new String[] {"funnycat", "cat_2", "a-b", "x".repeat(SavedImages.MAX_NAME)}) assertTrue(SavedImages.nameProblem(ok).isEmpty(), ok);
        for (String bad : new String[] {"", "funny.cat", "cat+", "x".repeat(SavedImages.MAX_NAME + 1), "list", "save", "remove"}) {
            assertTrue(SavedImages.nameProblem(bad).isPresent(), bad);
        }
        assertEquals("funnycat", SavedImages.normalise(" FunnyCat "));
    }

    @Test void namesIgnoreCase() {
        SavedImages saved = fresh();
        saved.put("FunnyCat", new SavedImages.Entry(GIF, GIPHY));
        assertEquals(GIF, saved.get("funnycat").orElseThrow().token());
        assertEquals(List.of("funnycat"), List.copyOf(saved.all().keySet()));
        assertEquals(GIPHY, saved.put("FUNNYCAT", new SavedImages.Entry(IMG, "other")).orElseThrow().link(), "replacing returns the old entry");
        assertEquals(1, saved.all().size());
    }

    @Test void savedEntriesSurviveAReload() {
        SavedImages saved = fresh();
        saved.put("cat", new SavedImages.Entry(GIF, GIPHY));
        saved.put("dog", new SavedImages.Entry(IMG, IMG));
        SavedImages again = SavedImages.load(dir.resolve("imgprev-saved.json"));
        assertEquals(saved.all(), again.all());
        assertTrue(again.remove("cat").isPresent());
        assertFalse(again.remove("cat").isPresent());
        assertEquals(List.of("dog"), List.copyOf(SavedImages.load(dir.resolve("imgprev-saved.json")).all().keySet()));
    }

    @Test void handWrittenEntriesGetTheirToken() throws IOException {
        Path file = dir.resolve("imgprev-saved.json");
        Files.writeString(file, """
            {"images": {
              "Cat": {"link": "%s"},
              "pasted": {"link": "%s"},
              "bad token": {"token": "%s", "link": "%s"},
              "stale": {"token": "<~Gnope0~>", "link": "%s"},
              "nothing": {"link": "https://example.com/x.png"},
              "empty": null
            }}""".formatted(GIPHY, IMG, GIF, GIPHY, GIPHY));
        SavedImages saved = SavedImages.load(file);
        assertEquals(List.of("cat", "pasted", "stale"), List.copyOf(saved.all().keySet()));
        assertEquals(GIF, saved.get("cat").orElseThrow().token(), "converted from its link");
        assertEquals(IMG, saved.get("pasted").orElseThrow().token(), "a token works as the link");
        assertEquals(GIF, saved.get("stale").orElseThrow().token(), "an unreadable token is rebuilt from the link");
    }

    @Test void anUnreadableFileIsKeptAside() throws IOException {
        Path file = dir.resolve("imgprev-saved.json");
        Files.writeString(file, "{\"images\": {\"cat\": ");
        SavedImages saved = SavedImages.load(file);
        assertTrue(saved.all().isEmpty());
        assertEquals("{\"images\": {\"cat\": ", Files.readString(dir.resolve("imgprev-saved.json.broken")));
        saved.put("dog", new SavedImages.Entry(IMG, IMG));
        assertEquals(List.of("dog"), List.copyOf(SavedImages.load(file).all().keySet()), "saving afterwards works");
    }

    @Test void shortcutsBecomeTokens() {
        SavedImages saved = fresh();
        saved.put("cat", new SavedImages.Entry(GIF, GIPHY));
        saved.put("dog", new SavedImages.Entry(IMG, IMG));
        assertEquals("look " + GIF + "!", saved.expand("look :cat:!"));
        assertEquals(GIF + IMG + " " + GIF, saved.expand(":CAT::dog: :cat:"), "any case, back to back");
        assertEquals("hi :bird: 12:30:45", saved.expand("hi :bird: 12:30:45"), "unknown names stay");
        assertEquals("at 12:30" + GIF, saved.expand("at 12:30:cat:"), "a colon can close one candidate and open the next");
        assertEquals("see https://host/a:cat:b", saved.expand("see https://host/a:cat:b"), "not inside links");
        assertEquals("plain", saved.expand("plain"));
        assertEquals(":cat:", fresh().expand(":cat:"), "nothing saved");
    }
}
