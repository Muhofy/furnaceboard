package dev.muhofy.furnaceboard.mixin;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.screen.PropertyDelegate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the protected 'propertyDelegate' field of AbstractFurnaceBlockEntity.
 *
 * PropertyDelegate is synced to the client via the screen handler.
 * Indices (verified in Yarn 1.21.11+build.4 via PROPERTY_INDEX constants):
 *   0 = litTimeRemaining  (was: burnTime)
 *   1 = litTotalTime      (was: fuelTime)
 *   2 = cookingTimeSpent  (was: cookTime)
 *   3 = cookingTotalTime  (was: cookTimeTotal)
 *
 * Field mapping:
 *   named: propertyDelegate
 *   intermediary: field_17286 (AbstractFurnaceBlockEntity)
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityAccessor {

    @Accessor("propertyDelegate")
    PropertyDelegate getPropertyDelegate();
}