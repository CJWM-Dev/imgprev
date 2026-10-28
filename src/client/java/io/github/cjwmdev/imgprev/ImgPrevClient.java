package io.github.cjwmdev.imgprev;

import io.github.cjwmdev.imgprev.chat.AutoConvert;
import io.github.cjwmdev.imgprev.command.ImgLinkCommand;
import io.github.cjwmdev.imgprev.command.ImgPrevCommand;
import io.github.cjwmdev.imgprev.config.ImgPrevConfig;
import io.github.cjwmdev.imgprev.image.ImageCache;
import io.github.cjwmdev.imgprev.render.ImageTooltip;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ImgPrevClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("imgprev");
    /** Loaded once at startup; {@code /imgprev reload} reads the file back into this same object. */
    public static ImgPrevConfig CONFIG;
    public static final ImageCache CACHE = new ImageCache();

    @Override public void onInitializeClient() {
        CONFIG = ImgPrevConfig.load();
        ImageTooltip.register();
        AutoConvert.register();
        ClientCommandRegistrationCallback.EVENT.register(ImgLinkCommand::register);
        ClientCommandRegistrationCallback.EVENT.register(ImgPrevCommand::register);
        LOG.info("ImgPrev client initialized");
    }
}
