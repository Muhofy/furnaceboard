package dev.muhofy.furnaceboard.ui;

import dev.muhofy.furnaceboard.data.FurnaceRecord;
import dev.muhofy.furnaceboard.data.FurnaceState;
import dev.muhofy.furnaceboard.tracker.FurnaceTrackerManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * Compact HUD overlay — always visible in the corner of the screen (toggleable).
 *
 * Layout:
 *   🔥 3 furnaces
 *   ⏱ Next: 1:24
 *
 * Colors per state (from SYSINSTRUCTIONS):
 *   Smelting : #55FF55 (green)
 *   Done     : #FFAA00 (gold)
 *   No fuel  : #FF5555 (red)
 *   Empty    : #AAAAAA (gray)
 *
 * Registered via HudElementRegistry (Fabric API 0.141.3+1.21.11).
 * HudElement.render uses Mojang names (GuiGraphics, DeltaTracker) in the interface,
 * but Loom remaps them to Yarn names (DrawContext, RenderTickCounter) at compile time.
 * We use Yarn names here — Loom handles the bridge.
 */
public final class FurnaceBoardHudWidget {

    private static final Identifier HUD_ID = Identifier.of("furnaceboard", "hud");

    // UI layout constants
    private static final int PADDING_X   = 4;
    private static final int PADDING_Y   = 4;
    private static final int LINE_HEIGHT = 10;

    // State colors (ARGB)
    private static final int COLOR_WHITE    = 0xFFFFFFFF;
    private static final int COLOR_SMELTING = 0xFF55FF55;
    private static final int COLOR_DONE     = 0xFFFFAA00;
    private static final int COLOR_NO_FUEL  = 0xFFFF5555;

    private static boolean visible = true;

    private FurnaceBoardHudWidget() {}

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers the HUD layer after MISC_OVERLAYS.
     * VanillaHudElements.MISC_OVERLAYS verified in fabric-api-0.141.3+1.21.11.
     */
    public static void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                HUD_ID,
                (context, tickCounter) -> render(context)
        );
    }

    public static void setVisible(boolean v) { visible = v; }
    public static boolean isVisible() { return visible; }
    public static void toggle() { visible = !visible; }

    // -------------------------------------------------------------------------
    // Render — uses Yarn types (DrawContext), Loom remaps at compile time
    // -------------------------------------------------------------------------

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        if (client.options.hudHidden) return;
        if (!visible) return;
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

        int x = PADDING_X;
        int y = PADDING_Y;

        // Line 1: furnace count
        String line1 = "\uD83D\uDD25 " + records.size() + " furnace" + (records.size() != 1 ? "s" : "");
        context.drawTextWithShadow(client.textRenderer, line1, x, y, headerColor);
        y += LINE_HEIGHT;

        // Line 2: next ETA or status
        Optional<FurnaceRecord> nextSmelting = records.stream()
                .filter(r -> r.state == FurnaceState.SMELTING && r.getEtaSeconds() > 0)
                .min(Comparator.comparingInt(FurnaceRecord::getEtaSeconds));

        if (doneCount > 0) {
            context.drawTextWithShadow(client.textRenderer,
                    "\u2705 " + doneCount + " done!", x, y, COLOR_DONE);
        } else if (nextSmelting.isPresent()) {
            String eta = formatEta(nextSmelting.get().getEtaSeconds());
            context.drawTextWithShadow(client.textRenderer,
                    "\u23F1 Next: " + eta, x, y, COLOR_WHITE);
        } else if (noFuelCount > 0) {
            context.drawTextWithShadow(client.textRenderer,
                    "\u274C " + noFuelCount + " no fuel", x, y, COLOR_NO_FUEL);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Formats seconds as MM:SS string. */
    static String formatEta(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }
}