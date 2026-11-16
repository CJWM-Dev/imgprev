package io.github.cjwmdev.imgprev.upload;

import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.config.ImgPrevConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The "Saved screenshot as …" message. Normally it gets a {@code [Share]} button, which only types
 * {@code /img upload <file>} into the chat box; pressing Enter is what uploads, so nothing leaves the computer by
 * accident. With auto-upload on, the screenshot is uploaded straight away instead and its link copied.
 */
public final class ScreenshotShare {
    static final String KEY = "screenshot.success";
    /** Auto-upload only sends a file written this recently: the message is about a screenshot just taken. */
    static final long FRESH_MS = 60_000;

    private ScreenshotShare() {}

    public static Component apply(Component in) { return apply(in, ImgPrevClient.CONFIG); }

    /** {@code in} itself unless it's the screenshot message; the hover says where the upload would go. */
    static Component apply(Component in, ImgPrevConfig cfg) {
        Optional<String> name = fileName(in);
        if (name.isEmpty() || cfg.autoUploadScreenshots) return in;   // auto-upload posts its own line about it
        Component hover = Component.literal("Upload to " + cfg.uploadMode.site() + " (" + cfg.uploadMode.lifetimeText(cfg.uploadLifetime)
            + ") and put it in the chat box.\nClick, then press Enter.");
        return in.copy().append(Component.literal(" ")).append(button("[Share]", "/img upload " + name.get(), hover));
    }

    /** The image file a "Saved screenshot as …" message names. */
    static Optional<String> fileName(Component in) {
        if (!(in.getContents() instanceof TranslatableContents t) || !KEY.equals(t.getKey()) || t.getArgs().length < 1) return Optional.empty();
        String name = t.getArgs()[0] instanceof Component c ? c.getString() : String.valueOf(t.getArgs()[0]);
        return Uploader.extension(name).isPresent() ? Optional.of(name) : Optional.empty();
    }

    /**
     * With auto-upload on, uploads the screenshot a "Saved screenshot as …" message names, copies its link to the
     * clipboard, and says so in chat, with a button that puts its token in the chat box. Only called for messages the
     * client posts itself, never ones from a server, and only sends a file just written, so a server can't fake the
     * message to have an older screenshot uploaded. Render thread.
     */
    public static void autoUpload(Component message) {
        ImgPrevConfig cfg = ImgPrevClient.CONFIG;
        if (!cfg.autoUploadScreenshots) return;
        Optional<String> name = fileName(message);
        if (name.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Optional<Path> file = Uploader.find(mc.gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR), name.get())
            .filter(p -> fresh(p, System.currentTimeMillis()));
        if (file.isEmpty()) return;

        String n = name.get();
        UploadMode mode = cfg.uploadMode;
        String lifetime = cfg.uploadLifetime, kept = mode.lifetimeText(lifetime);
        // Scheduled, so it comes after the "Saved screenshot" line this was called while adding.
        mc.schedule(() -> chat(mc, Component.literal("Uploading " + n + " to " + mode.site() + "…").withStyle(ChatFormatting.GRAY)));
        Uploader.upload(file.get(), mode, lifetime).whenComplete((uploaded, err) -> mc.execute(() -> {
            if (err != null) {
                chat(mc, Component.literal("Couldn't upload " + n + ": " + Uploader.describe(err, mode) + " ").withStyle(ChatFormatting.RED)
                    .append(button("[Try again]", "/img upload " + n, Component.literal("Click, then press Enter"))));
                return;
            }
            mc.keyboardHandler.setClipboard(uploaded.link());
            chat(mc, Component.literal("Uploaded " + n + ", " + kept + ". Link copied to the clipboard. ")
                .append(button("[Put in chat]", uploaded.token(),
                    Component.literal("Put its token in the chat box, so players with ImgPrev see the image"))));
        }));
    }

    static boolean fresh(Path p, long nowMs) {
        try {
            return nowMs - Files.getLastModifiedTime(p).toMillis() <= FRESH_MS;
        } catch (IOException e) {
            return false;
        }
    }

    private static MutableComponent button(String text, String suggest, Component hover) {
        return Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.AQUA)
            .withClickEvent(new ClickEvent.SuggestCommand(suggest))
            .withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    private static void chat(Minecraft mc, Component c) { mc.gui.hud.getChat().addClientSystemMessage(c); }
}
