package dev.example.imgprev.config;

import dev.example.imgprev.ImgPrevClient;
import dev.example.imgprev.site.SiteCodec;
import dev.example.imgprev.site.SiteRegistry;
import java.util.ArrayList;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Settings screen built with Cloth Config, opened from Mod Menu. Only loaded when Cloth Config is installed. */
public final class ConfigScreen {
    private ConfigScreen() {}

    public static Screen create(Screen parent) {
        ImgPrevConfig cfg = ImgPrevClient.CONFIG, defaults = new ImgPrevConfig();
        ConfigBuilder builder = ConfigBuilder.create().setParentScreen(parent).setTitle(text("ImgPrev"));
        ConfigEntryBuilder e = builder.entryBuilder();

        ConfigCategory chat = builder.getOrCreateCategory(text("Chat"));
        chat.addEntry(e.startBooleanToggle(text("Show image previews"), cfg.renderEnabled).setDefaultValue(defaults.renderEnabled)
            .setTooltip(text("Show tokens in chat as labels; hover one to see the image"))
            .setSaveConsumer(v -> cfg.renderEnabled = v).build());
        chat.addEntry(e.startBooleanToggle(text("Only preview while holding Shift"), cfg.requireShift).setDefaultValue(defaults.requireShift)
            .setSaveConsumer(v -> cfg.requireShift = v).build());
        chat.addEntry(e.startBooleanToggle(text("Convert links when sending"), cfg.autoConvertLinks).setDefaultValue(defaults.autoConvertLinks)
            .setTooltip(text("Image links in messages you send become tokens."), text("Players without ImgPrev see the token instead of the link."))
            .setSaveConsumer(v -> cfg.autoConvertLinks = v).build());
        chat.addEntry(e.startStrList(text("Also convert in these commands"), new ArrayList<>(cfg.autoConvertCommands))
            .setDefaultValue(defaults.autoConvertCommands)
            .setTooltip(text("Commands whose text is chat, without the slash, e.g. pc for Hypixel party chat"))
            .setSaveConsumer(v -> cfg.autoConvertCommands = new ArrayList<>(v)).build());

        ConfigCategory sites = builder.getOrCreateCategory(text("Sites"));
        for (SiteCodec c : SiteRegistry.all()) {
            sites.addEntry(e.startBooleanToggle(text(siteTitle(c)), cfg.siteEnabled(c.code())).setDefaultValue(defaults.siteEnabled(c.code()))
                .setSaveConsumer(v -> cfg.sites.put(String.valueOf(c.code()), v)).build());
        }

        ConfigCategory limits = builder.getOrCreateCategory(text("Limits"));
        limits.addEntry(e.startIntSlider(text("Preview size (px)"), cfg.maxDrawPx, 32, 512).setDefaultValue(defaults.maxDrawPx)
            .setTooltip(text("Images are scaled down so their longest side fits this many GUI pixels."), text("They're still drawn at your screen's full resolution."))
            .setSaveConsumer(v -> cfg.maxDrawPx = v).build());
        limits.addEntry(e.startIntField(text("Image memory (MB)"), cfg.cacheBudgetMB).setMin(16).setMax(2048).setDefaultValue(defaults.cacheBudgetMB)
            .setTooltip(text("Older previews are dropped when this fills up"))
            .setSaveConsumer(v -> cfg.cacheBudgetMB = v).build());
        limits.addEntry(e.startIntSlider(text("Largest download (MB)"), cfg.maxDownloadMB, 1, ImgPrevConfig.MAX_DOWNLOAD_MB)
            .setDefaultValue(defaults.maxDownloadMB)
            .setTooltip(text("Bigger files show \"File too large\" instead of a preview"))
            .setSaveConsumer(v -> cfg.maxDownloadMB = v).build());

        builder.setSavingRunnable(() -> {
            cfg.sanitize();
            cfg.save();
            ImgPrevClient.CACHE.clear();   // size limits may have changed
        });
        return builder.build();
    }

    static String siteTitle(SiteCodec c) {
        return switch (c.code()) {
            case 'g' -> "Giphy";
            case 'i' -> "Imgur";
            case 't' -> "Tenor";
            case 'e' -> "Discord emoji";
            case 'd' -> "Discord attachments";
            case 'z' -> "Gyazo";
            case 'y' -> "Klipy";
            case 'p' -> "Postimages";
            case 'b' -> "ImgBB";
            case 'c' -> "Catbox";
            default -> c.name();
        };
    }

    private static Component text(String s) { return Component.literal(s); }
}
