package dev.example.imgprev.gametest;

import static dev.example.imgprev.gametest.ChatPreviewClientGameTest.GIF;
import static dev.example.imgprev.gametest.ChatPreviewClientGameTest.IMG;
import static dev.example.imgprev.gametest.ChatPreviewClientGameTest.awaitLoaded;
import static dev.example.imgprev.gametest.ChatPreviewClientGameTest.cacheEntry;
import static dev.example.imgprev.gametest.ChatPreviewClientGameTest.hover;
import static dev.example.imgprev.gametest.GameTests.assertEquals;
import static dev.example.imgprev.gametest.GameTests.assertTrue;
import static dev.example.imgprev.gametest.GameTests.lastChat;
import static dev.example.imgprev.gametest.GameTests.typeInChat;

import dev.example.imgprev.ImgPrevClient;
import dev.example.imgprev.config.ImgPrevConfig;
import dev.example.imgprev.image.ImageCache;
import dev.example.imgprev.token.TokenCodec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** /imgprev settings take effect, persist to config/imgprev.json, and reload from it. */
public final class SettingsClientGameTest implements FabricClientGameTest {
    static final String KLIPY = TokenCodec.encode("https://klipy.com/gifs/hello-hi-662").orElseThrow();

    @Override public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();

            typeInChat(ctx, "/imgprev");
            assertTrue(lastChat(ctx).startsWith("Cache: "), "status second line: " + lastChat(ctx));

            // Global off: new tokens stay as text.
            typeInChat(ctx, "/imgprev off");
            assertEquals("Image previews off", lastChat(ctx), "off feedback");
            system(ctx, "x " + GIF);
            assertEquals("x " + GIF, lastChat(ctx), "token left alone while off");
            typeInChat(ctx, "/imgprev on");
            system(ctx, "x " + GIF);
            assertEquals("x [🖼 GIF]", lastChat(ctx), "labelled again when on");

            // Per-site switch, persisted to disk.
            typeInChat(ctx, "/imgprev site giphy off");
            assertEquals("giphy previews off", lastChat(ctx), "site feedback");
            system(ctx, GIF + " " + IMG);
            assertEquals(GIF + " [🖼 Image]", lastChat(ctx), "only giphy left as text");
            assertEquals(false, ImgPrevConfig.load().sites.get("g"), "giphy off saved to disk");
            typeInChat(ctx, "/imgprev site giphy on");

            // Klipy without a key explains itself, without any network call.
            typeInChat(ctx, "/imgprev site klipy on");
            assertEquals("klipy previews on (set a key: /imgprev klipy key <key>)", lastChat(ctx), "klipy hint");
            ctx.setScreen(() -> new ChatScreen("", false));
            hover(ctx, "klipy: ", KLIPY, "probe-klipy");
            ImageCache.Entry klipy = awaitLoaded(ctx, KLIPY);
            assertEquals("Klipy needs an API key (config)", klipy.error(), "klipy without key");
            ctx.takeScreenshot("hover-klipy-no-key");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);

            typeInChat(ctx, "/imgprev klipy key abcd1234efgh5678");
            assertEquals("abcd1234efgh5678", ctx.computeOnClient(mc -> ImgPrevClient.CONFIG.klipyApiKey), "key stored");
            assertEquals("abcd1234efgh5678", ImgPrevConfig.load().klipyApiKey, "key saved");
            typeInChat(ctx, "/imgprev klipy clear");
            assertEquals("", ImgPrevConfig.load().klipyApiKey, "key cleared");

            // Shift mode: hovering only hints, and nothing is downloaded.
            typeInChat(ctx, "/imgprev shift on");
            ctx.runOnClient(mc -> ImgPrevClient.CACHE.clear());
            ctx.setScreen(() -> new ChatScreen("", false));
            hover(ctx, "shift: ", IMG, "probe-shift");
            ctx.waitTicks(3);
            assertTrue(cacheEntry(ctx, IMG) == null, "no download without Shift");
            ctx.takeScreenshot("hover-needs-shift");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            typeInChat(ctx, "/imgprev shift off");

            // Reload picks up hand edits to the file.
            try {
                String json = Files.readString(ImgPrevConfig.path()).replaceAll("\"maxDrawPx\": \\d+", "\"maxDrawPx\": 64");
                Files.writeString(ImgPrevConfig.path(), json);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            typeInChat(ctx, "/imgprev reload");
            assertEquals("Reloaded imgprev.json", lastChat(ctx), "reload feedback");
            assertEquals(64, ctx.computeOnClient(mc -> ImgPrevClient.CONFIG.maxDrawPx), "maxDrawPx after reload");

            typeInChat(ctx, "/imgprev cache clear");
            assertEquals("Preview cache cleared", lastChat(ctx), "cache clear feedback");
            assertEquals(0, ctx.computeOnClient(mc -> ImgPrevClient.CACHE.size()), "cache empty");
        }
    }

    static void system(ClientGameTestContext ctx, String text) {
        ctx.runOnClient(mc -> mc.gui.hud.getChat().addServerSystemMessage(Component.literal(text)));
    }
}
