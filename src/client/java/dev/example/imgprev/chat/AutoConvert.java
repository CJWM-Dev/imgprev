package dev.example.imgprev.chat;

import dev.example.imgprev.ImgPrevClient;
import dev.example.imgprev.site.LinkConverter;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

/**
 * Optional ({@code autoConvertLinks}): image links in outgoing chat, and in chat-like commands such as Hypixel's
 * {@code /pc}, become tokens as the message is sent. Messages needing a Klipy lookup are held back and re-sent once
 * converted; anything that can't be sent is put back in the chat box with the reason.
 */
public final class AutoConvert {
    /** The server refuses chat messages and commands longer than this. */
    static final int MAX_LENGTH = 256;

    private AutoConvert() {}

    public static void register() {
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !enabled() || allow(message, false));
        ClientSendMessageEvents.MODIFY_CHAT.register(message -> enabled() ? LinkConverter.convertNow(message) : message);
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> !appliesTo(command) || allow(command, true));
        ClientSendMessageEvents.MODIFY_COMMAND.register(command -> appliesTo(command) ? LinkConverter.convertNow(command) : command);
    }

    static boolean enabled() { return ImgPrevClient.CONFIG.autoConvertLinks; }

    static boolean appliesTo(String command) {
        return enabled() && ImgPrevClient.CONFIG.autoConvertCommands.contains(command.split(" ", 2)[0].toLowerCase(Locale.ROOT));
    }

    /** True to send now (MODIFY_* then converts); false holds the message back. */
    private static boolean allow(String text, boolean command) {
        List<String> lookups = LinkConverter.lookupsIn(text);
        if (lookups.isEmpty()) return fits(LinkConverter.convertNow(text), text, command);
        if (!LinkConverter.canLookUp()) {
            refuse(text, command, LinkConverter.KLIPY_NEEDS_KEY);
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        LinkConverter.convertAll(text).whenComplete((out, err) -> mc.execute(() -> {
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
        int length = converted.length() + (command ? 1 : 0);
        if (length <= MAX_LENGTH) return true;
        refuse(original, command, "too long once its links are converted (" + length + "/" + MAX_LENGTH + ")");
        return false;
    }

    /** Says why the message wasn't sent and puts it back in the chat box to edit. */
    private static void refuse(String text, boolean command, String why) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Message not sent: " + why).withStyle(ChatFormatting.RED));
        mc.schedule(() -> mc.gui.setScreen(new ChatScreen(command ? "/" + text : text, false)));
    }
}
