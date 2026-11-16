package io.github.cjwmdev.imgprev.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Leaving chat for the image viewer resets its scroll; the viewer puts it back to where the player was reading. */
@Mixin(ChatComponent.class)
public interface ChatScrollAccess {
    @Accessor("chatScrollbarPos")
    int imgprev$scrollPos();
}
