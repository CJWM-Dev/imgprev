package io.github.cjwmdev.imgprev.gametest;

import static io.github.cjwmdev.imgprev.gametest.GameTests.GIF;
import static io.github.cjwmdev.imgprev.gametest.GameTests.IMG;
import static io.github.cjwmdev.imgprev.gametest.GameTests.assertEquals;
import static io.github.cjwmdev.imgprev.gametest.GameTests.assertNull;
import static io.github.cjwmdev.imgprev.gametest.GameTests.assertTrue;
import static io.github.cjwmdev.imgprev.gametest.GameTests.cacheEntry;
import static io.github.cjwmdev.imgprev.gametest.GameTests.hover;
import static io.github.cjwmdev.imgprev.gametest.GameTests.lastChat;
import static io.github.cjwmdev.imgprev.gametest.GameTests.lastChatLine;
import static io.github.cjwmdev.imgprev.gametest.GameTests.system;
import static io.github.cjwmdev.imgprev.gametest.GameTests.typeInChat;

import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.config.ImgPrevConfig;
import io.github.cjwmdev.imgprev.config.ModMenuIntegration;
import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.lwjgl.glfw.GLFW;

/** /imgprev settings take effect, persist to config/imgprev.json, and reload from it. */
public final class SettingsClientGameTest implements FabricClientGameTest {
    /** An old Klipy slug token (ImgPrev 0.1.0 made these from klipy.com page links). */
    static final String OLD_KLIPY = "<~1kred-moon-flyinga~>";

    @Override public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create(); GameTests.Restore config = GameTests.keepConfig(ctx)) {
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
            assertEquals("x [▶ GIF]", lastChat(ctx), "labelled again when on");

            // Per-site switch, persisted to disk.
            typeInChat(ctx, "/imgprev site giphy off");
            assertEquals("giphy previews off", lastChat(ctx), "site feedback");
            system(ctx, GIF + " " + IMG);
            assertEquals(GIF + " [⧈ Image]", lastChat(ctx), "only giphy left as text");
            assertEquals(Boolean.FALSE, ImgPrevConfig.load().sites.get("g"), "giphy off saved to disk");
            typeInChat(ctx, "/imgprev site giphy on");

            // Old Klipy slug tokens needed every viewer's own API key; they're plain text now.
            system(ctx, "old " + OLD_KLIPY);
            assertEquals("old " + OLD_KLIPY, lastChat(ctx), "old klipy token left as text");

            // Shift mode: hovering only hints, and nothing is downloaded.
            typeInChat(ctx, "/imgprev shift on");
            ctx.runOnClient(mc -> ImgPrevClient.CACHE.clear());
            ctx.setScreen(() -> new ChatScreen("", false));
            hover(ctx, "shift: ", IMG, "probe-shift");
            ctx.waitTicks(3);
            assertNull(cacheEntry(ctx, IMG), "no download without Shift");
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
            assertEquals("Reloaded imgprev.json and imgprev-saved.json", lastChat(ctx), "reload feedback");
            assertEquals(64, ctx.computeOnClient(mc -> ImgPrevClient.CONFIG.maxDrawPx), "maxDrawPx after reload");

            typeInChat(ctx, "/imgprev cache clear");
            assertEquals("Preview cache cleared", lastChat(ctx), "cache clear feedback");
            assertEquals(0, ctx.computeOnClient(mc -> ImgPrevClient.CACHE.size()), "cache empty");

            // Mod Menu's settings button opens the Cloth Config screen (both are present in dev runs).
            ctx.setScreen(() -> new ModMenuIntegration().getModConfigScreenFactory().create(null));
            ctx.waitTicks(2);
            assertTrue(ctx.computeOnClient(mc -> mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)), "settings screen open");
            ctx.takeScreenshot("settings-screen");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);

            // /imgprev settings opens the same screen straight from chat, after the chat screen has closed.
            typeInChat(ctx, "/imgprev settings");
            ctx.waitTicks(2);
            assertTrue(ctx.computeOnClient(mc -> mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)), "settings screen from command");
            ctx.takeScreenshot("settings-from-command");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);

            // Label colours, one per kind: new labels take them, and they're saved.
            system(ctx, "c " + IMG);
            assertEquals(TextColor.GREEN, labelColour(ctx), "image labels default to green");
            typeInChat(ctx, "/imgprev colour gif gold");
            assertEquals("GIF labels are now [▶ Gold]", lastChat(ctx), "colour feedback");
            system(ctx, "c " + GIF);
            assertEquals(TextColor.GOLD, labelColour(ctx), "gold GIF label");
            system(ctx, "c " + IMG);
            assertEquals(TextColor.GREEN, labelColour(ctx), "image label unchanged");
            assertEquals("gold", ImgPrevConfig.load().labelColors.get("GIF"), "colour saved to disk");
            typeInChat(ctx, "/imgprev colour image #123456");
            system(ctx, "c " + IMG);
            assertEquals(0x123456, labelColour(ctx).getValue(), "hex label");
            typeInChat(ctx, "/imgprev colour image orange");
            assertEquals("Image labels are now [⧈ Orange]", lastChat(ctx), "preset name");
            typeInChat(ctx, "/imgprev colour gif rainbow");
            assertTrue(lastChat(ctx).startsWith("Use a chat colour"), "bad colour refused: " + lastChat(ctx));
            typeInChat(ctx, "/imgprev colour reset");
            assertTrue(lastChat(ctx).startsWith("Label colours reset: [▶ GIF] [⧈ Image]"), "reset: " + lastChat(ctx));
            assertEquals("aqua", ImgPrevConfig.load().labelColors.get("GIF"), "reset saved");
        }
    }

    /** Colour of the last segment of the newest chat line, where a label sits after {@link #system}. */
    static TextColor labelColour(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> {
            TextColor[] last = {null};
            lastChatLine(mc).visit((style, text) -> { last[0] = style.getColor(); return Optional.empty(); }, Style.EMPTY);
            return last[0];
        });
    }

}
