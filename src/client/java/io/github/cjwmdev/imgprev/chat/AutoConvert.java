package io.github.cjwmdev.imgprev.chat;

import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.site.LinkConverter;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

/**
 * Optional ({@code autoConvertLinks}): image links in outgoing chat, and in chat-like commands such as Hypixel's
 * {@code /pc}, become tokens as the message is sent. Messages with page links to look up are held back and re-sent once
 * converted; anything that can't be sent is put back in the chat box with the reason. Separately optional
 * ({@code savedNameShortcuts}): {@code :name:} of a saved image becomes its token in the same messages.
 */
public final class AutoConvert {
    /** The server refuses chat messages and commands longer than this. */
    static final int MAX_LENGTH = 256;

    private AutoConvert() {}

    public static void register() {
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !enabled() || allow(message, false));
        ClientSendMessageEvents.MODIFY_CHAT.register(message -> enabled() ? convertNow(message) : message);
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> !appliesTo(command) || allow(command, true));
        ClientSendMessageEvents.MODIFY_COMMAND.register(command -> appliesTo(command) ? convertNow(command) : command);
    }

    static boolean links() { return ImgPrevClient.CONFIG.autoConvertLinks; }
    static boolean names() { return ImgPrevClient.CONFIG.savedNameShortcuts; }
    static boolean enabled() { return links() || names(); }

    /** Saved names first, then links that convert without a lookup; tokens contain no links, so the order is safe. */
    static String convertNow(String text) {
        String out = names() ? ImgPrevClient.SAVED.expand(text) : text;
        return links() ? LinkConverter.convertNow(out) : out;
    }

    static boolean appliesTo(String command) { return enabled() && appliesTo(command, ImgPrevClient.CONFIG.autoConvertCommands); }

    /** Whether {@code command} (without its slash) starts with one of {@code chatCommands}. */
    static boolean appliesTo(String command, List<String> chatCommands) {
        return chatCommands.contains(command.split(" ", 2)[0].toLowerCase(Locale.ROOT));
    }

    /** What the server counts: a command's slash is part of its length. */
    static int sentLength(String converted, boolean command) { return converted.length() + (command ? 1 : 0); }

    /** True to send now (MODIFY_* then converts); false holds the message back. */
    private static boolean allow(String text, boolean command) {
        List<String> lookups = links() ? LinkConverter.lookupsIn(text) : List.of();
        if (lookups.isEmpty()) return fits(convertNow(text), text, command);
        if (!lookups.stream().allMatch(LinkConverter::canLookUp)) {
            refuse(text, command, LinkConverter.KLIPY_PAGE_LINK);
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Looking up the image before sending…").withStyle(ChatFormatting.GRAY));
        LinkConverter.convertAll(names() ? ImgPrevClient.SAVED.expand(text) : text).whenComplete((out, err) -> mc.execute(() -> {
            if (err != null) refuse(text, command, LinkConverter.describe(err));
            else if (fits(out, text, command) && mc.player != null) {
                // Re-sending goes through these events again; the links are tokens by now, so it passes straight through.
                if (command) mc.player.connection.sendCommand(out);
                else mc.player.connection.sendChat(out);
            }
        }));
        return false;
    }

    private static boolean fits(String converted, String original, boolean command) {
        int length = sentLength(converted, command);
        if (length <= MAX_LENGTH) return true;
        refuse(original, command, "too long once converted (" + length + "/" + MAX_LENGTH + ")");
        return false;
    }

    /** Says why the message wasn't sent and puts it back in the chat box to edit. */
    private static void refuse(String text, boolean command, String why) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Message not sent: " + why).withStyle(ChatFormatting.RED));
        mc.schedule(() -> mc.gui.setScreen(new ChatScreen(command ? "/" + text : text, false)));
    }
}
