package io.github.cjwmdev.imgprev.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.config.ConfigScreen;
import io.github.cjwmdev.imgprev.config.ImgPrevConfig;
import io.github.cjwmdev.imgprev.site.Label;
import io.github.cjwmdev.imgprev.site.SiteCodec;
import io.github.cjwmdev.imgprev.site.SiteRegistry;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

/**
 * {@code /imgprev} settings. Every change is saved to config/imgprev.json. Toggles and the label colour apply to new
 * chat lines and to hovering; labels already in chat stay as they are. {@code /imgprev settings} opens the screen.
 */
public final class ImgPrevCommand {
    private ImgPrevCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> d, CommandBuildContext ctx) {
        LiteralArgumentBuilder<FabricClientCommandSource> site = ClientCommands.literal("site");
        for (SiteCodec c : SiteRegistry.all()) {
            site.then(ClientCommands.literal(c.name())
                .then(editing("on", (src, cfg) -> setSite(src, cfg, c, true)))
                .then(editing("off", (src, cfg) -> setSite(src, cfg, c, false))));
        }
        LiteralCommandNode<FabricClientCommandSource> colour = colour().build();
        d.register(ClientCommands.literal("imgprev")
            .requires(FabricClientCommandSource::attended)
            .executes(c -> status(c.getSource()))
            .then(ClientCommands.literal("settings").executes(c -> openSettings(c.getSource())))
            .then(colour)
            .then(ClientCommands.literal("color").redirect(colour))
            .then(editing("on", (src, cfg) -> { cfg.renderEnabled = true; src.sendFeedback(Component.literal("Image previews on")); }))
            .then(editing("off", (src, cfg) -> { cfg.renderEnabled = false; src.sendFeedback(Component.literal("Image previews off")); }))
            .then(ClientCommands.literal("shift")
                .then(editing("on", (src, cfg) -> { cfg.requireShift = true; src.sendFeedback(Component.literal("Previews need Shift held")); }))
                .then(editing("off", (src, cfg) -> { cfg.requireShift = false; src.sendFeedback(Component.literal("Previews show on hover")); })))
            .then(ClientCommands.literal("autoconvert")
                .then(editing("on", (src, cfg) -> { cfg.autoConvertLinks = true; src.sendFeedback(Component.literal("Image links in messages you send become tokens")); }))
                .then(editing("off", (src, cfg) -> { cfg.autoConvertLinks = false; src.sendFeedback(Component.literal("Links are sent as typed")); })))
            .then(site)
            .then(ClientCommands.literal("cache").then(ClientCommands.literal("clear").executes(c -> {
                ImgPrevClient.CACHE.clear();
                c.getSource().sendFeedback(Component.literal("Preview cache cleared"));
                return 1;
            })))
            .then(ClientCommands.literal("reload").executes(c -> {
                ImgPrevClient.CONFIG.reload();
                ImgPrevClient.CACHE.clear();   // size limits may have changed
                c.getSource().sendFeedback(Component.literal("Reloaded " + ImgPrevConfig.path().getFileName()));
                return 1;
            })));
    }

    /** Typed commands run inside the ChatScreen's key handler, which closes the screen afterwards, so this waits a frame. */
    private static int openSettings(FabricClientCommandSource src) {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            src.sendError(Component.literal("The settings screen needs Cloth Config. The /imgprev commands change the same settings."));
            return 0;
        }
        Minecraft mc = src.getClient();
        mc.schedule(() -> mc.gui.setScreen(ConfigScreen.create(null)));
        return 1;
    }

    /** {@code /imgprev colour <gif|image|emoji|discord> <name|#RRGGBB>} and {@code /imgprev colour reset}; "color" redirects here. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> colour() {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("colour")
            .then(editing("reset", (src, cfg) -> {
                cfg.labelColors = ImgPrevConfig.defaultLabelColors();
                src.sendFeedback(Component.literal("Label colours reset:").append(allLabels(cfg)));
            }));
        for (Label label : Label.values()) {
            root.then(ClientCommands.literal(label.text().toLowerCase(Locale.ROOT)).then(ClientCommands.argument("colour", StringArgumentType.greedyString())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(ImgPrevConfig.PRESET_COLORS.stream()
                    .map(p -> ImgPrevConfig.colorName(p).toLowerCase(Locale.ROOT).replace(' ', '_')), b))
                .executes(c -> {
                    FabricClientCommandSource src = c.getSource();
                    Optional<TextColor> colour = ImgPrevConfig.parseColor(StringArgumentType.getString(c, "colour"));
                    if (colour.isEmpty()) {
                        src.sendError(Component.literal("Use a chat colour such as aqua or gold, or a hex colour like #FF8800"));
                        return 0;
                    }
                    ImgPrevClient.CONFIG.setLabelColor(label, colour.get());
                    ImgPrevClient.CONFIG.save();
                    src.sendFeedback(Component.literal(label.text() + " labels are now ").append(swatch(label, colour.get())));
                    return 1;
                })));
        }
        return root;
    }

    /** "[▶ Gold]" in that colour. */
    static Component swatch(Label label, TextColor c) {
        return Component.literal("[" + label.icon() + " " + ImgPrevConfig.colorName(c) + "]").withStyle(s -> s.withColor(c));
    }

    /** " [▶ GIF] [⧈ Image] [☺ Emoji] [✉ Discord]", each in its colour. */
    static Component allLabels(ImgPrevConfig cfg) {
        MutableComponent out = Component.empty();
        for (Label label : Label.values()) {
            TextColor c = cfg.labelColor(label);
            out.append(Component.literal(" ")).append(Component.literal(label.labelText()).withStyle(s -> s.withColor(c)));
        }
        return out;
    }

    /** A literal that edits the config, then saves it. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> editing(String name, BiConsumer<FabricClientCommandSource, ImgPrevConfig> edit) {
        return ClientCommands.literal(name).executes(c -> {
            edit.accept(c.getSource(), ImgPrevClient.CONFIG);
            ImgPrevClient.CONFIG.save();
            return 1;
        });
    }

    private static void setSite(FabricClientCommandSource src, ImgPrevConfig cfg, SiteCodec c, boolean on) {
        cfg.setSiteEnabled(c.code(), on);
        src.sendFeedback(Component.literal(c.name() + " previews " + (on ? "on" : "off")));
    }

    private static int status(FabricClientCommandSource src) {
        ImgPrevConfig cfg = ImgPrevClient.CONFIG;
        MutableComponent sites = Component.empty();
        for (SiteCodec c : SiteRegistry.all()) {
            boolean on = cfg.siteEnabled(c.code());
            sites.append(Component.literal(" " + c.name()).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }
        src.sendFeedback(Component.literal("ImgPrev: previews " + (cfg.renderEnabled ? "on" : "off")
            + (cfg.requireShift ? ", Shift to preview" : "") + (cfg.autoConvertLinks ? ", auto-convert on" : "") + ". Sites:").append(sites));
        src.sendFeedback(Component.literal("Labels:").append(allLabels(cfg))
            .append(Component.literal(". All settings: /imgprev settings").withStyle(ChatFormatting.GRAY)));
        src.sendFeedback(Component.literal(String.format("Cache: %d images, %.1f of %d MB",
            ImgPrevClient.CACHE.size(), ImgPrevClient.CACHE.usedBytes() / 1048576.0, cfg.cacheBudgetMB))
            .withStyle(ChatFormatting.GRAY));
        return 1;
    }
}
