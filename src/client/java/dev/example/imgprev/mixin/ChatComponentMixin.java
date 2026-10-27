package dev.example.imgprev.mixin;

import dev.example.imgprev.ImgPrevClient;
import dev.example.imgprev.chat.TokenStyler;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Receive side. The private addMessage is where player chat, server system messages (much of Hypixel's chat)
 * and client messages all meet (docs/VERIFIED.md §1).
 */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
                    at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component imgprev$styleTokens(Component contents) {
        return ImgPrevClient.CONFIG.renderEnabled ? TokenStyler.apply(contents, ImgPrevClient.CONFIG::allows) : contents;
    }
}
