package com.simpleautofarm.client;

import java.util.function.Supplier;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only half of the config screen registration.
 * <p>
 * This exists because the reference must not live in {@code SimpleAutoFarm}: a lambda such as
 * {@code (modContainer, screen) -> new ConfigurationScreen(modContainer, screen)} gets a synthetic method whose
 * <b>descriptor</b> mentions {@code net.minecraft.client.gui.screens.Screen}, and NeoForge's RuntimeDistCleaner
 * cannot strip a client type out of a method descriptor - it aborts with "Attempted to load class
 * net/minecraft/client/gui/screens/Screen for invalid dist DEDICATED_SERVER" and mod loading fails.
 * Keeping the call in this class means {@code SimpleAutoFarm} only ever refers to our own type
 * ({@code ConfigScreens::register}, descriptor {@code (ModContainer)void}), which is harmless, and this class is
 * simply never loaded on a dedicated server.
 * <p>
 * Only call this from inside a {@code Dist.CLIENT} check.
 */
public final class ConfigScreens {

    private ConfigScreens() {
    }

    public static void register(ModContainer container) {
        // NeoForge's own screen builds every widget from the mod's ModConfigSpec, so no Cloth Config is needed.
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (Supplier<IConfigScreenFactory>) () -> ConfigurationScreen::new);
    }
}
