package io.github.cjwmdev.imgprev;

import io.github.cjwmdev.imgprev.chat.AutoConvert;
import io.github.cjwmdev.imgprev.command.ImgCommand;
import io.github.cjwmdev.imgprev.command.ImgLinkCommand;
import io.github.cjwmdev.imgprev.command.ImgPrevCommand;
import io.github.cjwmdev.imgprev.config.ImgPrevConfig;
import io.github.cjwmdev.imgprev.image.ImageCache;
import io.github.cjwmdev.imgprev.render.ImageTooltip;
import io.github.cjwmdev.imgprev.saved.SavedImages;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ImgPrevClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("imgprev");
    /** Loaded once at startup; {@code /imgprev reload} reads the file back into this same object. */
    public static ImgPrevConfig CONFIG;
    public static final ImageCache CACHE = new ImageCache();
    /** config/imgprev-saved.json, loaded once at startup like {@link #CONFIG}. */
    public static SavedImages SAVED;

    @Override public void onInitializeClient() {
        CONFIG = ImgPrevConfig.load();
        SAVED = SavedImages.load();
        ImageTooltip.register();
        AutoConvert.register();
        ClientCommandRegistrationCallback.EVENT.register(ImgLinkCommand::register);
        ClientCommandRegistrationCallback.EVENT.register(ImgPrevCommand::register);
        ClientCommandRegistrationCallback.EVENT.register(ImgCommand::register);
        LOG.info("ImgPrev client initialized");
    }
}
