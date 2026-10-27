package dev.example.imgprev.gametest;

import static dev.example.imgprev.gametest.GameTests.assertEquals;
import static dev.example.imgprev.gametest.GameTests.chatInput;
import static dev.example.imgprev.gametest.GameTests.clipboard;
import static dev.example.imgprev.gametest.GameTests.lastChat;
import static dev.example.imgprev.gametest.GameTests.setClipboard;
import static dev.example.imgprev.gametest.GameTests.typeInChat;

import dev.example.imgprev.token.TokenCodec;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

/** /imglink and /imglink chat against the real clipboard, typed through the chat screen. */
public final class ImgLinkClientGameTest implements FabricClientGameTest {
    static final String URL = "https://media4.giphy.com/media/kj8RVuDxh4x7W/giphy.gif";

    @Override public void runTest(ClientGameTestContext ctx) {
        String original = clipboard(ctx);   // the test uses the OS clipboard; put text back afterwards
        String token = TokenCodec.encode(URL).orElseThrow();
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();

            // Unsupported clipboard: error, clipboard untouched.
            setClipboard(ctx, "not a link");
            typeInChat(ctx, "/imglink");
            assertEquals("Clipboard isn't a supported image link.", lastChat(ctx), "error message");
            assertEquals("not a link", clipboard(ctx), "clipboard after error");

            // Supported URL (stray whitespace is stripped): token replaces the clipboard, feedback in chat.
            setClipboard(ctx, "  " + URL + "\n");
            typeInChat(ctx, "/imglink");
            assertEquals(token, clipboard(ctx), "clipboard after /imglink");
            assertEquals("Image token copied: [🖼 GIF]", lastChat(ctx), "feedback message (its token is shown as a label)");
            ctx.takeScreenshot("imglink-copied");

            // /imglink chat: a fresh chat screen pre-filled with the token; clipboard untouched.
            setClipboard(ctx, URL);
            typeInChat(ctx, "/imglink chat");
            ctx.waitForScreen(ChatScreen.class);
            assertEquals(token, chatInput(ctx), "chat input after /imglink chat");
            assertEquals(URL, clipboard(ctx), "clipboard after /imglink chat");
            ctx.takeScreenshot("imglink-chat");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        } finally {
            setClipboard(ctx, original);
        }
    }
}
