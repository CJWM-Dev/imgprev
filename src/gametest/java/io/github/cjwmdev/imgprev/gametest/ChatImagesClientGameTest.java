package io.github.cjwmdev.imgprev.gametest;

import static io.github.cjwmdev.imgprev.gametest.GameTests.GIF;
import static io.github.cjwmdev.imgprev.gametest.GameTests.IMG;
import static io.github.cjwmdev.imgprev.gametest.GameTests.assertEquals;
import static io.github.cjwmdev.imgprev.gametest.GameTests.assertTrue;
import static io.github.cjwmdev.imgprev.gametest.GameTests.awaitLoaded;
import static io.github.cjwmdev.imgprev.gametest.GameTests.lastChat;
import static io.github.cjwmdev.imgprev.gametest.GameTests.live;
import static io.github.cjwmdev.imgprev.gametest.GameTests.system;
import static io.github.cjwmdev.imgprev.gametest.GameTests.typeInChat;

import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.config.ImgPrevConfig;
import io.github.cjwmdev.imgprev.image.ImageCache;
import io.github.cjwmdev.imgprev.render.ChatImages;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.lwjgl.glfw.GLFW;

/** "Show images in chat": blank rows reserved under a message, the image drawn across them, the chat moving up around it. */
public final class ChatImagesClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create(); GameTests.Restore config = GameTests.keepConfig(ctx)) {
            sp.getConnection().waitForChunksRender();
            ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));

            system(ctx, "off: " + IMG);
            assertEquals(0L, imageRows(ctx), "off by default: just the label");

            typeInChat(ctx, "/imgprev chatimages on");
            assertTrue(lastChat(ctx).startsWith("Images show in chat, 5 lines tall"), "on feedback: " + lastChat(ctx));
            assertTrue(ImgPrevConfig.load().imagesInChat, "saved to disk");
            assertEquals(5L, imageRows(ctx), "chat laid out again: the message already there gets its rows");

            typeInChat(ctx, "/imgprev chatimages 3");
            assertEquals(3, ImgPrevConfig.load().chatImageLines, "height saved");
            assertEquals(3L, imageRows(ctx), "rows follow the height setting");

            ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
            system(ctx, "two: " + GIF + " and " + IMG);
            system(ctx, "after the images");
            ctx.waitTick();
            // Newest line first: the later message, then 6 image rows (the last ends the entry), then the first message's text.
            List<GuiMessage.Line> lines = ctx.computeOnClient(ChatImagesClientGameTest::lines);
            assertEquals("after the images", text(lines.get(0)), "later message below the images");
            for (int i = 1; i <= 6; i++) assertTrue(lines.get(i).content() instanceof ChatImages.Row, "row " + i + " is an image row");
            assertTrue(lines.get(1).endOfEntry(), "the bottom image row ends the message");
            assertTrue(!lines.get(7).endOfEntry() && text(lines.get(7)).startsWith("two: "), "the message's text sits above its images");

            if (live()) {
                awaitLoaded(ctx, GIF);   // the HUD chat drawing the rows starts the downloads, no hover needed
                assertEquals(ImageCache.State.READY, GameTests.cacheEntry(ctx, GIF).state(), "GIF loaded from the chat rows");
                awaitLoaded(ctx, IMG);
                ctx.waitTicks(4);
                ctx.takeScreenshot("chat-images-hud");

                ctx.setScreen(() -> new ChatScreen("", false));
                ctx.waitTicks(2);
                ctx.takeScreenshot("chat-images-open");
                // Hover the top image (the GIF's top row): the full-size preview tooltip appears over it.
                double[] at = ctx.computeOnClient(mc -> {
                    int h = mc.getWindow().getGuiScaledHeight();
                    // Lines are 9 tall up from chatBottom (h - 40): the later message, the image's 3 rows, then the GIF's 3; its top row is the 7th.
                    double gy = h - 40 - 7 * 9 + 3;
                    return new double[] {8.5 * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(),
                        gy * mc.getWindow().getScreenHeight() / h};
                });
                ctx.getInput().setCursorPos(at[0], at[1]);
                ctx.waitTicks(4);
                ctx.takeScreenshot("chat-images-hover");
                ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            }

            typeInChat(ctx, "/imgprev chatimages off");
            assertEquals(0L, imageRows(ctx), "off again: the rows go");
            assertTrue(!ImgPrevConfig.load().imagesInChat, "off saved");
            ctx.runOnClient(mc -> ImgPrevClient.CONFIG.imagesInChat = false);
        }
    }

    @SuppressWarnings("unchecked")
    static List<GuiMessage.Line> lines(Minecraft mc) {
        return List.copyOf((List<GuiMessage.Line>) GameTests.read(ChatComponent.class, "trimmedMessages", mc.gui.hud.getChat()));
    }

    static long imageRows(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> lines(mc).stream().filter(l -> l.content() instanceof ChatImages.Row).count());
    }

    static String text(GuiMessage.Line line) {
        StringBuilder sb = new StringBuilder();
        line.content().accept((i, style, cp) -> { sb.appendCodePoint(cp); return true; });
        return sb.toString();
    }
}
