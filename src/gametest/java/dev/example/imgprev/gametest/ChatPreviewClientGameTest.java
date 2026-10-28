package dev.example.imgprev.gametest;

import static dev.example.imgprev.gametest.GameTests.assertEquals;
import static dev.example.imgprev.gametest.GameTests.assertTrue;
import static dev.example.imgprev.gametest.GameTests.hoverChatInsertion;
import static dev.example.imgprev.gametest.GameTests.lastChat;
import static dev.example.imgprev.gametest.GameTests.typeInChat;

import dev.example.imgprev.ImgPrevClient;
import dev.example.imgprev.image.FrameDecoder;
import dev.example.imgprev.image.ImageCache;
import dev.example.imgprev.token.TokenCodec;
import java.io.IOException;
import java.util.Base64;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tokens in received chat become labels, for server system messages and player chat; hovering a label
 * downloads (live network) and shows the image, or explains why not.
 */
public final class ChatPreviewClientGameTest implements FabricClientGameTest {
    static final String GIF = TokenCodec.encode("https://media.giphy.com/media/XJtM2nNFCzT3etvzOB/giphy.gif").orElseThrow();
    static final String IMG = TokenCodec.encode("https://i.imgur.com/AbC12xY.png").orElseThrow();
    static final String TENOR = TokenCodec.encode("https://media.tenor.com/1S3s5iesWX0AAAAM/despierto-gato.gif").orElseThrow();
    // Tenor answers 404 for unknown keys; full-size Giphy GIFs answer 200 with a "content not available" GIF instead.
    static final String MISSING = TokenCodec.encode("https://media.tenor.com/ZZZZZnotreal0AAAAC/x.gif").orElseThrow();
    static final String EXPIRED = TokenCodec.encode("https://cdn.discordapp.com/attachments/1100000000000000001/1300000000000000002/cat.png"
        + "?ex=66b847f9&is=66b6f679&hm=" + "ab".repeat(32) + "&").orElseThrow();   // signed August 2024, long expired

    @Override public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();

            // --- Chat labels ---
            // Server system message (how Hypixel sends most chat).
            ctx.runOnClient(mc -> mc.gui.hud.getChat().addServerSystemMessage(Component.literal("[VIP] Steve: look " + GIF + " and " + IMG)));
            assertEquals("[VIP] Steve: look [▶ GIF] and [⧈ Image]", lastChat(ctx), "system message");

            // Player chat round-trips through the integrated server.
            typeInChat(ctx, "check " + GIF);
            ctx.waitFor(mc -> lastChat(mc).contains("check"));
            assertEquals("<Player0> check [▶ GIF]", lastChat(ctx), "player chat");
            ctx.takeScreenshot("chat-labels");

            // --- Hover previews ---
            ctx.setScreen(() -> new ChatScreen("", false));

            hover(ctx, "giphy: ", GIF, "probe-gif");
            ImageCache.Entry gif = awaitLoaded(ctx, GIF);
            assertEquals(ImageCache.State.READY, gif.state(), "giphy state (error: " + gif.error() + ")");
            assertTrue(gif.texture().frameCount() > 1, "giphy GIF is animated, frames=" + gif.texture().frameCount());
            int guiScale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
            assertTrue(guiScale == 1 || gif.texture().textureW() > gif.texture().displayW(),
                "decoded at screen resolution: " + gif.texture().textureW() + " px for " + gif.texture().displayW() + " GUI px at scale " + guiScale);
            ctx.waitTicks(2);
            ctx.takeScreenshot("hover-giphy");
            ctx.waitTicks(7);
            ctx.takeScreenshot("hover-giphy-later");

            // With no budget left, loading Tenor must evict the least-recently-hovered texture (Giphy) but not itself.
            ctx.runOnClient(mc -> ImgPrevClient.CONFIG.cacheBudgetMB = 0);
            hover(ctx, "tenor: ", TENOR, "probe-tenor");
            ImageCache.Entry tenor = awaitLoaded(ctx, TENOR);
            assertEquals(ImageCache.State.READY, tenor.state(), "tenor state (error: " + tenor.error() + ")");
            assertTrue(cacheEntry(ctx, GIF) == null, "giphy evicted over budget");
            assertEquals(tenor.texture().bytes(), ctx.computeOnClient(mc -> ImgPrevClient.CACHE.usedBytes()), "bytes after eviction");
            assertTrue(tenor.texture().textureW() > 220, "medium (220 px) Tenor link loads the full-size GIF: " + tenor.texture().textureW());
            ctx.runOnClient(mc -> ImgPrevClient.CONFIG.cacheBudgetMB = 256);
            ctx.waitTicks(2);
            ctx.takeScreenshot("hover-tenor");

            hover(ctx, "missing: ", MISSING, "probe-missing");
            ImageCache.Entry missing = awaitLoaded(ctx, MISSING);
            assertEquals(ImageCache.State.FAILED, missing.state(), "missing state");
            assertEquals("Image not found", missing.error(), "missing error");
            ctx.takeScreenshot("hover-missing");

            hover(ctx, "discord: ", EXPIRED, "probe-expired");
            ctx.waitTicks(2);
            assertTrue(cacheEntry(ctx, EXPIRED) == null, "expired Discord link never hits the network");
            ctx.takeScreenshot("hover-discord-expired");

            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            ctx.runOnClient(mc -> ImgPrevClient.CACHE.clear());

            // The bundled WebP reader registers and decodes under Fabric's class loading, not only in unit tests.
            FrameDecoder.Frames<int[]> webp;
            try { webp = FrameDecoder.decode(Base64.getDecoder().decode(WEBP_4X4_RED_THEN_BLUE), 256, 256); }
            catch (IOException e) { throw new AssertionError("WebP decode in game", e); }
            assertEquals(2, webp.count(), "webp frames");
            assertEquals(150, webp.delaysMs()[0], "webp first frame duration");
            assertEquals(0xFF0000FF, webp.frames().get(1)[3 * 4 + 3], "webp frame 2 pixel (3,3)");
            assertEquals(0xFFFF0000, webp.frames().get(1)[0], "webp frame 2 keeps frame 1 underneath");
        }
    }

    /** Animated 4×4 WebP: red for 150 ms, then a 2×2 blue patch at (2,2) for 250 ms (made with the unit tests' TestWebP). */
    static final String WEBP_4X4_RED_THEN_BLUE = "UklGRoAAAABXRUJQVlA4WAoAAAASAAAAAwAAAwAAQU5JTQYAAAAAAAAAAABBTk1GJgAAAAAAAAAAAAMAAAMAAJYAAABWUDhM"
        + "DQAAAC8DwAAAKED/C9D/AgAAQU5NRiYAAAABAAABAAABAAABAAD6AAAAVlA4TA0AAAAvAUAAAChAAfrf/wIAAA==";

    /** Posts {@code prefix + token} with the token tagged by {@code probe}, then puts the cursor on its label. */
    static void hover(ClientGameTestContext ctx, String prefix, String token, String probe) {
        ctx.runOnClient(mc -> mc.gui.hud.getChat().addServerSystemMessage(
            Component.literal(prefix).append(Component.literal(token).setStyle(Style.EMPTY.withInsertion(probe)))));
        ctx.waitTick();
        hoverChatInsertion(ctx, probe);
    }

    static ImageCache.Entry awaitLoaded(ClientGameTestContext ctx, String token) {
        String key = TokenCodec.decode(token).orElseThrow().key();
        try {
            ctx.waitFor(mc -> { ImageCache.Entry e = ImgPrevClient.CACHE.peek(key); return e != null && e.state() != ImageCache.State.LOADING; }, 600);
        } catch (AssertionError timeout) {
            ctx.takeScreenshot("timeout-" + key.charAt(0));
            ImageCache.Entry e = cacheEntry(ctx, token);
            throw new AssertionError("preview " + key + " never finished: " + (e == null ? "no cache entry (hover missed?)" : e.state()), timeout);
        }
        return ctx.computeOnClient(mc -> ImgPrevClient.CACHE.peek(key));
    }

    static ImageCache.@Nullable Entry cacheEntry(ClientGameTestContext ctx, String token) {
        String key = TokenCodec.decode(token).orElseThrow().key();
        return ctx.computeOnClient(mc -> ImgPrevClient.CACHE.peek(key));
    }
}
