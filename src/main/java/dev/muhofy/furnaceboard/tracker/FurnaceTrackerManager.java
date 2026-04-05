package dev.muhofy.furnaceboard.tracker;

import dev.muhofy.furnaceboard.data.FurnaceBoardWorldData;
import dev.muhofy.furnaceboard.data.FurnaceRecord;
import dev.muhofy.furnaceboard.data.FurnaceState;
import dev.muhofy.furnaceboard.mixin.AbstractFurnaceScreenHandlerWorldAccessor;
import dev.muhofy.furnaceboard.notification.FurnaceNotifier;
import dev.muhofy.furnaceboard.util.FurnaceBoardLogger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractFurnaceScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
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
 * Uses AbstractFurnaceScreenHandler public methods (getCookProgress, getFuelProgress, isBurning)
 * to read furnace state — avoids PropertyDelegate/reflection issues in production.
 *
 * BlockPos is found via world scan around the player when a furnace screen opens.
 * Handler reference is kept while screen is open for live tick updates.
 */
public final class FurnaceTrackerManager {

    private static final int TICK_INTERVAL = 20;
    private static final int SCAN_RADIUS   = 5;

    // DEFAULT_COOK_TIME verified in Yarn 1.21.11+build.4
    private static final int DEFAULT_COOK_TIME_TOTAL = 200;

    private static final FurnaceBoardWorldData worldData = new FurnaceBoardWorldData();
    private static int tickCounter = 0;
    private static final Set<BlockPos> notifiedDone = new HashSet<>();

    private static Screen lastScreen = null;

    /** Currently open furnace handler — kept for live tick updates while screen is open. */
    @Nullable
    private static AbstractFurnaceScreenHandler openHandler = null;
    @Nullable
    private static BlockPos openFurnacePos = null;

    private FurnaceTrackerManager() {}

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    public static void init() {
        registerTickEvent();
        FurnaceBoardLogger.info("FurnaceTrackerManager initialized.");
    }

    public static FurnaceBoardWorldData getWorldData() {
        return worldData;
    }

    public static void onWorldJoin(java.nio.file.Path worldSaveDir, int staleDays) {
        worldData.init(worldSaveDir, staleDays);
        notifiedDone.clear();
        FurnaceBoardLogger.info("World data loaded. Tracking " + worldData.size() + " furnace(s).");
    }

    public static void onWorldLeave() {
        worldData.save();
        notifiedDone.clear();
        openHandler = null;
        openFurnacePos = null;
        FurnaceBoardLogger.info("World data saved on world leave.");
    }

    // -------------------------------------------------------------------------
    // Tick event
    // -------------------------------------------------------------------------

    private static void registerTickEvent() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;

            // --- Screen change detection ---
            Screen currentScreen = client.currentScreen;
            if (currentScreen != lastScreen) {
                lastScreen = currentScreen;

                if (currentScreen instanceof AbstractFurnaceScreen<?> furnaceScreen) {
                    FurnaceBoardLogger.info("Furnace screen detected!");
                    AbstractFurnaceScreenHandler handler =
                            (AbstractFurnaceScreenHandler) furnaceScreen.getScreenHandler();

                    // Find furnace pos via world scan
                    BlockPos pos = findFurnacePos(client.world, client.player.getBlockPos());
                    if (pos == null) {
                        FurnaceBoardLogger.info("Could not find furnace near player.");
                        return;
                    }

                    FurnaceBoardLogger.info("Found furnace at " + pos);
                    openHandler = handler;
                    openFurnacePos = pos;
                    recordFromHandler(handler, pos, client.world.getRegistryKey());

                } else {
                    // Screen closed
                    openHandler = null;
                    openFurnacePos = null;
                }
            }

            // --- Update open furnace every tick while screen is open ---
            if (openHandler != null && openFurnacePos != null && currentScreen instanceof AbstractFurnaceScreen<?>) {
                recordFromHandler(openHandler, openFurnacePos, client.world.getRegistryKey());
            }

            // --- Periodic update for all tracked furnaces (not currently open) ---
            tickCounter++;
            if (tickCounter < TICK_INTERVAL) return;
            tickCounter = 0;

            ClientWorld world = client.world;
            RegistryKey<World> dimension = world.getRegistryKey();

            for (Map.Entry<BlockPos, FurnaceRecord> entry : worldData.getAll().entrySet()) {
                BlockPos pos = entry.getKey();
                FurnaceRecord oldRecord = entry.getValue();

                if (!oldRecord.dimension.equals(dimension)) continue;
                // Skip currently open furnace — already updated above
                if (pos.equals(openFurnacePos)) continue;

                if (!(world.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnaceBE)) {
                    continue;
                }

                FurnaceState oldState = oldRecord.state;
                recordFromBlockEntity(furnaceBE, pos, dimension);
                checkDoneTransition(pos, oldState);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Record from handler (screen open — most accurate, uses public API)
    // -------------------------------------------------------------------------

    /**
     * Reads furnace state from AbstractFurnaceScreenHandler public methods.
     * getCookProgress(), getFuelProgress(), isBurning() are all public and
     * work correctly in production without reflection or field access.
     */
    private static void recordFromHandler(
            AbstractFurnaceScreenHandler handler,
            BlockPos pos,
            RegistryKey<World> dimension
    ) {
        float cookProgress = handler.getCookProgress();  // 0.0 - 1.0
        float fuelProgress = handler.getFuelProgress();  // 0.0 - 1.0
        boolean burning    = handler.isBurning();

        // Derive tick values from progress ratios
        int cookTimeTotal = DEFAULT_COOK_TIME_TOTAL;
        int cookTime      = Math.round(cookProgress * cookTimeTotal);
        int burnTime      = burning ? Math.round(fuelProgress * 200) : 0;

        // Read item stacks from output slot to detect DONE
        ItemStack outputSlotStack = handler.getOutputSlot().getStack();
        ItemStack inputStack      = handler.slots.get(0).getStack();

        @Nullable Identifier inputItem = inputStack.isEmpty()
                ? null
                : inputStack.getItem().getRegistryEntry().registryKey().getValue();

        FurnaceState state = computeState(inputStack, outputSlotStack, burnTime, cookTime, cookTimeTotal);

        FurnaceBoardLogger.info("Handler state: cookProgress=" + cookProgress
                + " burning=" + burning + " state=" + state);

        FurnaceState oldState = worldData.get(pos) != null ? worldData.get(pos).state : null;

        FurnaceRecord record = new FurnaceRecord(
                pos, dimension, inputItem, inputStack.getCount(),
                cookTimeTotal, cookTime, burnTime, state,
                System.currentTimeMillis()
        );
        worldData.put(pos, record);
        worldData.save();

        if (state == FurnaceState.DONE && oldState != FurnaceState.DONE) {
            if (!notifiedDone.contains(pos)) {
                notifiedDone.add(pos);
                FurnaceNotifier.onFurnaceDone(record);
                FurnaceBoardLogger.info("Furnace DONE at " + pos);
            }
        } else if (state != FurnaceState.DONE) {
            notifiedDone.remove(pos);
        }
    }

    // -------------------------------------------------------------------------
    // Record from block entity (background tick — furnace not open)
    // -------------------------------------------------------------------------

    private static void recordFromBlockEntity(
            AbstractFurnaceBlockEntity furnaceBE,
            BlockPos pos,
            RegistryKey<World> dimension
    ) {
        // Without PropertyDelegate working, we can only check basic state
        // via item stacks — which are accessible
        ItemStack inputStack  = furnaceBE.getStack(0);
        ItemStack fuelStack   = furnaceBE.getStack(1);
        ItemStack outputStack = furnaceBE.getStack(2);

        // Use existing record's progress if available (preserve last known values)
        FurnaceRecord existing = worldData.get(pos);
        int cookTime      = existing != null ? existing.cookTime : 0;
        int cookTimeTotal = existing != null ? existing.cookTimeTotal : DEFAULT_COOK_TIME_TOTAL;
        int burnTime      = existing != null ? existing.burnTime : 0;

        @Nullable Identifier inputItem = inputStack.isEmpty()
                ? null
                : inputStack.getItem().getRegistryEntry().registryKey().getValue();

        FurnaceState state = computeState(inputStack, outputStack, burnTime, cookTime, cookTimeTotal);

        FurnaceRecord record = new FurnaceRecord(
                pos, dimension, inputItem, inputStack.getCount(),
                cookTimeTotal, cookTime, burnTime, state,
                System.currentTimeMillis()
        );
        worldData.put(pos, record);
    }

    private static void checkDoneTransition(BlockPos pos, FurnaceState oldState) {
        FurnaceRecord newRecord = worldData.get(pos);
        if (newRecord == null) return;
        if (newRecord.state == FurnaceState.DONE && oldState != FurnaceState.DONE) {
            if (!notifiedDone.contains(pos)) {
                notifiedDone.add(pos);
                FurnaceNotifier.onFurnaceDone(newRecord);
                FurnaceBoardLogger.info("Furnace DONE at " + pos);
            }
        } else if (newRecord.state != FurnaceState.DONE) {
            notifiedDone.remove(pos);
        }
    }

    // -------------------------------------------------------------------------
    // World scan — find furnace BlockPos near player
    // -------------------------------------------------------------------------

    @Nullable
    private static BlockPos findFurnacePos(ClientWorld world, BlockPos playerPos) {
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    BlockPos candidate = playerPos.add(dx, dy, dz);
                    if (world.getBlockEntity(candidate) instanceof AbstractFurnaceBlockEntity) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // State computation
    // -------------------------------------------------------------------------

    private static FurnaceState computeState(
            ItemStack input, ItemStack output,
            int burnTime, int cookTime, int cookTimeTotal
    ) {
        if (input.isEmpty() && !output.isEmpty()) return FurnaceState.DONE; // nothing left to smelt
        if (input.isEmpty())   return FurnaceState.EMPTY;
        if (burnTime > 0)      return FurnaceState.SMELTING;
        return FurnaceState.NO_FUEL;
    }
}