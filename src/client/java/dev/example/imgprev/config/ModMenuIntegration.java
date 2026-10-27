package dev.example.imgprev.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/** Mod Menu entrypoint. Without Cloth Config, Mod Menu just shows no settings button for ImgPrev. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return FabricLoader.getInstance().isModLoaded("cloth-config") ? ConfigScreen::create : ModMenuApi.super.getModConfigScreenFactory();
    }
}
