package dev.muhofy.furnaceboard.data;

import dev.muhofy.furnaceboard.util.FurnaceBoardLogger;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Root data container for all tracked furnaces in the current world session.
 * Persists data to: .minecraft/saves/<world>/furnaceboard.dat
 *
 * Lifecycle:
 *   - Loaded when a world is joined (FurnaceTrackerManager calls load())
 *   - Saved whenever a record is updated (save())
 *   - Stale records (not updated in STALE_DAYS) are pruned on load and periodically
 *
 * Thread safety: all access must be from the client thread. No concurrent access assumed.
 */
public final class FurnaceBoardWorldData {

    /** Default number of real-world days before a record is considered stale and pruned. */
    public static final int DEFAULT_STALE_DAYS = 3;

    private static final String NBT_ROOT_KEY   = "FurnaceBoard";
    private static final String NBT_RECORDS    = "Records";
    private static final String NBT_POS_KEY    = "Pos";
    private static final String FILE_NAME      = "furnaceboard.dat";

    private final Map<BlockPos, FurnaceRecord> records = new HashMap<>();
    private Path saveFile;
    private int staleDays = DEFAULT_STALE_DAYS;

    /**
     * Initializes the world data for the given world save directory.
     * Call this when the player joins a world.
     *
     * @param worldSaveDir path to .minecraft/saves/<world>/
     * @param staleDays    records older than this many days are pruned
     */
    public void init(Path worldSaveDir, int staleDays) {
        this.saveFile = worldSaveDir.resolve(FILE_NAME);
        this.staleDays = staleDays;
        load();
    }

    // -------------------------------------------------------------------------
    // Record access
    // -------------------------------------------------------------------------

    public void put(BlockPos pos, FurnaceRecord record) {
        records.put(pos, record);
    }

    @Nullable
    public FurnaceRecord get(BlockPos pos) {
        return records.get(pos);
    }

    public void remove(BlockPos pos) {
        records.remove(pos);
    }

    /** Returns an unmodifiable view of all tracked records. */
    public Map<BlockPos, FurnaceRecord> getAll() {
        return Collections.unmodifiableMap(records);
    }

    public int size() {
        return records.size();
    }

    // -------------------------------------------------------------------------
    // Stale pruning
    // -------------------------------------------------------------------------

    /**
     * Removes records not updated within staleDays real-world days.
     * Safe to call periodically (e.g. on world load, or hourly tick).
     */
    public void pruneStale() {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(staleDays);
        int before = records.size();
        Iterator<Map.Entry<BlockPos, FurnaceRecord>> it = records.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().lastUpdated < cutoff) {
                it.remove();
            }
        }
        int pruned = before - records.size();
        if (pruned > 0) {
            FurnaceBoardLogger.info("Pruned " + pruned + " stale furnace record(s).");
        }
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    /**
     * Loads furnace records from furnaceboard.dat.
     * Silently skips corrupt records. Prunes stale records after loading.
     * No-op if the file does not exist yet.
     */
    public void load() {
        if (saveFile == null || !Files.exists(saveFile)) {
            FurnaceBoardLogger.info("No furnaceboard.dat found — starting fresh.");
            return;
        }

        records.clear();

        try {
            // MC 1.21.11: NbtIo.readCompressed takes Path + NbtSizeTracker
            // NbtSizeTracker.ofBytes() was removed — use NbtSizeTracker.UNLIMITED or create unlimitedTracker
            // UNTESTED — verify NbtSizeTracker API in 1.21.11
            NbtCompound root = NbtIo.readCompressed(saveFile, NbtSizeTracker.ofUnlimitedBytes());
            // MC 1.21.11: getCompound() returns Optional<NbtCompound>
            NbtCompound board = root.getCompound(NBT_ROOT_KEY).orElseGet(NbtCompound::new);
            NbtCompound recordsNbt = board.getCompound(NBT_RECORDS).orElseGet(NbtCompound::new);

            for (String key : recordsNbt.getKeys()) {
                NbtCompound entry = recordsNbt.getCompound(key).orElseGet(NbtCompound::new);
                FurnaceRecord record = FurnaceRecord.fromNbt(entry);
                if (record != null) {
                    records.put(record.pos, record);
                } else {
                    FurnaceBoardLogger.warn("Skipped corrupt furnace record at key: " + key);
                }
            }

            FurnaceBoardLogger.info("Loaded " + records.size() + " furnace record(s) from disk.");
            pruneStale();

        } catch (IOException e) {
            FurnaceBoardLogger.error("Failed to read furnaceboard.dat — data may be lost.", e);
        }
    }

    /**
     * Saves all furnace records to furnaceboard.dat.
     * Silently handles IO errors (logs them).
     */
    public void save() {
        if (saveFile == null) {
            FurnaceBoardLogger.warn("save() called before init() — skipping.");
            return;
        }

        try {
            Files.createDirectories(saveFile.getParent());

            NbtCompound recordsNbt = new NbtCompound();
            int idx = 0;
            for (Map.Entry<BlockPos, FurnaceRecord> entry : records.entrySet()) {
                NbtCompound recordNbt = entry.getValue().toNbt();
                recordNbt.putLong(NBT_POS_KEY, entry.getKey().asLong());
                recordsNbt.put(String.valueOf(idx++), recordNbt);
            }

            NbtCompound board = new NbtCompound();
            board.put(NBT_RECORDS, recordsNbt);

            NbtCompound root = new NbtCompound();
            root.put(NBT_ROOT_KEY, board);

            // UNTESTED — verify before use
            NbtIo.writeCompressed(root, saveFile);
            FurnaceBoardLogger.debug("Saved " + records.size() + " furnace record(s) to disk.");

        } catch (IOException e) {
            FurnaceBoardLogger.error("Failed to write furnaceboard.dat.", e);
        }
    }
}