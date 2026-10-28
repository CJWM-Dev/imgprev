package dev.example.imgprev.mixin;

import dev.example.imgprev.ImgPrevClient;
import dev.example.imgprev.config.ImgPrevConfig;
import dev.example.imgprev.render.ImageTooltip;
import dev.example.imgprev.token.ResolvedMedia;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Where a hovered ShowText would become a text tooltip; token hovers become image previews instead (docs/VERIFIED.md §2). */
@Mixin(GuiGraphicsExtractor.class)
abstract class HoverTooltipMixin {
    @Inject(method = "componentHoverEffect(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Style;II)V", at = @At("HEAD"), cancellable = true)
    private void imgprev$imageTooltip(Font font, Style style, int mx, int my, CallbackInfo ci) {
        ImgPrevConfig cfg = ImgPrevClient.CONFIG;
        if (!cfg.renderEnabled || !(style.getHoverEvent() instanceof HoverEvent.ShowText(Component value))) return;
        String token = value.getString();
        Optional<ResolvedMedia> media = ImageTooltip.resolve(token).filter(cfg::allows);
        if (media.isEmpty()) return;
        ci.cancel();
        GuiGraphicsExtractor g = (GuiGraphicsExtractor) (Object) this;
        if (cfg.requireShift && !Minecraft.getInstance().hasShiftDown()) ImageTooltip.text(g, font, "Hold Shift to preview", mx, my);
        else ImageTooltip.show(g, font, token, media.get(), mx, my);
    }
}
