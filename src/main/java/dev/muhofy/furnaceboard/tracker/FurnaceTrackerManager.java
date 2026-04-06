package dev.muhofy.furnaceboard.tracker;

import dev.muhofy.furnaceboard.data.FurnaceBoardWorldData;
import dev.muhofy.furnaceboard.data.FurnaceRecord;
import dev.muhofy.furnaceboard.data.FurnaceState;
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

public final class FurnaceTrackerManager {

    private static final FurnaceBoardWorldData worldData = new FurnaceBoardWorldData();
    private static final Set<BlockPos> excluded = new HashSet<>();
    private static final Set<BlockPos> notified = new HashSet<>();

    private static Screen lastScreen = null;

    @Nullable private static AbstractFurnaceScreenHandler openHandler = null;
    @Nullable private static BlockPos openFurnacePos = null;

    private FurnaceTrackerManager() {}

    public static void init() {
        registerTickEvent();
        FurnaceBoardLogger.info("FurnaceTrackerManager initialized.");
    }

    public static FurnaceBoardWorldData getWorldData() { return worldData; }
    public static boolean isExcluded(BlockPos pos) { return excluded.contains(pos); }
    public static void excludeFurnace(BlockPos pos) {
        excluded.add(pos);
        worldData.remove(pos);
        worldData.save();
    }
    public static void clearExclusion(BlockPos pos) { excluded.remove(pos); }
    public static boolean isNotified(BlockPos pos) { return notified.contains(pos); }
    public static void markNotified(BlockPos pos) { notified.add(pos); }
    public static void clearNotified(BlockPos pos) { notified.remove(pos); }

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
        openHandler = null;
        openFurnacePos = null;
        FurnaceBoardLogger.info("World data saved.");
    }

    private static void registerTickEvent() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;

            // --- Screen change detection ---
            Screen current = client.currentScreen;
            if (current != lastScreen) {
                lastScreen = current;
                if (current instanceof AbstractFurnaceScreen<?> furnaceScreen) {
                    FurnaceBoardLogger.info("Furnace screen detected!");
                    BlockPos pos = findFurnacePos(client.world, client.player.getBlockPos());
                    if (pos != null) {
                        FurnaceBoardLogger.info("Furnace at " + pos);
                        clearExclusion(pos);
                        openHandler = (AbstractFurnaceScreenHandler) furnaceScreen.getScreenHandler();
                        openFurnacePos = pos;
                    }
                } else if (current == null) {
                    openHandler = null;
                    openFurnacePos = null;
                }
            }

            // --- Update open furnace every tick ---
            if (openHandler != null && openFurnacePos != null && !isExcluded(openFurnacePos)) {
                updateFromHandler(openHandler, openFurnacePos, client.world.getRegistryKey());
            }
        });
    }

    private static void updateFromHandler(
            AbstractFurnaceScreenHandler handler,
            BlockPos pos,
            RegistryKey<World> dimension
    ) {
        float cookProgress = handler.getCookProgress();
        boolean burning    = handler.isBurning();

        int cookTimeTotal = 200;
        int cookTime      = Math.round(cookProgress * cookTimeTotal);
        int burnTime      = burning ? 1 : 0;

        ItemStack inputStack  = handler.slots.get(0).getStack();
        ItemStack outputStack = handler.getOutputSlot().getStack();

        @Nullable Identifier inputItem = inputStack.isEmpty() ? null
                : inputStack.getItem().getRegistryEntry().registryKey().getValue();

        FurnaceState state = computeState(inputStack, outputStack, burnTime);
        FurnaceState oldState = worldData.get(pos) != null ? worldData.get(pos).state : null;

        FurnaceRecord record = new FurnaceRecord(
                pos, dimension, inputItem, inputStack.getCount(),
                cookTimeTotal, cookTime, burnTime, state,
                System.currentTimeMillis()
        );
        worldData.put(pos, record);
        worldData.save();

        if (state == FurnaceState.DONE && oldState != FurnaceState.DONE) {
            if (!isNotified(pos)) {
                markNotified(pos);
                FurnaceNotifier.onFurnaceDone(record);
                FurnaceBoardLogger.info("Furnace DONE at " + pos);
            }
        } else if (state != FurnaceState.DONE) {
            clearNotified(pos);
        }
    }

    @Nullable
    private static BlockPos findFurnacePos(ClientWorld world, BlockPos playerPos) {
        for (int dx = -5; dx <= 5; dx++)
            for (int dy = -5; dy <= 5; dy++)
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos c = playerPos.add(dx, dy, dz);
                    if (world.getBlockEntity(c) instanceof AbstractFurnaceBlockEntity)
                        return c;
                }
        return null;
    }

    private static FurnaceState computeState(ItemStack input, ItemStack output, int burnTime) {
        if (input.isEmpty() && !output.isEmpty()) return FurnaceState.DONE;
        if (input.isEmpty()) return FurnaceState.EMPTY;
        if (burnTime > 0)    return FurnaceState.SMELTING;
        return FurnaceState.NO_FUEL;
    }

    // Background tick için tracked furnace map
    public static Map<BlockPos, FurnaceRecord> getAll() {
        return worldData.getAll();
    }
}