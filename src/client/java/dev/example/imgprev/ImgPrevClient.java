package dev.example.imgprev;

import dev.example.imgprev.command.ImgLinkCommand;
import dev.example.imgprev.command.ImgPrevCommand;
import dev.example.imgprev.config.ImgPrevConfig;
import dev.example.imgprev.image.ImageCache;
import dev.example.imgprev.render.ImageTooltip;
import dev.example.imgprev.site.SiteRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ImgPrevClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("imgprev");
    public static ImgPrevConfig CONFIG;
    public static ImageCache CACHE;

    @Override public void onInitializeClient() {
        CONFIG = ImgPrevConfig.load();
        CACHE = new ImageCache();
        SiteRegistry.klipyApiKey(() -> CONFIG.klipyApiKey);
        ImageTooltip.register();
        ClientCommandRegistrationCallback.EVENT.register(ImgLinkCommand::register);
        ClientCommandRegistrationCallback.EVENT.register(ImgPrevCommand::register);
        LOG.info("ImgPrev client initialized");
    }
}
