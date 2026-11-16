package io.github.cjwmdev.imgprev.gametest;

import static io.github.cjwmdev.imgprev.gametest.GameTests.assertEquals;
import static io.github.cjwmdev.imgprev.gametest.GameTests.assertTrue;
import static io.github.cjwmdev.imgprev.gametest.GameTests.chatInput;
import static io.github.cjwmdev.imgprev.gametest.GameTests.lastChat;
import static io.github.cjwmdev.imgprev.gametest.GameTests.typeInChat;

import com.sun.net.httpserver.HttpServer;
import io.github.cjwmdev.imgprev.token.TokenCodec;
import io.github.cjwmdev.imgprev.upload.Uploader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

/**
 * A real F2 screenshot, its [Share] button, and /img upload in both modes. Uploads go to a local stand-in for
 * Litterbox and Catbox, so this never publishes anything.
 */
public final class UploadClientGameTest implements FabricClientGameTest {
    static final String LITTERBOX = "https://litter.catbox.moe/toh6ua.png", CATBOX = "https://files.catbox.moe/abc123.png";
    static final String PREFIX = "Saved screenshot as ", SHARE = " [Share]";

    @Override public void runTest(ClientGameTestContext ctx) {
        AtomicInteger status = new AtomicInteger(200);
        AtomicReference<String> reply = new AtomicReference<>(LITTERBOX), sent = new AtomicReference<>("");
        HttpServer server = start(status, reply, sent);
        Uploader.useTestServer(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api.php"));
        try (TestSingleplayerContext sp = ctx.worldBuilder().create(); GameTests.Restore config = GameTests.keepConfig(ctx)) {
            sp.getConnection().waitForChunksRender();

            // F2: the "Saved screenshot as …" line gets a button that types the upload command.
            ctx.getInput().pressKey(GLFW.GLFW_KEY_F2);
            ctx.waitFor(mc -> lastChat(mc).startsWith(PREFIX), 200);
            String line = lastChat(ctx);
            assertTrue(line.endsWith(SHARE), "share button: " + line);
            String file = line.substring(PREFIX.length(), line.length() - SHARE.length());
            Optional<String> command = ctx.computeOnClient(mc -> GameTests.lastChatLine(mc).visit((style, text) ->
                style.getClickEvent() instanceof ClickEvent.SuggestCommand s ? Optional.of(s.command()) : Optional.<String>empty(), Style.EMPTY));
            assertEquals(Optional.of("/img upload " + file), command, "button's command");

            // By default: temporary, 24 hours. The newest screenshot is sent and the token waits in the chat box.
            // (Test screenshots land in the same folder, so none are taken before this.)
            typeInChat(ctx, "/img upload");
            ctx.waitForScreen(ChatScreen.class);
            assertEquals(TokenCodec.encode(LITTERBOX).orElseThrow(), chatInput(ctx), "Litterbox token in the chat box");
            assertEquals("Uploaded " + file + " [⧈ Image], deleted after 24h.", lastChat(ctx), "upload feedback");
            assertTrue(sent.get().contains("name=\"time\"\r\n\r\n24h\r\n"), "lifetime sent");
            assertTrue(sent.get().contains("filename=\"image.png\"") && sent.get().contains("\u0089PNG"), "the screenshot itself, under a neutral name");
            ctx.takeScreenshot("upload-temporary");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);

            // Permanent: Catbox, with no lifetime.
            typeInChat(ctx, "/imgprev uploads permanent");
            assertEquals("Uploads go to Catbox and are kept, and can't be deleted", lastChat(ctx), "mode feedback");
            reply.set(CATBOX);
            typeInChat(ctx, "/img upload " + file);
            ctx.waitForScreen(ChatScreen.class);
            assertEquals(TokenCodec.encode(CATBOX).orElseThrow(), chatInput(ctx), "Catbox token in the chat box");
            assertTrue(!sent.get().contains("name=\"time\""), "no lifetime for Catbox");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);

            typeInChat(ctx, "/imgprev uploads temporary 1h");
            assertEquals("Uploads go to Litterbox and are deleted after 1h", lastChat(ctx), "lifetime feedback");

            // Failures are explained, and nothing outside the screenshots folder can be sent.
            status.set(500);
            reply.set("Internal error");
            typeInChat(ctx, "/img upload");
            ctx.waitFor(mc -> lastChat(mc).startsWith("Upload failed"), 200);
            assertEquals("Upload failed: Litterbox is having problems (HTTP 500). Try again in a few minutes, or upload to Catbox instead, "
                + "which keeps it for good (/imgprev uploads permanent)", lastChat(ctx), "server error");
            typeInChat(ctx, "/img upload ../options.txt");
            assertEquals("There's no image called ../options.txt in your screenshots folder.", lastChat(ctx), "outside the folder");

            // Upload every screenshot: F2 alone uploads it and copies the link; the screenshot line has no [Share].
            status.set(200);
            reply.set(LITTERBOX);
            sent.set("");
            typeInChat(ctx, "/imgprev uploads auto on");
            assertTrue(lastChat(ctx).startsWith("Every screenshot is uploaded to Litterbox (deleted after 1h)"), "auto feedback: " + lastChat(ctx));
            GameTests.setClipboard(ctx, "");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_F2);
            ctx.waitFor(mc -> lastChat(mc).startsWith("Uploaded "), 200);
            assertEquals(LITTERBOX, GameTests.clipboard(ctx), "link copied to the clipboard");
            String auto = lastChat(ctx);
            assertTrue(auto.endsWith(", deleted after 1h. Link copied to the clipboard. [Put in chat]"), "auto feedback: " + auto);
            String autoFile = auto.substring("Uploaded ".length(), auto.indexOf(", deleted"));
            assertEquals(java.util.List.of(auto, "Uploading " + autoFile + " to Litterbox…", PREFIX + autoFile),
                GameTests.recentChat(ctx, 3), "the screenshot line, then uploading, then done; no [Share]");
            Optional<String> putInChat = ctx.computeOnClient(mc -> GameTests.lastChatLine(mc).visit((style, text) ->
                style.getClickEvent() instanceof ClickEvent.SuggestCommand s ? Optional.of(s.command()) : Optional.<String>empty(), Style.EMPTY));
            assertEquals(Optional.of(TokenCodec.encode(LITTERBOX).orElseThrow()), putInChat, "[Put in chat] suggests the token");
            ctx.takeScreenshot("upload-auto");

            // A server sending the same message can't make it upload anything (here, the screenshot just taken).
            sent.set("");
            ctx.runOnClient(mc -> mc.gui.hud.getChat().addServerSystemMessage(
                net.minecraft.network.chat.Component.translatable("screenshot.success", net.minecraft.network.chat.Component.literal(autoFile))));
            ctx.waitTicks(40);
            assertEquals("", sent.get(), "nothing uploaded for a server's message");
            assertEquals(PREFIX + autoFile, lastChat(ctx), "and no upload messages");
            typeInChat(ctx, "/imgprev uploads auto off");
        } finally {
            Uploader.useTestServer(null);
            server.stop(0);
        }
    }

    /** Answers each upload with {@code status} and {@code reply}, keeping the request body (as ISO-8859-1 text) in {@code sent}. */
    static HttpServer start(AtomicInteger status, AtomicReference<String> reply, AtomicReference<String> sent) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api.php", ex -> {
                sent.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
                byte[] out = reply.get().getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(status.get(), out.length);
                ex.getResponseBody().write(out);
                ex.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
