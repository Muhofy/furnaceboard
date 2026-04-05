package dev.muhofy.furnaceboard.ui;

import dev.muhofy.furnaceboard.data.FurnaceRecord;
import dev.muhofy.furnaceboard.data.FurnaceState;
import dev.muhofy.furnaceboard.tracker.FurnaceTrackerManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
//import net.minecraft.client.DeltaTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * Compact HUD overlay — bottom-right corner, semi-transparent background.
 */
public final class FurnaceBoardHudWidget {

    private static final Identifier HUD_ID = Identifier.of("furnaceboard", "hud");

    private static final int PADDING     = 5;
    private static final int LINE_HEIGHT = 11;
    private static final int BG_COLOR    = 0x88000000; // semi-transparent black

    private static final int COLOR_WHITE    = 0xFFFFFFFF;
    private static final int COLOR_SMELTING = 0xFF55FF55;
    private static final int COLOR_DONE     = 0xFFFFAA00;
    private static final int COLOR_NO_FUEL  = 0xFFFF5555;

    private static boolean visible = true;

    private FurnaceBoardHudWidget() {}

    public static void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                HUD_ID,
                (context, tickCounter) -> render(context)
        );
    }

    public static void toggle() { visible = !visible; }
    public static boolean isVisible() { return visible; }
    public static void setVisible(boolean v) { visible = v; }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        if (client.options.hudHidden || !visible) return;
        if (client.currentScreen != null) return;

        Map<BlockPos, FurnaceRecord> all = FurnaceTrackerManager.getWorldData().getAll();
        if (all.isEmpty()) return;

        Collection<FurnaceRecord> records = all.values();

        int doneCount   = (int) records.stream().filter(r -> r.state == FurnaceState.DONE).count();
        int noFuelCount = (int) records.stream().filter(r -> r.state == FurnaceState.NO_FUEL).count();
        int smeltCount  = (int) records.stream().filter(r -> r.state == FurnaceState.SMELTING).count();

        int headerColor;
        if (doneCount > 0)        headerColor = COLOR_DONE;
        else if (noFuelCount > 0) headerColor = COLOR_NO_FUEL;
        else if (smeltCount > 0)  headerColor = COLOR_SMELTING;
        else                      headerColor = COLOR_WHITE;

        // Build lines
        String line1 = "\uD83D\uDD25 " + records.size() + " furnace" + (records.size() != 1 ? "s" : "");
        String line2 = null;

        Optional<FurnaceRecord> nextSmelting = records.stream()
                .filter(r -> r.state == FurnaceState.SMELTING && r.getEtaSeconds() > 0)
                .min(Comparator.comparingInt(FurnaceRecord::getEtaSeconds));

        if (doneCount > 0) {
            line2 = "\u2705 " + doneCount + " done!";
        } else if (nextSmelting.isPresent()) {
            line2 = "\u23F1 Next: " + formatEta(nextSmelting.get().getEtaSeconds());
        } else if (noFuelCount > 0) {
            line2 = "\u274C " + noFuelCount + " no fuel";
        }

        int lineCount = line2 != null ? 2 : 1;
        int boxW = 90;
        int boxH = PADDING * 2 + LINE_HEIGHT * lineCount;

        // Bottom-right position with margin
        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();
        int boxX = screenW - boxW - 8;
        int boxY = screenH - boxH - 48; // above hotbar

        // Background
        context.fill(boxX - PADDING, boxY - PADDING, boxX + boxW, boxY + boxH, BG_COLOR);

        // Text
        context.drawTextWithShadow(client.textRenderer, line1, boxX, boxY, headerColor);
        if (line2 != null) {
            int line2color = doneCount > 0 ? COLOR_DONE : noFuelCount > 0 ? COLOR_NO_FUEL : COLOR_WHITE;
            context.drawTextWithShadow(client.textRenderer, line2, boxX, boxY + LINE_HEIGHT, line2color);
        }
    }

    public static String formatEta(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}