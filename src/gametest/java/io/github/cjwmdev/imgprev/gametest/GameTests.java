package io.github.cjwmdev.imgprev.gametest;

import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.config.ImgPrevConfig;
import io.github.cjwmdev.imgprev.image.ImageCache;
import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Shared helpers and tokens for the client game tests. */
final class GameTests {
    private GameTests() {}

    static final String GIF = TokenCodec.encode("https://media.giphy.com/media/XJtM2nNFCzT3etvzOB/giphy.gif").orElseThrow();
    static final String IMG = TokenCodec.encode("https://i.imgur.com/AbC12xY.png").orElseThrow();

    /** False with {@code ./gradlew runClientGameTest -Poffline}: skip the checks that download from the live sites. */
    static boolean live() { return !Boolean.getBoolean("imgprev.gametest.offline"); }

    /** Puts config/imgprev.json, and the settings loaded from it, back as they were; for try-with-resources. */
    interface Restore extends AutoCloseable { @Override void close(); }

    static Restore keepConfig(ClientGameTestContext ctx) {
        Path file = ImgPrevConfig.path();
        String saved;
        try { saved = Files.readString(file); } catch (IOException e) { throw new UncheckedIOException(e); }
        return () -> ctx.runOnClient(mc -> {
            try { Files.writeString(file, saved); } catch (IOException e) { throw new UncheckedIOException(e); }
            ImgPrevClient.CONFIG.reload();
            ImgPrevClient.CACHE.clear();
        });
    }

    /** Opens chat, types {@code text}, presses Enter, and waits until that chat screen has gone. */
    static void typeInChat(ClientGameTestContext ctx, String text) {
        ctx.setScreen(() -> new ChatScreen("", false));
        Screen typedInto = ctx.computeOnClient(mc -> mc.gui.screen());
        ctx.getInput().typeChars(text);
        ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        ctx.waitFor(mc -> mc.gui.screen() != typedInto);
        ctx.waitTick();
    }

    static String clipboard(ClientGameTestContext ctx) { return ctx.computeOnClient(mc -> mc.keyboardHandler.getClipboard()); }
    static void setClipboard(ClientGameTestContext ctx, String s) { ctx.runOnClient(mc -> mc.keyboardHandler.setClipboard(s)); }

    static String lastChat(ClientGameTestContext ctx) { return ctx.computeOnClient(GameTests::lastChat); }

    /** Client-thread variant, usable inside {@code waitFor} predicates. */
    static String lastChat(Minecraft mc) { return lastChatLine(mc).getString(); }

    static Component lastChatLine(Minecraft mc) {
        List<GuiMessage> all = allMessages(mc);
        return all.isEmpty() ? Component.empty() : all.getFirst().content();
    }

    /** The newest {@code n} chat lines as text, newest first. */
    static List<String> recentChat(ClientGameTestContext ctx, int n) {
        return ctx.computeOnClient(mc -> allMessages(mc).stream().limit(n).map(m -> m.content().getString()).toList());
    }

    /** Newest first. */
    @SuppressWarnings("unchecked")
    private static List<GuiMessage> allMessages(Minecraft mc) {
        return (List<GuiMessage>) read(ChatComponent.class, "allMessages", mc.gui.hud.getChat());
    }

    static String chatInput(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> ((EditBox) read(ChatScreen.class, "input", mc.gui.screen())).getValue());
    }

    /** Posts a server system message (how Hypixel sends most chat). */
    static void system(ClientGameTestContext ctx, String text) {
        ctx.runOnClient(mc -> mc.gui.hud.getChat().addServerSystemMessage(Component.literal(text)));
    }

    /** Posts {@code prefix + token} with the token tagged by {@code probe}, then puts the cursor on its label. */
    static void hover(ClientGameTestContext ctx, String prefix, String token, String probe) {
        ctx.runOnClient(mc -> mc.gui.hud.getChat().addServerSystemMessage(
            Component.literal(prefix).append(Component.literal(token).setStyle(Style.EMPTY.withInsertion(probe)))));
        ctx.waitTick();
        hoverChatInsertion(ctx, probe);
    }

    static ImageCache.Entry awaitLoaded(ClientGameTestContext ctx, String token) {
        String key = TokenCodec.decode(token).orElseThrow().key();
        try {
            ctx.waitFor(mc -> { ImageCache.Entry e = ImgPrevClient.CACHE.peek(key); return e != null && e.state() != ImageCache.State.LOADING; }, 600);
        } catch (AssertionError timeout) {
            ctx.takeScreenshot("timeout-" + key.charAt(0));
            ImageCache.Entry e = cacheEntry(ctx, token);
            throw new AssertionError("preview " + key + " never finished: " + (e == null ? "no cache entry (hover missed?)" : e.state()), timeout);
        }
        return ctx.computeOnClient(mc -> ImgPrevClient.CACHE.peek(key));
    }

    static ImageCache.@Nullable Entry cacheEntry(ClientGameTestContext ctx, String token) {
        String key = TokenCodec.decode(token).orElseThrow().key();
        return ctx.computeOnClient(mc -> ImgPrevClient.CACHE.peek(key));
    }

    /**
     * With chat open, finds a GUI point over chat text whose style carries {@code insertion} (labels keep the
     * surrounding style, so tests tag the token's segment) and moves the real cursor there.
     */
    static void hoverChatInsertion(ClientGameTestContext ctx, String insertion) {
        int[] gui = ctx.computeOnClient(mc -> {
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            // Scanning up from the bottom finds the text's bottom-left edge; step up and right into the glyphs.
            for (int y = h - 1; y > h / 3; y--) {
                for (int x = 0; x < w / 2; x++) {
                    if (tagged(mc, x, y, h, insertion) && tagged(mc, x + 3, y - 3, h, insertion)) return new int[] {x + 3, y - 3};
                }
            }
            throw new AssertionError("no chat text tagged " + insertion);
        });
        double[] win = ctx.computeOnClient(mc -> new double[] {
            (gui[0] + 0.5) * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(),
            (gui[1] + 0.5) * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight()});
        ctx.getInput().setCursorPos(win[0], win[1]);
        ctx.waitTick();
    }

    static boolean tagged(Minecraft mc, int x, int y, int screenH, String insertion) {
        ActiveTextCollector.ClickableStyleFinder f = new ActiveTextCollector.ClickableStyleFinder(mc.font, x, y).includeInsertions(true);
        mc.gui.hud.getChat().captureClickableText(f, screenH, mc.gui.hud.getGuiTicks(), ChatComponent.DisplayMode.FOREGROUND);
        Style s = f.result();
        return s != null && insertion.equals(s.getInsertion());
    }

    /** Private-field read; 26.x runs with Mojang names, so the source names work at runtime. */
    static Object read(Class<?> owner, String name, Object target) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(owner.getSimpleName() + "." + name, e);
        }
    }

    static void assertEquals(Object expected, Object actual, String what) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
    }

    static void assertTrue(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }

    static void assertNull(@Nullable Object value, String what) {
        if (value != null) throw new AssertionError(what + ": expected nothing but was <" + value + ">");
    }
}
