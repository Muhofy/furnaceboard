package dev.muhofy.furnaceboard.mixin;

import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the protected 'world' field of AbstractFurnaceScreenHandler.
 *
 * Verified in Yarn 1.21.11+build.4:
 *   named: world
 *   intermediary: field_7822
 *   type: protected final World
 *
 * We use this to find the furnace BlockPos by scanning the world
 * around the player, since the inventory cast fails in production
 * due to intermediary remapping.
 */
@Mixin(AbstractFurnaceScreenHandler.class)
public interface AbstractFurnaceScreenHandlerWorldAccessor {

    @Accessor("world")
    World getWorld();
}