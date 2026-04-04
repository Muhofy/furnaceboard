package dev.muhofy.furnaceboard;

import dev.muhofy.furnaceboard.tracker.FurnaceTrackerManager;
import dev.muhofy.furnaceboard.ui.FurnaceBoardHudWidget;
import dev.muhofy.furnaceboard.ui.FurnaceBoardScreen;
import dev.muhofy.furnaceboard.util.FurnaceBoardKeybinds;
import dev.muhofy.furnaceboard.util.FurnaceBoardLogger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * FurnaceBoard — client-side entry point.
 * Registers all systems on mod init.
 * All logic is client-only; no server-side code in v1.
 */
@Environment(EnvType.CLIENT)
public class FurnaceBoardMod implements ClientModInitializer {

    public static final String MOD_ID = "furnaceboard";

    @Override
    public void onInitializeClient() {
        FurnaceBoardLogger.info("FurnaceBoard initializing...");

        FurnaceBoardKeybinds.register();
        FurnaceTrackerManager.init();
        FurnaceBoardHudWidget.register();

        // Keybind tick — open dashboard when F is pressed and no screen is open
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (FurnaceBoardKeybinds.openDashboard.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new FurnaceBoardScreen());
                }
            }
        });

        // Phase 6: FurnaceBoardConfig.init()

        FurnaceBoardLogger.info("FurnaceBoard initialized.");
    }
}