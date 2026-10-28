package dev.example.imgprev.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.example.imgprev.ImgPrevClient;
import dev.example.imgprev.config.ImgPrevConfig;
import dev.example.imgprev.site.SiteCodec;
import dev.example.imgprev.site.SiteRegistry;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * {@code /imgprev} settings. Every change is saved to config/imgprev.json. Toggles apply to new chat lines and to
 * hovering; labels already in chat stay as they are.
 */
public final class ImgPrevCommand {
    private ImgPrevCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> d, CommandBuildContext ctx) {
        LiteralArgumentBuilder<FabricClientCommandSource> site = ClientCommands.literal("site");
        for (SiteCodec c : SiteRegistry.all()) {
            site.then(ClientCommands.literal(c.name())
                .then(toggle("on", (src, cfg) -> setSite(src, cfg, c, true)))
                .then(toggle("off", (src, cfg) -> setSite(src, cfg, c, false))));
        }
        d.register(ClientCommands.literal("imgprev")
            .requires(FabricClientCommandSource::attended)
            .executes(c -> status(c.getSource()))
            .then(toggle("on", (src, cfg) -> { cfg.renderEnabled = true; src.sendFeedback(ok("Image previews on")); }))
            .then(toggle("off", (src, cfg) -> { cfg.renderEnabled = false; src.sendFeedback(ok("Image previews off")); }))
            .then(ClientCommands.literal("shift")
                .then(toggle("on", (src, cfg) -> { cfg.requireShift = true; src.sendFeedback(ok("Previews need Shift held")); }))
                .then(toggle("off", (src, cfg) -> { cfg.requireShift = false; src.sendFeedback(ok("Previews show on hover")); })))
            .then(ClientCommands.literal("autoconvert")
                .then(toggle("on", (src, cfg) -> { cfg.autoConvertLinks = true; src.sendFeedback(ok("Image links in messages you send become tokens")); }))
                .then(toggle("off", (src, cfg) -> { cfg.autoConvertLinks = false; src.sendFeedback(ok("Links are sent as typed")); })))
            .then(site)
            .then(ClientCommands.literal("cache").then(ClientCommands.literal("clear").executes(c -> {
                ImgPrevClient.CACHE.clear();
                c.getSource().sendFeedback(ok("Preview cache cleared"));
                return 1;
            })))
            .then(ClientCommands.literal("reload").executes(c -> {
                ImgPrevClient.CONFIG = ImgPrevConfig.load();
                ImgPrevClient.CACHE.clear();   // size limits may have changed
                c.getSource().sendFeedback(ok("Reloaded " + ImgPrevConfig.path().getFileName()));
                return 1;
            })));
    }

    /** A literal that edits the config, then saves it. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> toggle(String name, BiConsumer<FabricClientCommandSource, ImgPrevConfig> edit) {
        return ClientCommands.literal(name).executes(c -> {
            edit.accept(c.getSource(), ImgPrevClient.CONFIG);
            ImgPrevClient.CONFIG.save();
            return 1;
        });
    }

    private static void setSite(FabricClientCommandSource src, ImgPrevConfig cfg, SiteCodec c, boolean on) {
        cfg.sites.put(String.valueOf(c.code()), on);
        src.sendFeedback(ok(c.name() + " previews " + (on ? "on" : "off")));
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
        src.sendFeedback(Component.literal(String.format("Cache: %d images, %.1f of %d MB",
            ImgPrevClient.CACHE.size(), ImgPrevClient.CACHE.usedBytes() / 1048576.0, cfg.cacheBudgetMB))
            .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static Component ok(String message) { return Component.literal(message); }
}
