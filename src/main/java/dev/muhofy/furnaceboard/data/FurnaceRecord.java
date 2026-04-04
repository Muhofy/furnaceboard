package dev.muhofy.furnaceboard.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable snapshot of a single tracked furnace.
 * Serializable to/from NBT for persistence in furnaceboard.dat.
 *
 * All field names match the FurnaceRecord contract in SYSINSTRUCTIONS.
 * Yarn field name references (cookTime, cookTimeTotal, burnTime) verified
 * against Yarn 1.21.11+build.4 AbstractFurnaceBlockEntity javadoc.
 */
public final class FurnaceRecord {

    // NBT keys — centralized here to avoid typos
    private static final String NBT_POS            = "Pos";
    private static final String NBT_DIMENSION      = "Dimension";
    private static final String NBT_INPUT_ITEM     = "InputItem";
    private static final String NBT_INPUT_COUNT    = "InputCount";
    private static final String NBT_COOK_TIME      = "CookTime";
    private static final String NBT_COOK_TIME_TOTAL = "CookTimeTotal";
    private static final String NBT_BURN_TIME      = "BurnTime";
    private static final String NBT_STATE          = "State";
    private static final String NBT_LAST_UPDATED   = "LastUpdated";

    /** World position of the furnace block. */
    public final BlockPos pos;

    /** Which dimension this furnace is in (overworld / nether / end). */
    public final RegistryKey<World> dimension;

    /** Item currently being smelted. Null if furnace input slot is empty. */
    @Nullable
    public final Identifier inputItem;

    /** Stack size of the input item. */
    public final int inputCount;

    /**
     * Total ticks required to complete the current recipe.
     * Sourced from AbstractFurnaceBlockEntity.cookTimeTotal (Yarn 1.21.11+build.4).
     */
    public final int cookTimeTotal;

    /**
     * Ticks elapsed on the current item being smelted.
     * Sourced from AbstractFurnaceBlockEntity.cookTime (Yarn 1.21.11+build.4).
     */
    public final int cookTime;

    /**
     * Fuel ticks remaining.
     * Sourced from AbstractFurnaceBlockEntity.burnTime (Yarn 1.21.11+build.4).
     */
    public final int burnTime;

    /** Current operational state of this furnace. */
    public final FurnaceState state;

    /**
     * Wall-clock timestamp of the last update (System.currentTimeMillis()).
     * Used for stale data pruning (default: remove after 3 real days).
     */
    public final long lastUpdated;

    public FurnaceRecord(
            BlockPos pos,
            RegistryKey<World> dimension,
            @Nullable Identifier inputItem,
            int inputCount,
            int cookTimeTotal,
            int cookTime,
            int burnTime,
            FurnaceState state,
            long lastUpdated
    ) {
        this.pos = pos;
        this.dimension = dimension;
        this.inputItem = inputItem;
        this.inputCount = inputCount;
        this.cookTimeTotal = cookTimeTotal;
        this.cookTime = cookTime;
        this.burnTime = burnTime;
        this.state = state;
        this.lastUpdated = lastUpdated;
    }

    /**
     * Returns ETA in seconds until smelting is complete.
     * Returns 0 if not smelting or already done.
     * Formula: (cookTimeTotal - cookTime) / 20 ticks-per-second.
     */
    public int getEtaSeconds() {
        if (state != FurnaceState.SMELTING || cookTimeTotal <= 0) return 0;
        int ticksRemaining = cookTimeTotal - cookTime;
        return Math.max(0, ticksRemaining / 20);
    }

    // -------------------------------------------------------------------------
    // NBT serialization
    // -------------------------------------------------------------------------

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putLong(NBT_POS, pos.asLong());
        nbt.putString(NBT_DIMENSION, dimension.getValue().toString());
        if (inputItem != null) {
            nbt.putString(NBT_INPUT_ITEM, inputItem.toString());
        }
        nbt.putInt(NBT_INPUT_COUNT, inputCount);
        nbt.putInt(NBT_COOK_TIME, cookTime);
        nbt.putInt(NBT_COOK_TIME_TOTAL, cookTimeTotal);
        nbt.putInt(NBT_BURN_TIME, burnTime);
        nbt.putString(NBT_STATE, state.name());
        nbt.putLong(NBT_LAST_UPDATED, lastUpdated);
        return nbt;
    }

    /**
     * Deserializes a FurnaceRecord from NBT.
     * Returns null if NBT is malformed or missing required fields.
     */
    @Nullable
    public static FurnaceRecord fromNbt(NbtCompound nbt) {
        try {
            // MC 1.21.11: NbtCompound getters return Optional — use orElse for safe defaults
            BlockPos pos = BlockPos.fromLong(nbt.getLong(NBT_POS).orElse(0L));

            Identifier dimId = Identifier.of(nbt.getString(NBT_DIMENSION).orElse("minecraft:overworld"));
            RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, dimId);

            Identifier inputItem = null;
            if (nbt.contains(NBT_INPUT_ITEM)) {
                inputItem = Identifier.of(nbt.getString(NBT_INPUT_ITEM).orElse(""));
            }

            int inputCount    = nbt.getInt(NBT_INPUT_COUNT).orElse(0);
            int cookTime      = nbt.getInt(NBT_COOK_TIME).orElse(0);
            int cookTimeTotal = nbt.getInt(NBT_COOK_TIME_TOTAL).orElse(0);
            int burnTime      = nbt.getInt(NBT_BURN_TIME).orElse(0);

            FurnaceState state;
            try {
                state = FurnaceState.valueOf(nbt.getString(NBT_STATE).orElse("EMPTY"));
            } catch (IllegalArgumentException e) {
                state = FurnaceState.EMPTY; // safe fallback for unknown/corrupt state
            }

            long lastUpdated = nbt.getLong(NBT_LAST_UPDATED).orElse(0L);

            return new FurnaceRecord(
                    pos, dimension, inputItem, inputCount,
                    cookTimeTotal, cookTime, burnTime, state, lastUpdated
            );
        } catch (Exception e) {
            // Do not crash on corrupt NBT — return null and let caller handle it
            return null;
        }
    }

    @Override
    public String toString() {
        return "FurnaceRecord{pos=" + pos + ", state=" + state
                + ", item=" + inputItem + ", eta=" + getEtaSeconds() + "s}";
    }
}