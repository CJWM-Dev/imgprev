package io.github.cjwmdev.imgprev.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.cjwmdev.imgprev.render.ChatImages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Images in the in-game chat (chat closed, messages fading out): draws image rows; nothing to hover. */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess")
abstract class ChatHudLineMixin {
    @Shadow @Final private GuiGraphicsExtractor graphics;

    @WrapMethod(method = "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z")
    private boolean imgprev$imageRow(int textTop, float opacity, FormattedCharSequence message, Operation<Boolean> original) {
        if (message instanceof ChatImages.Row row) message = ChatImages.draw(graphics, row, textTop, opacity, null, Minecraft.getInstance().font, 0, 0);
        return original.call(textTop, opacity, message);
    }
}
