package io.github.cjwmdev.imgprev.gametest;

import static io.github.cjwmdev.imgprev.gametest.GameTests.assertEquals;
import static io.github.cjwmdev.imgprev.gametest.GameTests.assertTrue;
import static io.github.cjwmdev.imgprev.gametest.GameTests.awaitLoaded;
import static io.github.cjwmdev.imgprev.gametest.GameTests.hover;
import static io.github.cjwmdev.imgprev.gametest.GameTests.system;

import io.github.cjwmdev.imgprev.image.ImageCache;
import io.github.cjwmdev.imgprev.token.ResolvedMedia;
import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Opt-in: chat lines made by something else, such as the Discord chat bridge's guild chat, shown as Hypixel would
 * send them. Set {@code IMGPREV_CHAT_LINES} to a UTF-8 file of lines before {@code ./gradlew runClientGameTest}. Each
 * line is posted, then each token in it is hovered, loaded and screenshotted; every token must decode, and every
 * one that hasn't expired must load.
 */
public final class ChatLinesClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext ctx) {
        String file = System.getenv("IMGPREV_CHAT_LINES");
        if (file == null || file.isBlank()) return;
        List<String> lines;
        try { lines = Files.readAllLines(Path.of(file), StandardCharsets.UTF_8).stream().filter(l -> !l.isBlank()).toList(); }
        catch (IOException e) { throw new AssertionError("reading " + file, e); }

        try (TestSingleplayerContext sp = ctx.worldBuilder().create(); GameTests.Restore config = GameTests.keepConfig(ctx)) {
            sp.getConnection().waitForChunksRender();
            for (String line : lines) system(ctx, line);
            ctx.waitTicks(2);
            ctx.takeScreenshot("chat-lines");

            ctx.setScreen(() -> new ChatScreen("", false));
            int n = 0;
            for (String line : lines) {
                Matcher m = TokenCodec.TOKEN.matcher(line);
                while (m.find()) {
                    String token = m.group();
                    ResolvedMedia media = TokenCodec.decode(token).orElseThrow(() -> new AssertionError("doesn't decode: " + token));
                    String probe = "probe-line-" + n;
                    hover(ctx, line.substring(0, m.start()), token, probe);
                    var expires = media.codec().expiresAtEpochSec(media.payload());
                    if (expires.isPresent() && expires.getAsLong() <= System.currentTimeMillis() / 1000) {
                        ctx.waitTicks(2);   // known from the token alone: never downloaded
                    } else {
                        ImageCache.Entry e = awaitLoaded(ctx, token);
                        System.out.println("[chat lines] " + media.codec().name() + " " + e.state() + (e.error() == null ? "" : " " + e.error())
                            + (e.texture() == null ? "" : " frames=" + e.texture().frameCount()));
                        assertEquals(ImageCache.State.READY, e.state(), media.codec().name() + " state (error: " + e.error() + ")");
                        assertTrue(e.texture().frameCount() >= 1, "frames");
                        ctx.waitTicks(4);
                    }
                    ctx.takeScreenshot("chat-line-" + n++ + "-" + media.codec().name());
                }
            }
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        }
    }
}
