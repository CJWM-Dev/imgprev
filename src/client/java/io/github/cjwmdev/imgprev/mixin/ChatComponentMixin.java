package io.github.cjwmdev.imgprev.mixin;

import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.chat.TokenStyler;
import io.github.cjwmdev.imgprev.config.ImgPrevConfig;
import io.github.cjwmdev.imgprev.render.ChatImages;
import io.github.cjwmdev.imgprev.upload.ScreenshotShare;
import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Receive side. The private addMessage is where player chat, server system messages (much of Hypixel's chat)
 * and client messages all meet (docs/VERIFIED.md §1).
 */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
    private static final int MAX_LINES = 100;   // ChatComponent's own limit on wrapped lines

    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;
    @Shadow private boolean newMessageSinceScroll;
    @Shadow public abstract boolean isChatFocused();
    @Shadow public abstract void scrollChat(int dir);

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
                    at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component imgprev$styleTokens(Component contents) {
        ImgPrevConfig cfg = ImgPrevClient.CONFIG;
        contents = ScreenshotShare.apply(contents);
        return cfg.renderEnabled ? TokenStyler.apply(contents, cfg::allows, cfg::labelColor) : contents;
    }

    /**
     * Images in chat: after vanilla has wrapped a message into lines (newest first in trimmedMessages), add blank
     * rows under it for its images. Runs for new messages and whenever chat is laid out again (resized, settings).
     */
    @Inject(method = "addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V", at = @At("TAIL"))
    private void imgprev$reserveImageRows(GuiMessage message, CallbackInfo ci) {
        List<ChatImages.Row> rows = ChatImages.rowsFor(message.content(), ImgPrevClient.CONFIG, System.currentTimeMillis() / 1000);
        if (rows.isEmpty() || trimmedMessages.isEmpty()) return;
        GuiMessage.Line last = trimmedMessages.getFirst();
        if (last.parent() != message) return;
        trimmedMessages.set(0, new GuiMessage.Line(message, last.content(), false));   // the message now ends after its images
        boolean chatting = isChatFocused();
        for (int i = 0; i < rows.size(); i++) {
            if (chatting && chatScrollbarPos > 0) {   // as vanilla does per line: stay on what you were reading
                newMessageSinceScroll = true;
                scrollChat(1);
            }
            trimmedMessages.addFirst(new GuiMessage.Line(message, rows.get(i), i == rows.size() - 1));
        }
        while (trimmedMessages.size() > MAX_LINES) trimmedMessages.removeLast();
    }
}
