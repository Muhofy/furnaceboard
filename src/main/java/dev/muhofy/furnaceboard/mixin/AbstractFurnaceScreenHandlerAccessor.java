package dev.muhofy.furnaceboard.mixin;

import net.minecraft.inventory.Inventory;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the package-private 'inventory' field of AbstractFurnaceScreenHandler.
 * This is the backing inventory — on the client side, it is an instance of
 * AbstractFurnaceBlockEntity when the player opens a furnace in the world.
 *
 * We use this to retrieve the BlockPos of the furnace without needing a Mixin inject.
 * Accessor Mixins are low-risk and do not alter any game logic.
 *
 * Verified: 'inventory' is package-private in Yarn 1.21.11+build.4
 * AbstractFurnaceScreenHandler.inventory field mapping:
 *   named: inventory
 *   intermediary: field_7824
 */
@Mixin(AbstractFurnaceScreenHandler.class)
public interface AbstractFurnaceScreenHandlerAccessor {

    @Accessor("inventory")
    Inventory getInventory();
}