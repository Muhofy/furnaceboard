package dev.muhofy.furnaceboard.tracker;

import dev.muhofy.furnaceboard.data.FurnaceBoardWorldData;
import dev.muhofy.furnaceboard.util.FurnaceBoardLogger;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages the set of tracked furnace positions and notification state.
 *
 * Actual state reading is done in AbstractFurnaceBlockEntityMixin.tick()
 * which injects into the furnace tick method — no polling, no world scan,
 * no handler dependency.
 *
 * Tracking modes:
 *   - autoTrack=true (default): all client-visible furnaces are tracked
 *   - excluded: player can remove specific furnaces via dashboard ✕ button
 */
public final class FurnaceTrackerManager {

    private static final FurnaceBoardWorldData worldData = new FurnaceBoardWorldData();

    /** Furnaces the player explicitly removed from tracking. */
    private static final Set<BlockPos> excluded = new HashSet<>();

    /** Furnaces that have already fired a DONE notification this cycle. */
    private static final Set<BlockPos> notified = new HashSet<>();

    /**
     * When true, all client-visible furnaces are tracked automatically.
     * When false, only manually added furnaces are tracked.
     */
    private static boolean autoTrack = true;

    private FurnaceTrackerManager() {}

    public static void init() {
        FurnaceBoardLogger.info("FurnaceTrackerManager initialized.");
    }

    public static FurnaceBoardWorldData getWorldData() {
        return worldData;
    }

    // -------------------------------------------------------------------------
    // Tracking control
    // -------------------------------------------------------------------------

    public static boolean isAutoTrack() {
        return autoTrack;
    }

    public static boolean isTracked(BlockPos pos) {
        return worldData.get(pos) != null && !excluded.contains(pos);
    }

    public static void excludeFurnace(BlockPos pos) {
        excluded.add(pos);
        worldData.remove(pos);
        worldData.save();
        FurnaceBoardLogger.info("Furnace excluded at " + pos);
    }

    public static boolean isExcluded(BlockPos pos) {
        return excluded.contains(pos);
    }

    public static void clearExclusion(BlockPos pos) {
        excluded.remove(pos);
    }

    // -------------------------------------------------------------------------
    // Notification state
    // -------------------------------------------------------------------------

    public static boolean isNotified(BlockPos pos) {
        return notified.contains(pos);
    }

    public static void markNotified(BlockPos pos) {
        notified.add(pos);
    }

    public static void clearNotified(BlockPos pos) {
        notified.remove(pos);
    }

    // -------------------------------------------------------------------------
    // World lifecycle
    // -------------------------------------------------------------------------

    public static void onWorldJoin(java.nio.file.Path worldSaveDir, int staleDays) {
        worldData.init(worldSaveDir, staleDays);
        excluded.clear();
        notified.clear();
        FurnaceBoardLogger.info("World data loaded. Tracking " + worldData.size() + " furnace(s).");
    }

    public static void onWorldLeave() {
        worldData.save();
        excluded.clear();
        notified.clear();
        FurnaceBoardLogger.info("World data saved on world leave.");
    }
}