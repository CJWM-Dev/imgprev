package dev.example.imgprev.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
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
            .then(site)
            .then(ClientCommands.literal("klipy")
                .then(ClientCommands.literal("key").then(ClientCommands.argument("key", StringArgumentType.word()).executes(c -> {
                    ImgPrevConfig cfg = ImgPrevClient.CONFIG;
                    cfg.klipyApiKey = StringArgumentType.getString(c, "key");
                    cfg.sites.put("k", true);
                    cfg.save();
                    c.getSource().sendFeedback(ok("Klipy API key saved; Klipy previews on"));
                    return 1;
                })))
                .then(toggle("clear", (src, cfg) -> { cfg.klipyApiKey = ""; src.sendFeedback(ok("Klipy API key removed")); })))
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
        String note = on && c.code() == 'k' && cfg.klipyApiKey.isBlank() ? " (set a key: /imgprev klipy key <key>)" : "";
        src.sendFeedback(ok(c.name() + " previews " + (on ? "on" : "off") + note));
    }

    private static int status(FabricClientCommandSource src) {
        ImgPrevConfig cfg = ImgPrevClient.CONFIG;
        MutableComponent sites = Component.empty();
        for (SiteCodec c : SiteRegistry.all()) {
            boolean on = cfg.siteEnabled(c.code());
            sites.append(Component.literal(" " + c.name()).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }
        src.sendFeedback(Component.literal("ImgPrev: previews " + (cfg.renderEnabled ? "on" : "off")
            + (cfg.requireShift ? ", Shift to preview" : "") + ". Sites:").append(sites));
        src.sendFeedback(Component.literal(String.format("Cache: %d images, %.1f of %d MB. Klipy key: %s",
            ImgPrevClient.CACHE.size(), ImgPrevClient.CACHE.usedBytes() / 1048576.0, cfg.cacheBudgetMB, cfg.klipyApiKey.isBlank() ? "none" : "set"))
            .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static Component ok(String message) { return Component.literal(message); }
}
