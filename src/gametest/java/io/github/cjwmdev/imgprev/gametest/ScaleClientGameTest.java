package io.github.cjwmdev.imgprev.gametest;

import static io.github.cjwmdev.imgprev.gametest.GameTests.GIF;
import static io.github.cjwmdev.imgprev.gametest.GameTests.assertEquals;
import static io.github.cjwmdev.imgprev.gametest.GameTests.assertTrue;
import static io.github.cjwmdev.imgprev.gametest.GameTests.awaitLoaded;
import static io.github.cjwmdev.imgprev.gametest.GameTests.hover;
import static io.github.cjwmdev.imgprev.gametest.GameTests.lastChat;
import static io.github.cjwmdev.imgprev.gametest.GameTests.live;
import static io.github.cjwmdev.imgprev.gametest.GameTests.typeInChat;

import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.config.ConfigScreen;
import io.github.cjwmdev.imgprev.config.ImgPrevConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

/** The preview scale setting: the slider on the settings screen, /imgprev scale, and previews drawn bigger or smaller. */
public final class ScaleClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create(); GameTests.Restore config = GameTests.keepConfig(ctx)) {
            sp.getConnection().waitForChunksRender();

            typeInChat(ctx, "/imgprev scale 150");
            assertEquals("Previews are now drawn at 150%", lastChat(ctx), "scale feedback");
            assertEquals(150, ImgPrevConfig.load().previewScalePercent, "scale saved to disk");
            typeInChat(ctx, "/imgprev scale 100");

            // The settings screen, with the scale slider at a non-default value.
            ctx.runOnClient(mc -> ImgPrevClient.CONFIG.previewScalePercent = 150);
            ctx.setScreen(() -> ConfigScreen.create(null));
            ctx.waitTicks(2);
            ctx.takeScreenshot("settings-scale-150");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            ctx.runOnClient(mc -> ImgPrevClient.CONFIG.previewScalePercent = 100);

            if (live()) {
                ctx.runOnClient(mc -> ImgPrevClient.CONFIG.maxDrawPx = 64);   // small enough that 200% still fits this window
                int[] shown = new int[3];
                int[] scales = {50, 100, 200};
                for (int i = 0; i < scales.length; i++) {
                    int percent = scales[i];
                    ctx.runOnClient(mc -> ImgPrevClient.CONFIG.previewScalePercent = percent);
                    ctx.setScreen(() -> new ChatScreen("", false));
                    hover(ctx, "scale " + percent + ": ", GIF, "probe-scale-" + percent);
                    awaitLoaded(ctx, GIF);
                    ctx.waitTicks(4);   // a bigger box re-decodes the GIF for the new size, then swaps it in
                    shown[i] = ctx.computeOnClient(mc -> ImgPrevClient.CACHE.peek(
                        io.github.cjwmdev.imgprev.token.TokenCodec.decode(GIF).orElseThrow().key()).texture().displayW());
                    ctx.takeScreenshot("scale-" + percent);
                    ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                }
                assertTrue(shown[0] > 0, "loaded: " + shown[0]);
            }
        }
    }
}
