package dev.example.imgprev.gametest;

import static dev.example.imgprev.gametest.ChatPreviewClientGameTest.awaitLoaded;
import static dev.example.imgprev.gametest.ChatPreviewClientGameTest.hover;
import static dev.example.imgprev.gametest.GameTests.assertEquals;
import static dev.example.imgprev.gametest.GameTests.assertTrue;
import static dev.example.imgprev.gametest.GameTests.chatInput;
import static dev.example.imgprev.gametest.GameTests.clipboard;
import static dev.example.imgprev.gametest.GameTests.lastChat;
import static dev.example.imgprev.gametest.GameTests.setClipboard;
import static dev.example.imgprev.gametest.GameTests.typeInChat;

import dev.example.imgprev.image.ImageCache;
import dev.example.imgprev.site.LinkConverter;
import dev.example.imgprev.token.TokenCodec;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

/** Sender side: /imglink with a typed link, Klipy and Postimages links, and converting links as messages are sent. */
public final class SendingClientGameTest implements FabricClientGameTest {
    static final String GIPHY = "https://media.giphy.com/media/XJtM2nNFCzT3etvzOB/giphy.gif";
    /** A real animated WebP on Klipy's CDN (mirror host). */
    static final String KLIPY_CDN = "https://static2.klipy.com/ii/35ccce3d852f7995dd2da910f2abd795/2c/59/NiBtvUhr.webp";
    static final String KLIPY_PAGE = "https://klipy.com/gifs/red-moon-flying";
    /** A real Postimages share page; its og:image is i.postimg.cc/8zXVwKmm/01.png. */
    static final String POSTIMAGES_PAGE = "https://postimg.cc/PCDc5McC";

    @Override public void runTest(ClientGameTestContext ctx) {
        String original = clipboard(ctx);   // the test uses the OS clipboard; put text back afterwards
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            setClipboard(ctx, "untouched");

            // A link typed after the command, instead of the clipboard's.
            typeInChat(ctx, "/imglink " + GIPHY);
            assertEquals(TokenCodec.encode(GIPHY).orElseThrow(), clipboard(ctx), "token from typed link");

            // Klipy CDN addresses make keyless tokens that anyone can view: hover loads the animated WebP live.
            typeInChat(ctx, "/imglink " + KLIPY_CDN);
            String klipy = clipboard(ctx);
            assertTrue(klipy.startsWith("<~Y"), "keyless Klipy token: " + klipy);
            ctx.setScreen(() -> new ChatScreen("", false));
            hover(ctx, "klipy: ", klipy, "probe-klipy-cdn");
            ImageCache.Entry klipyEntry = awaitLoaded(ctx, klipy);
            assertEquals(ImageCache.State.READY, klipyEntry.state(), "klipy webp state (error: " + klipyEntry.error() + ")");
            assertTrue(klipyEntry.texture().frameCount() > 1, "klipy webp is animated");
            ctx.waitTicks(2);
            ctx.takeScreenshot("hover-klipy-webp");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);

            // Klipy page links can't be converted; say how to get a usable link.
            typeInChat(ctx, "/imglink " + KLIPY_PAGE);
            assertEquals(LinkConverter.KLIPY_PAGE_LINK, lastChat(ctx), "klipy page link");

            // Postimages page links are looked up live and become tokens for the full-size image.
            typeInChat(ctx, "/imglink " + POSTIMAGES_PAGE);
            ctx.waitFor(mc -> mc.keyboardHandler.getClipboard().startsWith("<~P"));
            String postimages = clipboard(ctx);
            assertEquals(TokenCodec.encode("https://i.postimg.cc/8zXVwKmm/01.png").orElseThrow(), postimages, "page's og:image");
            ctx.setScreen(() -> new ChatScreen("", false));
            hover(ctx, "postimages: ", postimages, "probe-postimages");
            ImageCache.Entry postimagesEntry = awaitLoaded(ctx, postimages);
            assertEquals(ImageCache.State.READY, postimagesEntry.state(), "postimages state (error: " + postimagesEntry.error() + ")");
            ctx.waitTicks(2);
            ctx.takeScreenshot("hover-postimages");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);

            // Converting links as chat is sent (the server echoes what it received).
            typeInChat(ctx, "/imgprev autoconvert on");
            typeInChat(ctx, "look " + GIPHY + "!");
            ctx.waitFor(mc -> lastChat(mc).startsWith("<Player0> look"));
            assertEquals("<Player0> look [🖼 GIF]!", lastChat(ctx), "converted chat");
            ctx.takeScreenshot("autoconvert-chat");

            // Chat-like commands on the list (msg is a default) are converted too.
            typeInChat(ctx, "/msg Player0 see " + GIPHY);
            ctx.waitFor(mc -> lastChat(mc).contains("whisper"));
            assertTrue(lastChat(ctx).endsWith(": see [🖼 GIF]"), "converted command: " + lastChat(ctx));

            // A message that can't be converted isn't sent; it goes back in the chat box.
            typeInChat(ctx, "hey " + KLIPY_PAGE);
            assertEquals("Message not sent: " + LinkConverter.KLIPY_PAGE_LINK, lastChat(ctx), "held back");
            ctx.waitForScreen(ChatScreen.class);
            assertEquals("hey " + KLIPY_PAGE, chatInput(ctx), "text restored for editing");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);

            // Off again: links go out as typed.
            typeInChat(ctx, "/imgprev autoconvert off");
            typeInChat(ctx, "plain " + GIPHY);
            ctx.waitFor(mc -> lastChat(mc).startsWith("<Player0> plain"));
            assertEquals("<Player0> plain " + GIPHY, lastChat(ctx), "unconverted when off");
        } finally {
            setClipboard(ctx, original);
        }
    }
}
