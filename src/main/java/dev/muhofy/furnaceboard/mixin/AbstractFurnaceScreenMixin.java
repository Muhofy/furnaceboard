package dev.muhofy.furnaceboard.mixin;

import dev.muhofy.furnaceboard.data.FurnaceBoardWorldData;
import dev.muhofy.furnaceboard.data.FurnaceRecord;
import dev.muhofy.furnaceboard.data.FurnaceState;
import dev.muhofy.furnaceboard.notification.FurnaceNotifier;
import dev.muhofy.furnaceboard.tracker.FurnaceTrackerManager;
import dev.muhofy.furnaceboard.util.FurnaceBoardLogger;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.client.gui.screen.ingame.AbstractFurnaceScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into AbstractFurnaceScreen.handledScreenTick() to read furnace state
 * every screen tick — client-side only, no reflection, no casting issues.
 *
 * Uses getCookProgress(), getFuelProgress(), isBurning() public API from handler,
 * and finds BlockPos via world scan around player (only once on open).
 */
@Mixin(AbstractFurnaceScreen.class)
public abstract class AbstractFurnaceScreenMixin<T extends AbstractFurnaceScreenHandler>
        extends HandledScreen<T> {

    protected AbstractFurnaceScreenMixin() {
        super(null, null, null);
    }

    @Shadow
    protected T handler;

    /** Cached BlockPos — found once on screen open, reused every tick. */
    private BlockPos furnaceboard$cachedPos = null;

    @Inject(method = "init", at = @At("RETURN"))
    private void furnaceboard$onInit(CallbackInfo ci) {
        // Find furnace pos when screen opens
        furnaceboard$cachedPos = findFurnacePos();
        if (furnaceboard$cachedPos != null) {
            FurnaceBoardLogger.info("Screen Mixin: furnace found at " + furnaceboard$cachedPos);
            // Clear exclusion if player reopened — auto-track again
            FurnaceTrackerManager.clearExclusion(furnaceboard$cachedPos);
        }
    }

    @Inject(method = "handledScreenTick", at = @At("RETURN"))
    private void furnaceboard$onTick(CallbackInfo ci) {
        if (furnaceboard$cachedPos == null) return;
        if (FurnaceTrackerManager.isExcluded(furnaceboard$cachedPos)) return;
        if (client == null || client.world == null) return;

        T handler = this.handler;
        float cookProgress = handler.getCookProgress();
        boolean burning    = handler.isBurning();

        int cookTimeTotal = 200; // DEFAULT_COOK_TIME
        int cookTime      = Math.round(cookProgress * cookTimeTotal);
        int burnTime      = burning ? 1 : 0; // we only need burning state

        ItemStack inputStack  = handler.slots.get(0).getStack();
        ItemStack outputStack = handler.getOutputSlot().getStack();

        @Nullable Identifier inputItem = inputStack.isEmpty()
                ? null
                : inputStack.getItem().getRegistryEntry().registryKey().getValue();

        FurnaceState state = computeState(inputStack, outputStack, burnTime);
        RegistryKey<World> dimension = client.world.getRegistryKey();

        FurnaceBoardWorldData worldData = FurnaceTrackerManager.getWorldData();
        FurnaceState oldState = worldData.get(furnaceboard$cachedPos) != null
                ? worldData.get(furnaceboard$cachedPos).state : null;

        FurnaceRecord record = new FurnaceRecord(
                furnaceboard$cachedPos, dimension, inputItem, inputStack.getCount(),
                cookTimeTotal, cookTime, burnTime, state,
                System.currentTimeMillis()
        );
        worldData.put(furnaceboard$cachedPos, record);

        if (state == FurnaceState.DONE && oldState != FurnaceState.DONE) {
            if (!FurnaceTrackerManager.isNotified(furnaceboard$cachedPos)) {
                FurnaceTrackerManager.markNotified(furnaceboard$cachedPos);
                FurnaceNotifier.onFurnaceDone(record);
            }
        } else if (state != FurnaceState.DONE) {
            FurnaceTrackerManager.clearNotified(furnaceboard$cachedPos);
        }
    }

    @Nullable
    private BlockPos findFurnacePos() {
        if (client == null || client.world == null || client.player == null) return null;
        ClientWorld world = client.world;
        BlockPos playerPos = client.player.getBlockPos();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos c = playerPos.add(dx, dy, dz);
                    if (world.getBlockEntity(c) instanceof AbstractFurnaceBlockEntity) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    private static FurnaceState computeState(ItemStack input, ItemStack output, int burnTime) {
        if (input.isEmpty() && !output.isEmpty()) return FurnaceState.DONE;
        if (input.isEmpty()) return FurnaceState.EMPTY;
        if (burnTime > 0)    return FurnaceState.SMELTING;
        return FurnaceState.NO_FUEL;
    }
}