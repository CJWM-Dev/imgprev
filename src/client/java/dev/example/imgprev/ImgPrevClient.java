package dev.example.imgprev;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ImgPrevClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("imgprev");

    @Override public void onInitializeClient() {
        LOG.info("ImgPrev client initialized");
    }
}
