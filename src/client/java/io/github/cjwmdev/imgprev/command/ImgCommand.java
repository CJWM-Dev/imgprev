package io.github.cjwmdev.imgprev.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.saved.SavedImages;
import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Saved images ({@link SavedImages}): {@code /img save <name> [link]} (the clipboard's link if none is typed),
 * {@code /img <name>} to send one, {@code /img list} (or just {@code /img}) and {@code /img remove <name>}.
 */
public final class ImgCommand {
    private static final SuggestionProvider<FabricClientCommandSource> SAVED_NAMES =
        (c, b) -> SharedSuggestionProvider.suggest(ImgPrevClient.SAVED.all().keySet(), b);

    private ImgCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> d, CommandBuildContext ctx) {
        d.register(ClientCommands.literal("img")
            .requires(FabricClientCommandSource::attended)   // server text components can't send or change saved images
            .executes(c -> list(c.getSource()))
            .then(ClientCommands.literal("list").executes(c -> list(c.getSource())))
            .then(ClientCommands.literal("save")
                .executes(c -> usage(c.getSource(), "/img save <name> <link>, or just /img save <name> to use the link you copied"))
                .then(ClientCommands.argument("name", StringArgumentType.word())
                    .executes(c -> save(c.getSource(), StringArgumentType.getString(c, "name"), null))
                    .then(ClientCommands.argument("link", StringArgumentType.greedyString())
                        .executes(c -> save(c.getSource(), StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "link"))))))
            .then(ClientCommands.literal("remove")
                .executes(c -> usage(c.getSource(), "/img remove <name>"))
                .then(ClientCommands.argument("name", StringArgumentType.word()).suggests(SAVED_NAMES)
                    .executes(c -> remove(c.getSource(), StringArgumentType.getString(c, "name")))))
            .then(ClientCommands.argument("name", StringArgumentType.word()).suggests(SAVED_NAMES)
                .executes(c -> send(c.getSource(), StringArgumentType.getString(c, "name")))));
    }

    private static int usage(FabricClientCommandSource src, String how) {
        src.sendError(Component.literal("Use " + how));
        return 0;
    }

    /** A pasted token is saved as it is; links are converted (or looked up) first, so sending never waits. */
    private static int save(FabricClientCommandSource src, String rawName, @Nullable String typed) {
        String name = SavedImages.normalise(rawName);
        Optional<String> problem = SavedImages.nameProblem(name);
        if (problem.isPresent()) {
            src.sendError(Component.literal(problem.get()));
            return 0;
        }
        String input = (typed == null ? src.getClient().keyboardHandler.getClipboard() : typed).strip();
        if (TokenCodec.decode(input).isPresent()) {
            store(src, name, input, input);
            return 1;
        }
        return ImgLinkCommand.toToken(src, typed, token -> store(src, name, token, input));
    }

    private static void store(FabricClientCommandSource src, String name, String token, String link) {
        Optional<SavedImages.Entry> old = ImgPrevClient.SAVED.put(name, new SavedImages.Entry(token, link));
        MutableComponent msg = Component.literal((old.isPresent() ? "Replaced " : "Saved ") + name + " ").append(Component.literal(token))
            .append(Component.literal(". Send it with "))
            .append(Component.literal("/img " + name).withStyle(s -> s.withUnderlined(true)
                .withClickEvent(new ClickEvent.SuggestCommand("/img " + name))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to type it")))));
        if (ImgPrevClient.CONFIG.savedNameShortcuts) msg.append(Component.literal(", or :" + name + ": in a message"));
        src.sendFeedback(msg.append(Component.literal(".")));
        old.filter(o -> !o.link().equals(link))
            .ifPresent(o -> src.sendFeedback(Component.literal("It used to be " + o.link()).withStyle(ChatFormatting.GRAY)));
    }

    /** Puts the token in the chat box, or sends it at once with {@code sendSavedImmediately}. */
    private static int send(FabricClientCommandSource src, String name) {
        Optional<SavedImages.Entry> e = ImgPrevClient.SAVED.get(name);
        if (e.isEmpty()) {
            src.sendError(Component.literal("Nothing is saved as " + SavedImages.normalise(name) + ". /img list shows your saved images."));
            return 0;
        }
        String token = e.get().token();
        Minecraft mc = src.getClient();
        if (ImgPrevClient.CONFIG.sendSavedImmediately) {
            if (mc.player == null) return 0;
            mc.player.connection.sendChat(token);
        } else {
            // As /imglink chat: the ChatScreen this was typed in closes once the command returns (docs/VERIFIED.md §5).
            mc.schedule(() -> mc.gui.setScreen(new ChatScreen(token, false)));
        }
        return 1;
    }

    /**
     * One line: each name followed by its token, which chat shows as a label to hover. Clicking an entry puts its token
     * in the chat box; it can't run {@code /img}, since commands run by clicking chat aren't {@code attended}.
     */
    private static int list(FabricClientCommandSource src) {
        Map<String, SavedImages.Entry> all = ImgPrevClient.SAVED.all();
        if (all.isEmpty()) {
            src.sendFeedback(Component.literal("No saved images yet. Save one with /img save <name> <link>."));
            return 1;
        }
        MutableComponent line = Component.literal("Saved images (" + all.size() + "), hover to preview, click to use:").withStyle(ChatFormatting.GRAY);
        all.forEach((name, e) -> line.append(Component.literal(" ")).append(Component.literal(name + " ").withStyle(s -> s
            .withColor(ChatFormatting.WHITE)
            .withClickEvent(new ClickEvent.SuggestCommand(e.token()))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(e.link() + "\nClick to put it in the chat box"))))
            .append(Component.literal(e.token()))));
        src.sendFeedback(line);
        return 1;
    }

    private static int remove(FabricClientCommandSource src, String name) {
        Optional<SavedImages.Entry> old = ImgPrevClient.SAVED.remove(name);
        if (old.isEmpty()) {
            src.sendError(Component.literal("Nothing is saved as " + SavedImages.normalise(name) + "."));
            return 0;
        }
        src.sendFeedback(Component.literal("Removed " + SavedImages.normalise(name) + ". It was " + old.get().link()));
        return 1;
    }
}
