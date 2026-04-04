package dev.muhofy.furnaceboard;

import dev.muhofy.furnaceboard.tracker.FurnaceTrackerManager;
import dev.muhofy.furnaceboard.util.FurnaceBoardKeybinds;
import dev.muhofy.furnaceboard.util.FurnaceBoardLogger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

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
        // Phase 5: FurnaceBoardHudWidget.register()
        // Phase 6: FurnaceBoardConfig.init()

        FurnaceBoardLogger.info("FurnaceBoard initialized.");
    }
}