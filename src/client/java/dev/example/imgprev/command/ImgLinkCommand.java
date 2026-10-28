package dev.example.imgprev.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.example.imgprev.site.LinkConverter;
import dev.example.imgprev.token.TokenCodec;
import java.util.Optional;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * {@code /imglink [link]} swaps a supported image link (the clipboard's, or one typed after the command) for a
 * token on the clipboard; {@code /imglink chat [link]} opens chat with it instead.
 */
public final class ImgLinkCommand {
    private ImgLinkCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> d, CommandBuildContext ctx) {
        d.register(ClientCommands.literal("imglink")
            .requires(FabricClientCommandSource::attended)   // server text components can't run it unprompted
            .executes(c -> run(c.getSource(), null, false))
            .then(ClientCommands.literal("chat")
                .executes(c -> run(c.getSource(), null, true))
                .then(ClientCommands.argument("link", StringArgumentType.greedyString())
                    .executes(c -> run(c.getSource(), StringArgumentType.getString(c, "link"), true))))
            .then(ClientCommands.argument("link", StringArgumentType.greedyString())
                .executes(c -> run(c.getSource(), StringArgumentType.getString(c, "link"), false))));
    }

    /** {@code typed == null} reads the clipboard. */
    private static int run(FabricClientCommandSource src, @Nullable String typed, boolean openChat) {
        Minecraft mc = src.getClient();
        String link = (typed == null ? mc.keyboardHandler.getClipboard() : typed).strip();   // "" when empty or not text
        Optional<String> token = TokenCodec.encode(link);
        if (token.isPresent()) {
            deliver(src, token.get(), openChat);
            return 1;
        }
        if (!LinkConverter.needsLookup(link)) {
            src.sendError(Component.literal(typed == null ? "Clipboard isn't a supported image link." : "That isn't a supported image link."));
            return 0;
        }
        if (!LinkConverter.canLookUp(link)) {
            src.sendError(Component.literal(LinkConverter.KLIPY_NEEDS_KEY));
            return 0;
        }
        src.sendFeedback(Component.literal("Looking up the image…"));
        LinkConverter.convert(link).whenComplete((t, err) -> mc.execute(() -> {
            if (err != null) src.sendError(Component.literal(LinkConverter.describe(err)));
            else deliver(src, t, openChat);
        }));
        return 1;
    }

    private static void deliver(FabricClientCommandSource src, String token, boolean openChat) {
        Minecraft mc = src.getClient();
        if (openChat) {
            // Typed commands run inside the ChatScreen's key handler, which closes the screen afterwards.
            // schedule() always queues; execute() would run at once unless the key arrived inside a task (docs/VERIFIED.md §5).
            mc.schedule(() -> mc.gui.setScreen(new ChatScreen(token, false)));
        } else {
            mc.keyboardHandler.setClipboard(token);
            src.sendFeedback(Component.literal("Image token copied: " + token));
        }
    }
}
