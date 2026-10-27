package dev.example.imgprev.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.example.imgprev.token.TokenCodec;
import java.util.Optional;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

/** {@code /imglink} swaps a supported image URL on the clipboard for a token; {@code /imglink chat} opens chat with it. */
public final class ImgLinkCommand {
    private ImgLinkCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> d, CommandBuildContext ctx) {
        d.register(ClientCommands.literal("imglink")
            .requires(FabricClientCommandSource::attended)   // server text components can't run it unprompted
            .executes(c -> run(c.getSource(), false))
            .then(ClientCommands.literal("chat").executes(c -> run(c.getSource(), true))));
    }

    private static int run(FabricClientCommandSource src, boolean openChat) {
        Minecraft mc = src.getClient();
        Optional<String> token = TokenCodec.encode(mc.keyboardHandler.getClipboard().strip());   // "" when empty or not text
        if (token.isEmpty()) {
            src.sendError(Component.literal("Clipboard isn't a supported image link."));
            return 0;
        }
        if (openChat) {
            // Typed commands run inside the ChatScreen's key handler, which closes the screen afterwards.
            // schedule() always queues; execute() would run at once unless the key arrived inside a task (docs/VERIFIED.md §5).
            mc.schedule(() -> mc.gui.setScreen(new ChatScreen(token.get(), false)));
        } else {
            mc.keyboardHandler.setClipboard(token.get());
            src.sendFeedback(Component.literal("Image token copied: " + token.get()));
        }
        return 1;
    }
}
