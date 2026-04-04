package dev.muhofy.furnaceboard.notification;

import dev.muhofy.furnaceboard.data.FurnaceRecord;
import dev.muhofy.furnaceboard.util.FurnaceBoardLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Handles player notifications when a furnace transitions to DONE state.
 *
 * Notification types:
 *   - Vanilla SystemToast (title: item name, body: position)
 *   - Sound effect: SoundEvents.BLOCK_NOTE_BLOCK_PLING
 *
 * Fires exactly once per DONE transition — deduplication handled by FurnaceTrackerManager.
 * All methods must be called from the client thread.
 */
public final class FurnaceNotifier {

    /**
     * Toast type — NARRATOR_TOGGLE is the least intrusive reusable vanilla type.
     * Verified: SystemToast.Type exists in Yarn 1.21.11+build.4 (replaced SystemToast.Id)
     */
    private static final SystemToast.Type TOAST_TYPE = SystemToast.Type.NARRATOR_TOGGLE;

    private FurnaceNotifier() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Called by FurnaceTrackerManager when a furnace transitions to DONE.
     * Shows a toast and plays a sound.
     *
     * @param record the furnace record that just completed
     */
    public static void onFurnaceDone(FurnaceRecord record) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        showToast(client, record);
        playSound(client);

        FurnaceBoardLogger.info("Notification fired for furnace at " + record.pos);
    }

    // -------------------------------------------------------------------------
    // Toast
    // -------------------------------------------------------------------------

    private static void showToast(MinecraftClient client, FurnaceRecord record) {
        Text title = buildToastTitle(record);
        Text body  = buildToastBody(record.pos);

        SystemToast.show(client.getToastManager(), TOAST_TYPE, title, body);
    }

    private static Text buildToastTitle(FurnaceRecord record) {
        if (record.inputItem != null) {
            return Text.translatable("toast.furnaceboard.done.title",
                    Text.translatable("item." + record.inputItem.getNamespace()
                            + "." + record.inputItem.getPath()));
        }
        return Text.translatable("toast.furnaceboard.done.title.unknown");
    }

    private static Text buildToastBody(BlockPos pos) {
        return Text.translatable("toast.furnaceboard.done.body",
                pos.getX(), pos.getY(), pos.getZ());
    }

    // -------------------------------------------------------------------------
    // Sound
    // -------------------------------------------------------------------------

    /**
     * Plays a pling sound at master volume (not positional).
     * Uses master(SoundEvent, float pitch, float volume) overload —
     * verified present in Yarn 1.21+build.9 and nearby versions.
     * // UNTESTED at runtime — verify sound plays in game
     */
    private static void playSound(MinecraftClient client) {
        if (client.player == null) return;

        client.getSoundManager().play(
                // master() removed in 1.21.11 — use ui() instead
                // SoundEvents fields return RegistryEntry<SoundEvent>, .value() extracts SoundEvent
                PositionedSoundInstance.ui(
                        SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                        1.0f,  // pitch
                        1.0f   // volume — Phase 6: replace with config value
                )
        );
    }
}