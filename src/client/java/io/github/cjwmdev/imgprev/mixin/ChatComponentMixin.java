package io.github.cjwmdev.imgprev.mixin;

import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.chat.TokenStyler;
import io.github.cjwmdev.imgprev.config.ImgPrevConfig;
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
        ImgPrevConfig cfg = ImgPrevClient.CONFIG;
        return cfg.renderEnabled ? TokenStyler.apply(contents, cfg::allows, cfg::labelColor) : contents;
    }
}
