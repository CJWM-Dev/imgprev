package io.github.cjwmdev.imgprev.gametest;

import static io.github.cjwmdev.imgprev.gametest.GameTests.GIF;
import static io.github.cjwmdev.imgprev.gametest.GameTests.IMG;
import static io.github.cjwmdev.imgprev.gametest.GameTests.assertEquals;
import static io.github.cjwmdev.imgprev.gametest.GameTests.assertTrue;
import static io.github.cjwmdev.imgprev.gametest.GameTests.chatInput;
import static io.github.cjwmdev.imgprev.gametest.GameTests.hover;
import static io.github.cjwmdev.imgprev.gametest.GameTests.live;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.image.ImageCache;
import io.github.cjwmdev.imgprev.render.MediaViewerScreen;
import io.github.cjwmdev.imgprev.token.TokenCodec;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

/** Double-clicking a label or an image in chat opens it full screen, where it can be zoomed, dragged and paused. */
public final class ViewerClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create(); GameTests.Restore config = GameTests.keepConfig(ctx)) {
            sp.getConnection().waitForChunksRender();
            ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));

            // One click on a label does nothing; a double-click opens the viewer, and Esc goes back to chat as it was.
            ctx.setScreen(() -> new ChatScreen("", false));
            ctx.getInput().typeChars("half-typed");
            hover(ctx, "viewer: ", IMG, "probe-viewer");
            ChatScreen chat = (ChatScreen) ctx.computeOnClient(mc -> mc.gui.screen());
            ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(10);   // well past a double-click
            assertTrue(screen(ctx) == chat, "one click leaves chat open");
            doubleClick(ctx);
            assertTrue(screen(ctx) instanceof MediaViewerScreen, "double-click opens the viewer: " + screen(ctx));
            assertEquals(IMG, ((MediaViewerScreen) screen(ctx)).token(), "the label's image");
            ctx.waitTicks(2);
            ctx.takeScreenshot("viewer-opened");
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTick();
            assertTrue(screen(ctx) == chat, "Esc returns to the same chat");
            assertEquals("half-typed", chatInput(ctx), "what was typed is still there");

            // Double-clicking blank chat does nothing.
            ctx.getInput().setCursorPos(5, 5);
            ctx.waitTick();
            doubleClick(ctx);
            assertTrue(screen(ctx) == chat, "double-click away from images");
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);

            if (live()) {
                ctx.setScreen(() -> new ChatScreen("", false));
                hover(ctx, "gif: ", GIF, "probe-viewer-gif");
                doubleClick(ctx);
                assertTrue(screen(ctx) instanceof MediaViewerScreen, "viewer for the GIF");
                String key = TokenCodec.decode(GIF).orElseThrow().key();
                ctx.waitFor(mc -> { ImageCache.Entry e = ImgPrevClient.CACHE.peek(key); return e != null && e.state() == ImageCache.State.READY; }, 600);
                ctx.waitTicks(10);   // the full-size copy replaces the small hover one
                ctx.takeScreenshot("viewer-fit");

                centre(ctx);
                ctx.getInput().scroll(4);
                ctx.waitTicks(2);
                double zoomed = zoom(ctx);
                assertTrue(zoomed > 2, "scrolling zooms in: " + zoomed);
                ctx.takeScreenshot("viewer-zoomed");

                double xBefore = imageX(ctx);
                ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
                ctx.getInput().moveCursor(-150, -80);
                ctx.waitTick();
                ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
                ctx.waitTicks(2);
                assertTrue(imageX(ctx) < xBefore, "dragging left moves the image left");
                ctx.takeScreenshot("viewer-dragged");

                ctx.getInput().pressKey(InputConstants.KEY_SPACE);
                ctx.getInput().pressKey(InputConstants.KEY_PERIOD);
                ctx.waitTicks(2);
                assertTrue((boolean) GameTests.read(MediaViewerScreen.class, "paused", screen(ctx)), "Space pauses");
                ctx.takeScreenshot("viewer-paused");

                ctx.waitTicks(10);
                doubleClick(ctx);
                assertEquals(1.0, zoom(ctx), "double-click when zoomed in goes back to the whole image");
                ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
                ctx.waitTick();
                assertTrue(screen(ctx) instanceof ChatScreen, "back in chat");

                // An image shown in chat opens the same way.
                ctx.runOnClient(mc -> {
                    ImgPrevClient.CONFIG.imagesInChat = true;
                    ImgPrevClient.CONFIG.chatImageLines = 3;
                    mc.gui.hud.getChat().clearMessages(false);
                    mc.gui.hud.getChat().addServerSystemMessage(net.minecraft.network.chat.Component.literal(GIF));
                });
                ctx.waitTicks(4);
                double[] at = ctx.computeOnClient(mc -> {   // the image's 3 rows sit right above the bottom of chat
                    int h = mc.getWindow().getGuiScaledHeight();
                    return new double[] {8.5 * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(),
                        (h - 40 - 2 * 9 + 3) * (double) mc.getWindow().getScreenHeight() / h};
                });
                ctx.getInput().setCursorPos(at[0], at[1]);
                ctx.waitTicks(2);
                doubleClick(ctx);
                assertTrue(screen(ctx) instanceof MediaViewerScreen, "double-clicking an image in chat opens it: " + screen(ctx));
                ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
                ctx.runOnClient(mc -> ImgPrevClient.CONFIG.imagesInChat = false);
            }
        }
    }

    static Screen screen(ClientGameTestContext ctx) { return ctx.computeOnClient(mc -> mc.gui.screen()); }

    static void doubleClick(ClientGameTestContext ctx) {
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTick();
    }

    static void centre(ClientGameTestContext ctx) {
        double[] c = ctx.computeOnClient(mc -> new double[] {mc.getWindow().getScreenWidth() / 2.0, mc.getWindow().getScreenHeight() / 2.0});
        ctx.getInput().setCursorPos(c[0], c[1]);
        ctx.waitTick();
    }

    static Object viewport(ClientGameTestContext ctx) { return GameTests.read(MediaViewerScreen.class, "view", screen(ctx)); }

    static double zoom(ClientGameTestContext ctx) {
        Object v = viewport(ctx);
        return (double) GameTests.read(v.getClass(), "zoom", v);
    }

    static double imageX(ClientGameTestContext ctx) {
        Object v = viewport(ctx);
        double ax = (double) GameTests.read(v.getClass(), "ax", v), aw = (double) GameTests.read(v.getClass(), "aw", v);
        double fitW = (double) GameTests.read(v.getClass(), "fitW", v), cu = (double) GameTests.read(v.getClass(), "cu", v);
        return ax + aw / 2 - cu * fitW * zoom(ctx);
    }
}
