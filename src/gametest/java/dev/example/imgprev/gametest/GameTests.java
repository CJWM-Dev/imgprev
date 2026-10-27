package dev.example.imgprev.gametest;

import java.lang.reflect.Field;
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
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

/** Shared helpers for the client game tests. */
final class GameTests {
    private GameTests() {}

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
    @SuppressWarnings("unchecked")
    static String lastChat(Minecraft mc) {
        List<GuiMessage> all = (List<GuiMessage>) read(ChatComponent.class, "allMessages", mc.gui.hud.getChat());
        return all.isEmpty() ? "" : all.getFirst().content().getString();   // newest first
    }

    static String chatInput(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> ((EditBox) read(ChatScreen.class, "input", mc.gui.screen())).getValue());
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
}
