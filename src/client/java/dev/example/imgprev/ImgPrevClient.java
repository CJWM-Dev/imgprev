package dev.example.imgprev;

import dev.example.imgprev.chat.AutoConvert;
import dev.example.imgprev.command.ImgLinkCommand;
import dev.example.imgprev.command.ImgPrevCommand;
import dev.example.imgprev.config.ImgPrevConfig;
import dev.example.imgprev.image.ImageCache;
import dev.example.imgprev.render.ImageTooltip;
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
