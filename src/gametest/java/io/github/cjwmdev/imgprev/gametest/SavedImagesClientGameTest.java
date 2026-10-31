package io.github.cjwmdev.imgprev.gametest;

import static io.github.cjwmdev.imgprev.gametest.GameTests.assertEquals;
import static io.github.cjwmdev.imgprev.gametest.GameTests.assertTrue;
import static io.github.cjwmdev.imgprev.gametest.GameTests.chatInput;
import static io.github.cjwmdev.imgprev.gametest.GameTests.clipboard;
import static io.github.cjwmdev.imgprev.gametest.GameTests.lastChat;
import static io.github.cjwmdev.imgprev.gametest.GameTests.setClipboard;
import static io.github.cjwmdev.imgprev.gametest.GameTests.typeInChat;

import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.saved.SavedImages;
import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

/** /img save, list, send and remove, the instant-send setting, and :name: shortcuts. Needs no network. */
public final class SavedImagesClientGameTest implements FabricClientGameTest {
    static final String GIPHY = "https://media.giphy.com/media/XJtM2nNFCzT3etvzOB/giphy.gif";
    static final String IMGUR = "https://i.imgur.com/AbC12xY.png";

    @Override public void runTest(ClientGameTestContext ctx) {
        String original = clipboard(ctx);   // the test uses the OS clipboard; put text back afterwards
        String gif = TokenCodec.encode(GIPHY).orElseThrow();
        try (TestSingleplayerContext sp = ctx.worldBuilder().create(); GameTests.Restore config = GameTests.keepConfig(ctx);
             GameTests.Restore saved = keepSaved(ctx)) {
            sp.getConnection().waitForChunksRender();
            ctx.runOnClient(mc -> List.copyOf(ImgPrevClient.SAVED.all().keySet()).forEach(ImgPrevClient.SAVED::remove));

            typeInChat(ctx, "/img");
            assertEquals("No saved images yet. Save one with /img save <name> <link>.", lastChat(ctx), "empty list");
            typeInChat(ctx, "/img save list " + GIPHY);
            assertEquals("\"list\" is part of the /img command, so pick another name.", lastChat(ctx), "reserved name");

            // A typed link, then the clipboard's; names are stored in lower case.
            typeInChat(ctx, "/img save FunnyCat " + GIPHY);
            assertEquals("Saved funnycat [▶ GIF]. Send it with /img funnycat.", lastChat(ctx), "save feedback");
            setClipboard(ctx, IMGUR);
            typeInChat(ctx, "/img save dog");
            assertEquals("Saved dog [⧈ Image]. Send it with /img dog.", lastChat(ctx), "save from clipboard");
            assertTrue(fileText().contains("\"funnycat\"") && fileText().contains(IMGUR), "written to " + SavedImages.path().getFileName());

            // The list shows labels to hover; clicking one puts its token in the chat box.
            typeInChat(ctx, "/img list");
            assertEquals("Saved images (2), hover to preview, click to use: dog [⧈ Image] funnycat [▶ GIF]", lastChat(ctx), "list");
            boolean clickable = ctx.computeOnClient(mc -> GameTests.lastChatLine(mc).visit((style, text) ->
                style.getClickEvent() instanceof ClickEvent.SuggestCommand s && s.command().equals(gif) ? Optional.of(true) : Optional.empty(),
                Style.EMPTY).orElse(false));
            assertTrue(clickable, "funnycat's entry puts its token in the chat box");
            ctx.takeScreenshot("img-list");

            // By default /img <name> fills the chat box, ready to send.
            typeInChat(ctx, "/img FUNNYCAT");
            ctx.waitForScreen(ChatScreen.class);
            assertEquals(gif, chatInput(ctx), "chat input after /img funnycat");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);

            // Instant send: straight to chat (the server echoes it).
            typeInChat(ctx, "/imgprev instantsend on");
            typeInChat(ctx, "/img funnycat");
            ctx.waitFor(mc -> lastChat(mc).startsWith("<Player0>"));
            assertEquals("<Player0> [▶ GIF]", lastChat(ctx), "sent at once");

            // :name: shortcuts work without link auto-convert, in chat and in chat-like commands.
            typeInChat(ctx, "/imgprev shortcuts on");
            typeInChat(ctx, "look :funnycat: and :nope:");
            ctx.waitFor(mc -> lastChat(mc).startsWith("<Player0> look"));
            assertEquals("<Player0> look [▶ GIF] and :nope:", lastChat(ctx), "shortcut in chat");
            typeInChat(ctx, "/msg Player0 :Dog:");
            ctx.waitFor(mc -> lastChat(mc).contains("whisper"));
            assertTrue(lastChat(ctx).endsWith(": [⧈ Image]"), "shortcut in a command: " + lastChat(ctx));
            typeInChat(ctx, "/imgprev shortcuts off");
            typeInChat(ctx, "plain :funnycat:");
            ctx.waitFor(mc -> lastChat(mc).startsWith("<Player0> plain"));
            assertEquals("<Player0> plain :funnycat:", lastChat(ctx), "shortcuts off");

            typeInChat(ctx, "/img remove funnycat");
            assertEquals("Removed funnycat. It was " + GIPHY, lastChat(ctx), "remove feedback");
            typeInChat(ctx, "/img funnycat");
            assertEquals("Nothing is saved as funnycat. /img list shows your saved images.", lastChat(ctx), "removed");
            assertTrue(!fileText().contains("\"funnycat\""), "removed from the file");
        } finally {
            setClipboard(ctx, original);
        }
    }

    static String fileText() {
        try { return Files.readString(SavedImages.path()); } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /** Puts config/imgprev-saved.json back as it was (or deletes it), and reloads it. */
    static GameTests.Restore keepSaved(ClientGameTestContext ctx) {
        Path file = SavedImages.path();
        String before;
        try { before = Files.exists(file) ? Files.readString(file) : null; } catch (IOException e) { throw new UncheckedIOException(e); }
        return () -> ctx.runOnClient(mc -> {
            try {
                if (before == null) Files.deleteIfExists(file);
                else Files.writeString(file, before);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            ImgPrevClient.SAVED.reload();
        });
    }
}
