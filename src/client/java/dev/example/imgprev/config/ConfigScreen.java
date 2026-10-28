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

/**
 * Settings screen built with Cloth Config, opened from Mod Menu or {@code /imgprev settings}. Only loaded when Cloth
 * Config is installed.
 */
public final class ConfigScreen {

    private ConfigScreen() {}

    public static Screen create(Screen parent) {
        ImgPrevConfig cfg = ImgPrevClient.CONFIG, defaults = new ImgPrevConfig();
        ConfigBuilder builder = ConfigBuilder.create().setParentScreen(parent).setTitle(text("ImgPrev"));
        ConfigEntryBuilder e = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(text("General"));
        general.addEntry(e.startBooleanToggle(text("Show image previews"), cfg.renderEnabled).setDefaultValue(defaults.renderEnabled)
            .setTooltip(text("Show tokens in chat as labels; hover one to see the image"))
            .setSaveConsumer(v -> cfg.renderEnabled = v).build());
        general.addEntry(e.startBooleanToggle(text("Only preview while holding Shift"), cfg.requireShift).setDefaultValue(defaults.requireShift)
            .setTooltip(text("Hovering a label shows a hint until you hold Shift, so nothing downloads by accident"))
            .setSaveConsumer(v -> cfg.requireShift = v).build());
        general.addEntry(e.startIntSlider(text("Preview size (px)"), cfg.maxDrawPx, 32, 512).setDefaultValue(defaults.maxDrawPx)
            .setTooltip(text("Images are scaled down so their longest side fits this many GUI pixels."), text("They're still drawn at your screen's full resolution."))
            .setSaveConsumer(v -> cfg.maxDrawPx = v).build());

        // One row per kind of label: a hex box, then a button that opens the preset swatches (see LabelColourEntry).
        ConfigCategory colours = builder.getOrCreateCategory(text("Colours"));
        colours.addEntry(e.startTextDescription(text("Each kind of label has its own colour. Pick a preset with the swatch button, or type "
            + "your own hex colour such as #FF8800; a hex colour takes priority. Labels already in chat keep their colour."))
            .setColor(0xFFAAAAAA).build());
        for (String label : ImgPrevConfig.LABELS) {
            colours.addEntry(new LabelColourEntry(label, cfg.labelColor(label).serialize(), ImgPrevConfig.defaultLabelColor(label).serialize(),
                v -> cfg.labelColors.put(label, v)));
        }

        ConfigCategory sending = builder.getOrCreateCategory(text("Sending"));
        sending.addEntry(e.startBooleanToggle(text("Convert links when sending"), cfg.autoConvertLinks).setDefaultValue(defaults.autoConvertLinks)
            .setTooltip(text("Image links in messages you send become tokens."), text("Players without ImgPrev see the token instead of the link."))
            .setSaveConsumer(v -> cfg.autoConvertLinks = v).build());
        sending.addEntry(e.startStrList(text("Also convert in these commands"), new ArrayList<>(cfg.autoConvertCommands))
            .setDefaultValue(defaults.autoConvertCommands)
            .setTooltip(text("Commands whose text is chat, without the slash, e.g. pc for Hypixel party chat"))
            .setSaveConsumer(v -> cfg.autoConvertCommands = new ArrayList<>(v)).build());

        ConfigCategory sites = builder.getOrCreateCategory(text("Sites"));
        for (SiteCodec c : SiteRegistry.all()) {
            sites.addEntry(e.startBooleanToggle(text(c.title()), cfg.siteEnabled(c.code())).setDefaultValue(defaults.siteEnabled(c.code()))
                .setTooltip(text("Turn off to leave " + c.title() + " tokens as plain text"))
                .setSaveConsumer(v -> cfg.sites.put(String.valueOf(c.code()), v)).build());
        }

        ConfigCategory memory = builder.getOrCreateCategory(text("Memory & downloads"));
        memory.addEntry(e.startIntField(text("Image memory (MB)"), cfg.cacheBudgetMB).setMin(16).setMax(2048).setDefaultValue(defaults.cacheBudgetMB)
            .setTooltip(text("Older previews are dropped when this fills up"))
            .setSaveConsumer(v -> cfg.cacheBudgetMB = v).build());
        memory.addEntry(e.startIntSlider(text("Largest download (MB)"), cfg.maxDownloadMB, 1, ImgPrevConfig.MAX_DOWNLOAD_MB)
            .setDefaultValue(defaults.maxDownloadMB)
            .setTooltip(text("Bigger files show \"File too large\" instead of a preview"))
            .setSaveConsumer(v -> cfg.maxDownloadMB = v).build());

        int oldBudget = cfg.cacheBudgetMB, oldDownload = cfg.maxDownloadMB, oldDraw = cfg.maxDrawPx;
        builder.setSavingRunnable(() -> {
            cfg.sanitize();
            cfg.save();
            // Cached previews were decoded for the old limits.
            if (cfg.cacheBudgetMB != oldBudget || cfg.maxDownloadMB != oldDownload || cfg.maxDrawPx != oldDraw) ImgPrevClient.CACHE.clear();
        });
        return builder.build();
    }

    private static Component text(String s) { return Component.literal(s); }
}
