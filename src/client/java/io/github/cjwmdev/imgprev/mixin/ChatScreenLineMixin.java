package io.github.cjwmdev.imgprev.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.cjwmdev.imgprev.render.ChatImages;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Images in open chat: draws image rows, and hovering an image shows the full-size preview. */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess")
abstract class ChatScreenLineMixin {
    @Shadow @Final private GuiGraphicsExtractor graphics;
    @Shadow @Final private Font font;
    @Shadow @Final private int globalMouseX;
    @Shadow @Final private int globalMouseY;
    @Shadow @Final private Vector2f localMousePos;   // the cursor under chat's transform, like the rows' coordinates

    @WrapMethod(method = "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z")
    private boolean imgprev$imageRow(int textTop, float opacity, FormattedCharSequence message, Operation<Boolean> original) {
        if (message instanceof ChatImages.Row row) message = ChatImages.draw(graphics, row, textTop, opacity, localMousePos, font, globalMouseX, globalMouseY);
        return original.call(textTop, opacity, message);
    }
}
