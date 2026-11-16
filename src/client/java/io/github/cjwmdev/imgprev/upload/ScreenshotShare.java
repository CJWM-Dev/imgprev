package io.github.cjwmdev.imgprev.upload;

import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.config.ImgPrevConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Adds a {@code [Share]} button to "Saved screenshot as …". Clicking it only types {@code /img upload <file>} into the
 * chat box; pressing Enter is what uploads, so nothing leaves the computer by accident.
 */
public final class ScreenshotShare {
    static final String KEY = "screenshot.success";

    private ScreenshotShare() {}

    public static Component apply(Component in) { return apply(in, ImgPrevClient.CONFIG); }

    /** {@code in} itself unless it's the screenshot message; the hover says where the upload would go. */
    static Component apply(Component in, ImgPrevConfig cfg) {
        if (!(in.getContents() instanceof TranslatableContents t) || !KEY.equals(t.getKey()) || t.getArgs().length < 1) return in;
        String name = t.getArgs()[0] instanceof Component c ? c.getString() : String.valueOf(t.getArgs()[0]);
        if (Uploader.extension(name).isEmpty()) return in;
        Component hover = Component.literal("Upload to " + cfg.uploadMode.site() + " (" + cfg.uploadMode.lifetimeText(cfg.uploadLifetime)
            + ") and put it in the chat box.\nClick, then press Enter.");
        return in.copy().append(Component.literal(" ")).append(Component.literal("[Share]").withStyle(s -> s
            .withColor(ChatFormatting.AQUA)
            .withClickEvent(new ClickEvent.SuggestCommand("/img upload " + name))
            .withHoverEvent(new HoverEvent.ShowText(hover))));
    }
}
