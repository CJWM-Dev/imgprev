package io.github.cjwmdev.imgprev.gametest;

import static io.github.cjwmdev.imgprev.gametest.GameTests.awaitLoaded;
import static io.github.cjwmdev.imgprev.gametest.GameTests.hoverChatInsertion;
import static io.github.cjwmdev.imgprev.gametest.GameTests.typeInChat;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.config.ConfigScreen;
import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Marketing screenshots, not a test: a 1080p window and tidy, Hypixel-style chat for every feature. Only runs with
 * {@code -Dimgprev.gametest.showcase=true}; each scene is independent, so a dead image site only costs its own shot.
 */
public final class ShowcaseClientGameTest implements FabricClientGameTest {
    private record Scene(String name, String rank, ChatFormatting rankColour, String who, String text, String url, int percent) {}

    private static final String EXPIRED = "https://cdn.discordapp.com/attachments/1100000000000000001/1300000000000000002/cat.png"
        + "?ex=66b847f9&is=66b6f679&hm=" + "ab".repeat(32) + "&";

    private static final List<Scene> HOVERS = List.of(
        new Scene("giphy", "[MVP+]", ChatFormatting.AQUA, "Alex", "when the build finally passes: ", "https://media.giphy.com/media/hvRJCLFzcasrR4ia7z/giphy.gif", 100),
        new Scene("tenor", "[VIP]", ChatFormatting.GREEN, "Steve", "me hacking the mainframe ", "https://media.tenor.com/bLXVJaboy9gAAAAM/hacking-hacker.gif", 100),
        new Scene("sticker", "[MVP+]", ChatFormatting.AQUA, "Alex", "the guild bot relayed this sticker ", "https://media.discordapp.net/stickers/781291131828699156.png?size=160", 100),
        new Scene("emoji", "[VIP+]", ChatFormatting.GREEN, "Notch", "new server emote ", "https://cdn.discordapp.com/emojis/1000632669622767686.gif", 100),
        new Scene("catbox", "[MVP++]", ChatFormatting.GOLD, "Herobrine", "look at my base ", "https://files.catbox.moe/k5x3as.gif", 100),
        new Scene("postimages", "[VIP]", ChatFormatting.GREEN, "Steve", "the new UI suggestion ", "https://i.postimg.cc/1XSKBRh8/suggestions.gif", 100),
        new Scene("klipy", "[MVP+]", ChatFormatting.AQUA, "Alex", "this one is great ", "https://static.klipy.com/ii/935d7ab9d8c6202580a668421940ec81/14/af/um0L4dFH.gif", 100),
        new Scene("imgur", "[VIP]", ChatFormatting.GREEN, "Steve", "screenshot of my build ", "https://i.imgur.com/BZXZg3j.png", 100),
        new Scene("missing", "[MVP+]", ChatFormatting.AQUA, "Alex", "this link is dead ", "https://media.tenor.com/ZZZZZnotreal0AAAAC/x.gif", 100),
        new Scene("expired", "[VIP]", ChatFormatting.GREEN, "Steve", "old discord attachment ", EXPIRED, 100));

    @Override public void runTest(ClientGameTestContext ctx) {
        if (!Boolean.getBoolean("imgprev.gametest.showcase")) return;
        try (TestSingleplayerContext sp = ctx.worldBuilder().create(); GameTests.Restore config = GameTests.keepConfig(ctx)) {
            sp.getConnection().waitForChunksRender();
            ctx.getInput().resizeWindow(1920, 1080);
            gui(ctx, 3);
            ctx.runOnClient(mc -> {
                ImgPrevClient.CONFIG.maxDrawPx = 256;
                mc.options.chatOpacity().set(1.0);
                mc.options.textBackgroundOpacity().set(0.5);
            });

            scene("chat-labels", () -> {
                reset(ctx);
                banter(ctx);
                chat(ctx, "[MVP+]", ChatFormatting.AQUA, "Alex", "gif of the day " + token("https://media.giphy.com/media/hvRJCLFzcasrR4ia7z/giphy.gif"));
                chat(ctx, "[VIP]", ChatFormatting.GREEN, "Steve", "my base " + token("https://i.imgur.com/BZXZg3j.png"));
                chat(ctx, "[VIP+]", ChatFormatting.GREEN, "Notch", "new emote " + token("https://cdn.discordapp.com/emojis/1000632669622767686.gif"));
                guild(ctx, "BridgeBot [Staff]", "Steve » " + token("https://media.discordapp.net/stickers/781291131828699156.png?size=160"));
                guild(ctx, "BridgeBot [Staff]", "Alex » " + token(EXPIRED));
                ctx.setScreen(() -> new ChatScreen("", false));
                ctx.waitTicks(3);
                ctx.takeScreenshot("showcase-01-chat-labels");
                ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
                ctx.waitTicks(2);
                ctx.takeScreenshot("showcase-01b-chat-hud");
            });

            scene("without-mod", () -> {
                ctx.runOnClient(mc -> ImgPrevClient.CONFIG.renderEnabled = false);
                reset(ctx);
                banter(ctx);
                chat(ctx, "[MVP+]", ChatFormatting.AQUA, "Alex", "gif of the day " + token("https://media.giphy.com/media/hvRJCLFzcasrR4ia7z/giphy.gif"));
                chat(ctx, "[VIP]", ChatFormatting.GREEN, "Steve", "my base " + token("https://i.imgur.com/BZXZg3j.png"));
                ctx.setScreen(() -> new ChatScreen("", false));
                ctx.waitTicks(3);
                ctx.takeScreenshot("showcase-02-without-imgprev");
                ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
                ctx.runOnClient(mc -> ImgPrevClient.CONFIG.renderEnabled = true);
            });

            for (Scene s : HOVERS) {
                scene("hover-" + s.name, () -> {
                    ctx.runOnClient(mc -> { ImgPrevClient.CONFIG.previewScalePercent = s.percent; ImgPrevClient.CACHE.clear(); });
                    reset(ctx);
                    banter(ctx);
                    ctx.setScreen(() -> new ChatScreen("", false));
                    hoverLine(ctx, s);
                    if (!s.name.equals("expired")) {
                        try { awaitLoaded(ctx, TokenCodec.encode(s.url).orElseThrow()); } catch (AssertionError ignored) { /* shoot whatever state it is in */ }
                    }
                    ctx.waitTicks(4);
                    ctx.takeScreenshot("showcase-10-hover-" + s.name);
                    ctx.waitTicks(9);
                    ctx.takeScreenshot("showcase-10-hover-" + s.name + "-later");
                    ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
                });
            }

            for (int percent : new int[] {50, 100, 200}) {
                scene("scale-" + percent, () -> {
                    ctx.runOnClient(mc -> { ImgPrevClient.CONFIG.previewScalePercent = percent; ImgPrevClient.CONFIG.maxDrawPx = 96; ImgPrevClient.CACHE.clear(); });
                    reset(ctx);
                    banter(ctx);
                    ctx.setScreen(() -> new ChatScreen("", false));
                    Scene s = HOVERS.get(0);
                    hoverLine(ctx, s);
                    awaitLoaded(ctx, TokenCodec.encode(s.url).orElseThrow());
                    ctx.waitTicks(8);
                    ctx.takeScreenshot("showcase-20-scale-" + percent);
                    ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
                });
            }
            ctx.runOnClient(mc -> { ImgPrevClient.CONFIG.previewScalePercent = 100; ImgPrevClient.CONFIG.maxDrawPx = 256; });

            scene("shift", () -> {
                typeInChat(ctx, "/imgprev shift on");
                ctx.runOnClient(mc -> ImgPrevClient.CACHE.clear());
                reset(ctx);
                banter(ctx);
                ctx.setScreen(() -> new ChatScreen("", false));
                hoverLine(ctx, HOVERS.get(7));
                ctx.waitTicks(3);
                ctx.takeScreenshot("showcase-30-shift-hint");
                ctx.getInput().holdShift();
                ctx.waitTicks(3);
                try { awaitLoaded(ctx, TokenCodec.encode(HOVERS.get(7).url).orElseThrow()); } catch (AssertionError ignored) { }
                ctx.waitTicks(4);
                ctx.takeScreenshot("showcase-30-shift-held");
                ctx.getInput().releaseShift();
                ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
                typeInChat(ctx, "/imgprev shift off");
            });

            scene("colours", () -> {
                for (String c : new String[] {"gif gold", "image light_purple", "emoji green", "sticker red", "discord yellow"}) {
                    typeInChat(ctx, "/imgprev colour " + c);
                    ctx.waitTicks(2);
                }
                reset(ctx);
                banter(ctx);
                chat(ctx, "[MVP+]", ChatFormatting.AQUA, "Alex", "gif of the day " + token("https://media.giphy.com/media/hvRJCLFzcasrR4ia7z/giphy.gif"));
                chat(ctx, "[VIP]", ChatFormatting.GREEN, "Steve", "my base " + token("https://i.imgur.com/BZXZg3j.png"));
                chat(ctx, "[VIP+]", ChatFormatting.GREEN, "Notch", "new emote " + token("https://cdn.discordapp.com/emojis/1000632669622767686.gif"));
                guild(ctx, "BridgeBot [Staff]", "Steve » " + token("https://media.discordapp.net/stickers/781291131828699156.png?size=160"));
                guild(ctx, "BridgeBot [Staff]", "Alex » " + token(EXPIRED));
                ctx.setScreen(() -> new ChatScreen("", false));
                ctx.waitTicks(3);
                ctx.takeScreenshot("showcase-40-custom-colours");
                ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
                typeInChat(ctx, "/imgprev colour reset");
            });

            scene("commands", () -> {
                reset(ctx);
                typeInChat(ctx, "/imgprev");
                ctx.setScreen(() -> new ChatScreen("/imgprev ", false));
                ctx.waitTicks(3);
                ctx.takeScreenshot("showcase-50-status-command");
                ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);

                GameTests.setClipboard(ctx, "https://media.giphy.com/media/hvRJCLFzcasrR4ia7z/giphy.gif");
                typeInChat(ctx, "/imglink");
                ctx.setScreen(() -> new ChatScreen("", false));
                ctx.waitTicks(3);
                ctx.takeScreenshot("showcase-51-imglink-copied");
                ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);

                typeInChat(ctx, "/imglink chat");
                ctx.waitForScreen(ChatScreen.class);
                ctx.waitTicks(3);
                ctx.takeScreenshot("showcase-52-imglink-chat");
                ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            });

            scene("autoconvert", () -> {
                typeInChat(ctx, "/imgprev autoconvert on");
                reset(ctx);
                banter(ctx);
                ctx.setScreen(() -> new ChatScreen("", false));
                ctx.getInput().typeChars("https://media.giphy.com/media/hvRJCLFzcasrR4ia7z/giphy.gif");
                ctx.waitTicks(3);
                ctx.takeScreenshot("showcase-53-autoconvert-before");
                ctx.getInput().pressKey(InputConstants.KEY_RETURN);
                ctx.waitTicks(4);
                ctx.setScreen(() -> new ChatScreen("", false));
                ctx.waitTicks(3);
                ctx.takeScreenshot("showcase-53-autoconvert-after");
                ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
                typeInChat(ctx, "/imgprev autoconvert off");
            });

            gui(ctx, 2);
            ctx.runOnClient(mc -> ImgPrevClient.CONFIG.maxDrawPx = 256);
            for (String tab : new String[] {"General", "Colours", "Sending", "Sites", "Memory & downloads"}) {
                scene("settings-" + tab, () -> {
                    ctx.setScreen(() -> ConfigScreen.create(null));
                    ctx.waitTicks(3);
                    if (!tab.equals("General")) clickWidget(ctx, tab);
                    ctx.waitTicks(3);
                    ctx.takeScreenshot("showcase-60-settings-" + tab.split(" ")[0].toLowerCase());
                    ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
                });
            }
        }
    }

    private static void scene(String name, Runnable body) {
        try { body.run(); } catch (Throwable t) { System.err.println("[showcase] scene " + name + " failed: " + t); }
    }

    private static void gui(ClientGameTestContext ctx, int scale) {
        ctx.runOnClient(mc -> mc.options.guiScale().set(scale));
        ctx.getInput().resizeWindow(1920, 1080);
        ctx.waitTicks(3);
    }

    private static void reset(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(true));
        ctx.waitTick();
    }

    private static void banter(ClientGameTestContext ctx) {
        chat(ctx, "[VIP+]", ChatFormatting.GREEN, "Notch", "anyone up for a bedwars game?");
        chat(ctx, "[MVP+]", ChatFormatting.AQUA, "Alex", "yeah, give me two minutes");
    }

    private static String token(String url) { return TokenCodec.encode(url).orElseThrow(); }

    private static MutableComponent rank(String rank, ChatFormatting colour, String who) {
        return Component.literal("").append(Component.literal(rank + " " + who).withStyle(colour)).append(Component.literal(": ").withStyle(ChatFormatting.WHITE));
    }

    private static void chat(ClientGameTestContext ctx, String rank, ChatFormatting colour, String who, String text) {
        ctx.runOnClient(mc -> mc.gui.hud.getChat().addServerSystemMessage(rank(rank, colour, who).append(Component.literal(text).withStyle(ChatFormatting.WHITE))));
    }

    private static void guild(ClientGameTestContext ctx, String who, String text) {
        ctx.runOnClient(mc -> mc.gui.hud.getChat().addServerSystemMessage(
            Component.literal("Guild > ").withStyle(ChatFormatting.DARK_GREEN)
                .append(Component.literal("[MVP+] " + who + ": ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE))));
    }

    /** The scene's line, with its token tagged so the cursor can be put on the label. */
    private static void hoverLine(ClientGameTestContext ctx, Scene s) {
        String probe = "probe-" + s.name;
        ctx.runOnClient(mc -> mc.gui.hud.getChat().addServerSystemMessage(
            rank(s.rank, s.rankColour, s.who).append(Component.literal(s.text).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(token(s.url)).setStyle(Style.EMPTY.withInsertion(probe)))));
        ctx.waitTick();
        hoverChatInsertion(ctx, probe);
    }

    /** Clicks the first button on the open screen whose text is {@code label}. */
    private static void clickWidget(ClientGameTestContext ctx, String label) {
        double[] win = ctx.computeOnClient(mc -> {
            Screen screen = mc.gui.screen();
            for (var child : screen.children()) {
                if (child instanceof AbstractWidget w && w.getMessage().getString().equals(label)) {
                    double sx = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
                    double sy = (double) mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
                    return new double[] {(w.getX() + w.getWidth() / 2.0) * sx, (w.getY() + w.getHeight() / 2.0) * sy};
                }
            }
            return null;
        });
        if (win == null) throw new AssertionError("no widget " + label);
        ctx.getInput().setCursorPos(win[0], win[1]);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }
}
