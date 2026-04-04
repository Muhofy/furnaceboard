package dev.muhofy.furnaceboard.tracker;

import dev.muhofy.furnaceboard.data.FurnaceBoardWorldData;
import dev.muhofy.furnaceboard.data.FurnaceRecord;
import dev.muhofy.furnaceboard.data.FurnaceState;
import dev.muhofy.furnaceboard.mixin.AbstractFurnaceBlockEntityAccessor;
import dev.muhofy.furnaceboard.mixin.AbstractFurnaceScreenHandlerAccessor;
import dev.muhofy.furnaceboard.util.FurnaceBoardLogger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AbstractFurnaceScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Core tracking system for FurnaceBoard.
 *
 * Responsibilities:
 *   - On furnace screen open: read BlockPos + state, create/update FurnaceRecord
 *   - Every 20 ticks: recalculate ETA for all tracked furnaces
 *   - Detect DONE transitions → fire FurnaceNotifier (Phase 4)
 *   - Persist changes to FurnaceBoardWorldData
 *
 * All logic runs on the client thread only.
 */
public final class FurnaceTrackerManager {

    /** Recalculate ETA every 20 ticks (1 second). */
    private static final int TICK_INTERVAL = 20;

    private static final FurnaceBoardWorldData worldData = new FurnaceBoardWorldData();
    private static int tickCounter = 0;

    /** Positions that were DONE on the last tick — used to fire notifications only once. */
    private static final Set<BlockPos> notifiedDone = new HashSet<>();

    private FurnaceTrackerManager() {}

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    public static void init() {
        registerScreenEvent();
        registerTickEvent();
        FurnaceBoardLogger.info("FurnaceTrackerManager initialized.");
    }

    public static FurnaceBoardWorldData getWorldData() {
        return worldData;
    }

    // -------------------------------------------------------------------------
    // World lifecycle
    // -------------------------------------------------------------------------

    /**
     * Call when the player joins a world.
     * Loads saved furnace data from furnaceboard.dat.
     *
     * @param worldSaveDir path to .minecraft/saves/<world>/
     * @param staleDays    stale record pruning threshold
     */
    public static void onWorldJoin(java.nio.file.Path worldSaveDir, int staleDays) {
        worldData.init(worldSaveDir, staleDays);
        notifiedDone.clear();
        FurnaceBoardLogger.info("World data loaded. Tracking " + worldData.size() + " furnace(s).");
    }

    /**
     * Call when the player leaves a world.
     * Saves current state to disk.
     */
    public static void onWorldLeave() {
        worldData.save();
        notifiedDone.clear();
        FurnaceBoardLogger.info("World data saved on world leave.");
    }

    // -------------------------------------------------------------------------
    // Screen event — record furnace on open
    // -------------------------------------------------------------------------

    private static void registerScreenEvent() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractFurnaceScreen<?>)) return;

            AbstractFurnaceScreen<?> furnaceScreen = (AbstractFurnaceScreen<?>) screen;
            // getScreenHandler() is from ScreenHandlerProvider<T>, returns T (AbstractFurnaceScreenHandler)
            AbstractFurnaceScreenHandler handler = (AbstractFurnaceScreenHandler) furnaceScreen.getScreenHandler();

            // Use Mixin accessor to get the backing inventory
            Inventory inventory = ((AbstractFurnaceScreenHandlerAccessor) handler).getInventory();

            if (!(inventory instanceof AbstractFurnaceBlockEntity furnaceBE)) {
                // Client-side dummy inventory — BlockPos not available, skip
                FurnaceBoardLogger.debug("Furnace screen opened but inventory is not AbstractFurnaceBlockEntity — skipping.");
                return;
            }

            BlockPos pos = furnaceBE.getPos();
            ClientWorld world = client.world;
            if (world == null) return;

            RegistryKey<World> dimension = world.getRegistryKey();
            recordFurnace(furnaceBE, pos, dimension);
        });
    }

    // -------------------------------------------------------------------------
    // Tick event — recalculate ETA every 20 ticks
    // -------------------------------------------------------------------------

    private static void registerTickEvent() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;

            tickCounter++;
            if (tickCounter < TICK_INTERVAL) return;
            tickCounter = 0;

            ClientWorld world = client.world;
            RegistryKey<World> dimension = world.getRegistryKey();

            for (Map.Entry<BlockPos, FurnaceRecord> entry : worldData.getAll().entrySet()) {
                BlockPos pos = entry.getKey();
                FurnaceRecord oldRecord = entry.getValue();

                // Only update furnaces in the current dimension
                if (!oldRecord.dimension.equals(dimension)) continue;

                // Try to get the live block entity from the world
                if (!(world.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnaceBE)) {
                    // Not loaded — keep existing record, don't update
                    continue;
                }

                FurnaceState oldState = oldRecord.state;
                recordFurnace(furnaceBE, pos, dimension);
                FurnaceRecord newRecord = worldData.get(pos);
                if (newRecord == null) continue;

                // Detect DONE transition — fire notification once per transition
                if (newRecord.state == FurnaceState.DONE && oldState != FurnaceState.DONE) {
                    if (!notifiedDone.contains(pos)) {
                        notifiedDone.add(pos);
                        // Phase 4: FurnaceNotifier.onFurnaceDone(newRecord);
                        FurnaceBoardLogger.info("Furnace DONE at " + pos);
                    }
                } else if (newRecord.state != FurnaceState.DONE) {
                    // Reset notification flag when furnace is no longer DONE
                    notifiedDone.remove(pos);
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Record creation
    // -------------------------------------------------------------------------

    /**
     * Reads current state from a live AbstractFurnaceBlockEntity and
     * creates/updates the FurnaceRecord in worldData.
     *
     * Field names verified against Yarn 1.21.11+build.4:
     *   burnTime, cookTime, cookTimeTotal — package-private in AbstractFurnaceBlockEntity.
     *   Accessed here via PropertyDelegate indices from AbstractFurnaceScreenHandler,
     *   which are synced to the client. This avoids needing direct field access.
     *
     * // UNTESTED — verify PropertyDelegate index mapping in 1.21.11
     */
    private static void recordFurnace(
            AbstractFurnaceBlockEntity furnaceBE,
            BlockPos pos,
            RegistryKey<World> dimension
    ) {
        // Field names verified in Yarn 1.21.11+build.4:
        //   litTimeRemaining  (index 0) — was: burnTime
        //   litTotalTime      (index 1) — was: fuelTime
        //   cookingTimeSpent  (index 2) — was: cookTime
        //   cookingTotalTime  (index 3) — was: cookTimeTotal
        // Accessed via PropertyDelegate (synced to client, no direct field access needed)
        PropertyDelegate props = ((AbstractFurnaceBlockEntityAccessor) furnaceBE).getPropertyDelegate();
        int burnTime      = props.get(AbstractFurnaceBlockEntity.BURN_TIME_PROPERTY_INDEX);
        int cookTime      = props.get(AbstractFurnaceBlockEntity.COOK_TIME_PROPERTY_INDEX);
        int cookTimeTotal = props.get(AbstractFurnaceBlockEntity.COOK_TIME_TOTAL_PROPERTY_INDEX);

        // Input slot is slot index 0 per AbstractFurnaceScreenHandler
        ItemStack inputStack = furnaceBE.getStack(0);
        // Output slot is slot index 2
        ItemStack outputStack = furnaceBE.getStack(2);

        @Nullable Identifier inputItem = inputStack.isEmpty()
                ? null
                : inputStack.getItem().getRegistryEntry().registryKey().getValue();

        FurnaceState state = computeState(inputStack, outputStack, burnTime, cookTime, cookTimeTotal);

        FurnaceRecord record = new FurnaceRecord(
                pos,
                dimension,
                inputItem,
                inputStack.getCount(),
                cookTimeTotal,
                cookTime,
                burnTime,
                state,
                System.currentTimeMillis()
        );

        worldData.put(pos, record);
        worldData.save();
    }

    /**
     * Derives FurnaceState from raw furnace field values.
     *
     * Logic:
     *   - Output has items → DONE
     *   - Input empty → EMPTY
     *   - Fuel present (burnTime > 0) → SMELTING
     *   - No fuel → NO_FUEL
     */
    private static FurnaceState computeState(
            ItemStack input,
            ItemStack output,
            int burnTime,
            int cookTime,
            int cookTimeTotal
    ) {
        if (!output.isEmpty()) return FurnaceState.DONE;
        if (input.isEmpty())   return FurnaceState.EMPTY;
        if (burnTime > 0)      return FurnaceState.SMELTING;
        return FurnaceState.NO_FUEL;
    }
}