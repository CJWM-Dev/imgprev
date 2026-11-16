package io.github.cjwmdev.imgprev.render;

import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.image.AnimatedTexture;
import io.github.cjwmdev.imgprev.image.ImageCache;
import io.github.cjwmdev.imgprev.mixin.ChatScrollAccess;
import io.github.cjwmdev.imgprev.token.ResolvedMedia;
import java.net.URI;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Full-screen view of one image or GIF, opened by double-clicking its label or its image in chat. Scroll to zoom
 * towards the cursor, drag to move around, double-click to zoom in or back out; GIFs can be paused and stepped
 * through. The image is loaded at up to twice the screen's resolution, so zooming in stays sharp; once one image
 * pixel covers several screen pixels they're drawn crisp rather than blurred, so small details can be inspected.
 */
public final class MediaViewerScreen extends Screen {
    private static final int MARGIN = 8, BAR = 16;
    /** Zoomed in this far (screen pixels per image pixel), pixels are drawn sharp instead of smoothed. */
    private static final double SHARP_FROM = 3;
    /** The first view enlarges small images to fill the screen, but to at most this many times their real size. */
    private static final double MAX_FIT_UPSCALE = 4;
    /** Zooming stops when one image pixel covers this many screen pixels (or at 4× the first view, if that's further). */
    private static final double MAX_PIXEL_SIZE = 32;
    private static final long FLASH_MS = 2000;

    private record Hover(String token, ResolvedMedia media, long at) {}

    private static @Nullable Hover hovered, lastClick;

    private final Screen parent;
    private final String token;
    private final ResolvedMedia media;
    private final int chatScroll;
    private final Viewport view = new Viewport();
    private boolean dragging, paused;
    /** Animation time: {@link Util#getMillis} minus this, or {@link #pausedAt} while paused. */
    private long clockShift, pausedAt;
    /** Zoom at which one image pixel is one screen pixel, from the last frame drawn; double-click zooms there. */
    private double actualSize = 2, maxZoom = 4;
    private @Nullable String flash;
    private long flashUntil;

    MediaViewerScreen(Screen parent, String token, ResolvedMedia media) {
        super(Component.literal(media.codec().siteName() + " " + media.codec().label().text().toLowerCase(java.util.Locale.ROOT)));
        this.parent = parent;
        this.token = token;
        this.media = media;
        this.chatScroll = ((ChatScrollAccess) Minecraft.getInstance().gui.hud.getChat()).imgprev$scrollPos();
    }

    /** Open chat is about to be drawn: whatever was under the cursor last frame no longer counts. */
    public static void chatFrameStarted() { hovered = null; }

    /** Chat calls this while drawing, for the label or image under the cursor. Render thread. */
    public static void noteHovered(String token, ResolvedMedia media) { hovered = new Hover(token, media, Util.getMillis()); }

    /**
     * A left click in open chat. The second click on the same label or image within a double-click's time opens it
     * here; returns true then, so chat doesn't act on the click too.
     */
    public static boolean chatClicked(Screen chat) {
        long now = Util.getMillis();
        Hover h = hovered;   // what was under the cursor in the last frame drawn
        if (h == null) {
            lastClick = null;
            return false;
        }
        Hover first = lastClick;
        if (first != null && first.token().equals(h.token()) && now - first.at() <= MouseHandler.DOUBLE_CLICK_THRESHOLD_MS) {
            lastClick = null;
            Minecraft.getInstance().gui.setScreen(new MediaViewerScreen(chat, h.token(), h.media()));
            return true;
        }
        lastClick = new Hover(h.token(), h.media(), now);
        return false;
    }

    @Override public boolean isPauseScreen() { return false; }

    /** Back to chat, scrolled to where it was. */
    @Override public void onClose() {
        minecraft.gui.setScreen(parent);
        if (chatScroll > 0) minecraft.gui.hud.getChat().scrollChat(chatScroll);
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, 0xF00E0E12);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        int areaH = height - BAR - MARGIN * 2, gs = minecraft.getWindow().getGuiScale();
        String info = media.codec().siteName();

        OptionalLong exp = media.codec().expiresAtEpochSec(media.payload());
        ImageCache.Entry e = exp.isPresent() && exp.getAsLong() < System.currentTimeMillis() / 1000 ? null
            : ImgPrevClient.CACHE.getOrStart(media, width * 2, height * 2, gs, true);
        AnimatedTexture tex = e == null ? null : e.texture();
        if (e == null) {
            g.centeredText(font, media.codec().expiredMessage(), width / 2, MARGIN + areaH / 2 - 4, 0xFFFF5555);
        } else if (e.state() == ImageCache.State.READY && tex != null) {
            int srcW = e.srcW() > 0 ? e.srcW() : tex.textureW(), srcH = e.srcH() > 0 ? e.srcH() : tex.textureH();
            double natW = (double) srcW / gs, natH = (double) srcH / gs;   // GUI units where one image pixel is one screen pixel
            double[] fit = Viewport.fit(natW, natH, width - 2 * MARGIN, areaH, MAX_FIT_UPSCALE);
            actualSize = natW / fit[0];
            maxZoom = Math.max(4, actualSize * MAX_PIXEL_SIZE);
            view.layout(MARGIN, MARGIN, width - 2 * MARGIN, areaH, fit[0], fit[1], maxZoom);
            Viewport.Rect r = view.image();
            double pixel = r.w() * gs / srcW;   // screen pixels per image pixel
            // Drawn as a 1000-unit square, scaled and moved by the pose, so the position isn't rounded to whole GUI units.
            g.pose().pushMatrix();
            g.pose().translate((float) r.x(), (float) r.y());
            g.pose().scale((float) (r.w() / 1000), (float) (r.h() / 1000));
            tex.drawPart(g, 0, 0, 1000, 1000, 0f, 1f, -1, time(), pixel < SHARP_FROM);
            g.pose().popMatrix();
            info += " · " + srcW + "×" + srcH + " · " + Math.round(pixel * 100) + "%";
            if (tex.frameCount() > 1 && paused) info += " · Paused, frame " + (tex.frameAt(pausedAt) + 1) + "/" + tex.frameCount();
        } else if (e.state() == ImageCache.State.FAILED) {
            long retry = ImgPrevClient.CACHE.secondsUntilRetry(e);
            g.centeredText(font, e.error(), width / 2, MARGIN + areaH / 2 - 8, 0xFFFF5555);
            if (retry >= 0) g.centeredText(font, retry > 0 ? "Trying again in " + retry + " s" : "Trying again shortly", width / 2, MARGIN + areaH / 2 + 4, 0xFFAAAAAA);
        } else {
            g.centeredText(font, ImageTooltip.loading(e.attempt() > 1, ImgPrevClient.CACHE.isSlow(e), media.codec().siteName()),
                width / 2, MARGIN + areaH / 2 - 4, 0xFFAAAAAA);
        }
        bar(g, flash != null && Util.getMillis() < flashUntil ? flash : info, tex != null && tex.frameCount() > 1);
    }

    /** The strip along the bottom: what's shown on the left, the controls on the right when there's room. */
    private void bar(GuiGraphicsExtractor g, String left, boolean animated) {
        int y = height - BAR;
        g.fill(0, y, width, height, 0xC0000000);
        g.text(font, left, 6, y + 4, 0xFFFFFFFF);
        String[] hints = {
            "Scroll: zoom · Drag: move · Double-click: zoom · " + (animated ? "Space: pause · , .: frames · " : "") + "C: copy link · O: open · Esc: close",
            "Scroll: zoom · Drag: move · Esc: close",
            "Esc: close"};
        int room = width - 12 - font.width(left) - 16;
        for (String h : hints) {
            if (font.width(h) <= room) {
                g.text(font, h, width - 6 - font.width(h), y + 4, 0xFF9A9AA6);
                return;
            }
        }
    }

    private long time() { return paused ? pausedAt : Util.getMillis() - clockShift; }

    private void togglePause() {
        if (paused) clockShift = Util.getMillis() - pausedAt;
        else pausedAt = time();
        paused = !paused;
    }

    /** Pauses if needed, then shows the next ({@code +1}) or previous ({@code -1}) frame. */
    private void step(int dir) {
        Optional<AnimatedTexture> tex = texture();
        if (tex.isEmpty() || tex.get().frameCount() < 2) return;
        if (!paused) togglePause();
        AnimatedTexture t = tex.get();
        pausedAt = t.frameStartMs(Math.floorMod(t.frameAt(pausedAt) + dir, t.frameCount()));
    }

    private Optional<AnimatedTexture> texture() {
        ImageCache.Entry e = ImgPrevClient.CACHE.peek(media.key());
        return e == null || e.state() != ImageCache.State.READY ? Optional.empty() : Optional.ofNullable(e.texture());
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;
        if (doubleClick) {
            dragging = false;
            if (view.zoomedIn()) view.reset();
            else view.zoomTo(actualSize > 1.5 ? actualSize : 3, event.x(), event.y());   // to real size, or 3× if that's barely bigger
        } else {
            dragging = true;
        }
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) dragging = false;
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() != 0 || !dragging) return false;
        view.pan(dx, dy);
        return true;
    }

    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY != 0) view.zoomAt(Math.pow(Viewport.STEP, scrollY), x, y);
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        double cx = width / 2.0, cy = MARGIN + (height - BAR - MARGIN * 2) / 2.0, move = Math.max(width, height) / 10.0;
        switch (event.key()) {
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> view.zoomAt(Viewport.STEP, cx, cy);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> view.zoomAt(1 / Viewport.STEP, cx, cy);
            case GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_KP_0, GLFW.GLFW_KEY_R -> view.reset();
            case GLFW.GLFW_KEY_LEFT -> view.pan(move, 0);
            case GLFW.GLFW_KEY_RIGHT -> view.pan(-move, 0);
            case GLFW.GLFW_KEY_UP -> view.pan(0, move);
            case GLFW.GLFW_KEY_DOWN -> view.pan(0, -move);
            case GLFW.GLFW_KEY_SPACE -> { if (texture().map(t -> t.frameCount() > 1).orElse(false)) togglePause(); }
            case GLFW.GLFW_KEY_COMMA -> step(-1);
            case GLFW.GLFW_KEY_PERIOD -> step(1);
            case GLFW.GLFW_KEY_C -> link().ifPresent(uri -> {
                minecraft.keyboardHandler.setClipboard(uri.toString());
                flash("Link copied to the clipboard");
            });
            case GLFW.GLFW_KEY_O -> link().ifPresent(uri -> {
                if (!clickUrlAction(minecraft, this, uri)) flash("Opening links is turned off in Chat Settings");
            });
            default -> { return super.keyPressed(event); }
        }
        return true;
    }

    private Optional<URI> link() {
        try {
            return Optional.of(media.codec().mediaUri(media.payload()));
        } catch (RuntimeException e) {
            flash("This image has no link to copy");
            return Optional.empty();
        }
    }

    private void flash(String message) {
        flash = message;
        flashUntil = Util.getMillis() + FLASH_MS;
    }

    /** The token being shown. */
    public String token() { return token; }
}
