package dev.muhofy.furnaceboard.tracker;

import dev.muhofy.furnaceboard.data.FurnaceBoardWorldData;
import dev.muhofy.furnaceboard.data.FurnaceRecord;
import dev.muhofy.furnaceboard.data.FurnaceState;
import dev.muhofy.furnaceboard.mixin.AbstractFurnaceBlockEntityAccessor;
import dev.muhofy.furnaceboard.mixin.AbstractFurnaceScreenHandlerAccessor;
import dev.muhofy.furnaceboard.mixin.AbstractFurnaceScreenHandlerWorldAccessor;
import dev.muhofy.furnaceboard.notification.FurnaceNotifier;
import dev.muhofy.furnaceboard.util.FurnaceBoardLogger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractFurnaceScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.world.World;
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
 *   - Detects furnace screen open via tick-based currentScreen check
 *     (ScreenEvents.AFTER_INIT was not firing in 1.21.11 — replaced with tick polling)
 *   - Every 20 ticks: recalculate ETA for all tracked furnaces
 *   - Detect DONE transitions → fire FurnaceNotifier
 *   - Persist changes to FurnaceBoardWorldData
 *
 * All logic runs on the client thread only.
 */
public final class FurnaceTrackerManager {

    private static final int TICK_INTERVAL = 20;

    private static final FurnaceBoardWorldData worldData = new FurnaceBoardWorldData();
    private static int tickCounter = 0;
    private static final Set<BlockPos> notifiedDone = new HashSet<>();

    /** Used to detect screen change each tick. */
    private static Screen lastScreen = null;

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

    // -------------------------------------------------------------------------
    // World lifecycle
    // -------------------------------------------------------------------------

    public static void onWorldJoin(java.nio.file.Path worldSaveDir, int staleDays) {
        worldData.init(worldSaveDir, staleDays);
        notifiedDone.clear();
        FurnaceBoardLogger.info("World data loaded. Tracking " + worldData.size() + " furnace(s).");
    }

    public static void onWorldLeave() {
        worldData.save();
        notifiedDone.clear();
        FurnaceBoardLogger.info("World data saved on world leave.");
    }

    // -------------------------------------------------------------------------
    // Tick event
    // -------------------------------------------------------------------------

    private static void registerTickEvent() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;

            // --- Screen change detection (replaces ScreenEvents.AFTER_INIT) ---
            Screen currentScreen = client.currentScreen;
            if (currentScreen != lastScreen) {
                lastScreen = currentScreen;
                if (currentScreen instanceof AbstractFurnaceScreen<?> furnaceScreen) {
                    FurnaceBoardLogger.info("Furnace screen detected!");
                    AbstractFurnaceScreenHandler handler =
                            (AbstractFurnaceScreenHandler) furnaceScreen.getScreenHandler();

                    // Get world from handler via Mixin accessor — works in production
                    World handlerWorld = ((AbstractFurnaceScreenHandlerWorldAccessor) handler).getWorld();
                    if (handlerWorld == null || client.player == null) return;

                    // Scan blocks around the player to find the furnace block entity
                    // Player must be within 5 blocks of the furnace to open it
                    BlockPos playerPos = client.player.getBlockPos();
                    BlockPos foundPos = null;

                    outer:
                    for (int dx = -5; dx <= 5; dx++) {
                        for (int dy = -5; dy <= 5; dy++) {
                            for (int dz = -5; dz <= 5; dz++) {
                                BlockPos candidate = playerPos.add(dx, dy, dz);
                                if (handlerWorld.getBlockEntity(candidate) instanceof AbstractFurnaceBlockEntity) {
                                    foundPos = candidate;
                                    break outer;
                                }
                            }
                        }
                    }

                    if (foundPos == null) {
                        FurnaceBoardLogger.info("Could not find furnace block entity near player.");
                        return;
                    }

                    AbstractFurnaceBlockEntity furnaceBE =
                            (AbstractFurnaceBlockEntity) handlerWorld.getBlockEntity(foundPos);
                    if (furnaceBE == null) return;

                    FurnaceBoardLogger.info("Found furnace at " + foundPos);
                    recordFurnace(furnaceBE, foundPos, client.world.getRegistryKey());
                }
            }

            // --- Periodic ETA recalculation ---
            tickCounter++;
            if (tickCounter < TICK_INTERVAL) return;
            tickCounter = 0;

            ClientWorld world = client.world;
            RegistryKey<World> dimension = world.getRegistryKey();

            for (Map.Entry<BlockPos, FurnaceRecord> entry : worldData.getAll().entrySet()) {
                BlockPos pos = entry.getKey();
                FurnaceRecord oldRecord = entry.getValue();

                if (!oldRecord.dimension.equals(dimension)) continue;

                if (!(world.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnaceBE)) {
                    continue;
                }

                FurnaceState oldState = oldRecord.state;
                recordFurnace(furnaceBE, pos, dimension);
                FurnaceRecord newRecord = worldData.get(pos);
                if (newRecord == null) continue;

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
        });
    }

    // -------------------------------------------------------------------------
    // Record creation
    // -------------------------------------------------------------------------

    private static void recordFurnace(
            AbstractFurnaceBlockEntity furnaceBE,
            BlockPos pos,
            RegistryKey<World> dimension
    ) {
        PropertyDelegate props = ((AbstractFurnaceBlockEntityAccessor) furnaceBE).getPropertyDelegate();
        int burnTime      = props.get(AbstractFurnaceBlockEntity.BURN_TIME_PROPERTY_INDEX);
        int cookTime      = props.get(AbstractFurnaceBlockEntity.COOK_TIME_PROPERTY_INDEX);
        int cookTimeTotal = props.get(AbstractFurnaceBlockEntity.COOK_TIME_TOTAL_PROPERTY_INDEX);

        ItemStack inputStack  = furnaceBE.getStack(0);
        ItemStack outputStack = furnaceBE.getStack(2);

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
        worldData.save();
        FurnaceBoardLogger.info("Recorded furnace at " + pos + " state=" + state);
    }

    private static FurnaceState computeState(
            ItemStack input, ItemStack output,
            int burnTime, int cookTime, int cookTimeTotal
    ) {
        if (!output.isEmpty()) return FurnaceState.DONE;
        if (input.isEmpty())   return FurnaceState.EMPTY;
        if (burnTime > 0)      return FurnaceState.SMELTING;
        return FurnaceState.NO_FUEL;
    }
}