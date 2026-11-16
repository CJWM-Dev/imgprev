package io.github.cjwmdev.imgprev.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.cjwmdev.imgprev.render.MediaViewerScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Double-clicking a label or an image in chat opens it full screen. Vanilla only reports a double-click when it
 * handled the first click, which it doesn't for labels (they have no click action), so the viewer counts them itself.
 */
@Mixin(ChatScreen.class)
abstract class ChatScreenMixin {
    /** Labels and images note themselves as hovered while this frame draws; clear the last frame's first. */
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"))
    private void imgprev$newFrame(GuiGraphicsExtractor g, int mx, int my, float a, CallbackInfo ci) {
        MediaViewerScreen.chatFrameStarted();
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)
    private void imgprev$openViewer(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && MediaViewerScreen.chatClicked((ChatScreen) (Object) this)) cir.setReturnValue(true);
    }
}
