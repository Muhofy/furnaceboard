package dev.muhofy.furnaceboard.ui;

import dev.muhofy.furnaceboard.data.FurnaceRecord;
import dev.muhofy.furnaceboard.data.FurnaceState;
import dev.muhofy.furnaceboard.tracker.FurnaceTrackerManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dashboard screen — opened via keybind (default: F).
 * Shows all tracked furnaces with state, progress, and ETA.
 *
 * Layout per SYSINSTRUCTIONS:
 *   Header: "FurnaceBoard"
 *   Per row: Position | Item | Progress bar | State/ETA
 *   Footer: [Toggle HUD] [Close]
 *   Scroll if more than 6 furnaces
 *
 * Colors per state:
 *   SMELTING : #55FF55
 *   DONE     : #FFAA00
 *   NO_FUEL  : #FF5555
 *   EMPTY    : #AAAAAA
 */
public class FurnaceBoardScreen extends Screen {

    // Layout constants
    private static final int ROW_HEIGHT        = 36;
    private static final int MAX_VISIBLE_ROWS  = 6;
    private static final int PANEL_WIDTH       = 280;
    private static final int HEADER_HEIGHT     = 20;
    private static final int FOOTER_HEIGHT     = 30;
    private static final int PADDING           = 8;
    private static final int BAR_HEIGHT        = 6;

    // State colors (ARGB)
    private static final int COLOR_SMELTING    = 0xFF55FF55;
    private static final int COLOR_DONE        = 0xFFFFAA00;
    private static final int COLOR_NO_FUEL     = 0xFFFF5555;
    private static final int COLOR_EMPTY       = 0xFFAAAAAA;
    private static final int COLOR_BAR_BG      = 0xFF333333;
    private static final int COLOR_WHITE       = 0xFFFFFFFF;
    private static final int COLOR_PANEL_BG    = 0xCC000000;

    private int scrollOffset = 0;
    private List<FurnaceRecord> records = new ArrayList<>();

    public FurnaceBoardScreen() {
        super(Text.translatable("screen.furnaceboard.title"));
    }

    @Override
    protected void init() {
        refreshRecords();

        int panelX = (width - PANEL_WIDTH) / 2;
        int panelH = HEADER_HEIGHT + Math.min(records.size(), MAX_VISIBLE_ROWS) * ROW_HEIGHT + FOOTER_HEIGHT + PADDING * 2;
        int panelY = (height - panelH) / 2;
        int footerY = panelY + panelH - FOOTER_HEIGHT + 4;

        // [Toggle HUD] button
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("button.furnaceboard.toggle_hud"),
                btn -> FurnaceBoardHudWidget.toggle()
        ).dimensions(panelX + PADDING, footerY, 120, 20).build());

        // [Close] button
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("button.furnaceboard.close"),
                btn -> close()
        ).dimensions(panelX + PANEL_WIDTH - 60 - PADDING, footerY, 60, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        refreshRecords();

        int panelH = HEADER_HEIGHT + Math.min(records.size(), MAX_VISIBLE_ROWS) * ROW_HEIGHT + FOOTER_HEIGHT + PADDING * 2;
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - panelH) / 2;

        // Panel background
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, COLOR_PANEL_BG);

        // Header
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("screen.furnaceboard.title"),
                width / 2, panelY + PADDING, COLOR_WHITE);

        // Divider
        context.fill(panelX + PADDING, panelY + HEADER_HEIGHT + 2,
                panelX + PANEL_WIDTH - PADDING, panelY + HEADER_HEIGHT + 3, 0x44FFFFFF);

        // Rows
        int rowY = panelY + HEADER_HEIGHT + PADDING;
        int end = Math.min(scrollOffset + MAX_VISIBLE_ROWS, records.size());
        for (int i = scrollOffset; i < end; i++) {
            renderRow(context, records.get(i), panelX + PADDING, rowY, PANEL_WIDTH - PADDING * 2);
            rowY += ROW_HEIGHT;
        }

        // Scroll hint
        if (records.size() > MAX_VISIBLE_ROWS) {
            String hint = (scrollOffset + MAX_VISIBLE_ROWS < records.size()) ? "▼ scroll" : "▲ scroll";
            context.drawTextWithShadow(textRenderer, hint,
                    panelX + PANEL_WIDTH - 50, rowY, 0x88FFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderRow(DrawContext context, FurnaceRecord record, int x, int y, int w) {
        int stateColor = stateColor(record.state);

        // Position label
        String posStr = record.pos.getX() + ", " + record.pos.getY() + ", " + record.pos.getZ();
        context.drawTextWithShadow(textRenderer, posStr, x, y, 0xFFAAAAAA);

        // Item name + count
        String itemStr = record.inputItem != null
                ? record.inputItem.getPath().replace("_", " ") + " x" + record.inputCount
                : Text.translatable("label.furnaceboard.empty").getString();
        context.drawTextWithShadow(textRenderer, itemStr, x, y + 10, COLOR_WHITE);

        // Progress bar
        int barW = w - 80;
        context.fill(x, y + 22, x + barW, y + 22 + BAR_HEIGHT, COLOR_BAR_BG);
        if (record.cookTimeTotal > 0 && record.state == FurnaceState.SMELTING) {
            int filled = (int) ((float) record.cookTime / record.cookTimeTotal * barW);
            context.fill(x, y + 22, x + filled, y + 22 + BAR_HEIGHT, stateColor);
        } else if (record.state == FurnaceState.DONE) {
            context.fill(x, y + 22, x + barW, y + 22 + BAR_HEIGHT, COLOR_DONE);
        }

        // State / ETA label
        String stateLabel = buildStateLabel(record);
        context.drawTextWithShadow(textRenderer, stateLabel, x + barW + 4, y + 22, stateColor);
    }

    private String buildStateLabel(FurnaceRecord record) {
        return switch (record.state) {
            case SMELTING -> FurnaceBoardHudWidget.formatEta(record.getEtaSeconds());
            case DONE     -> "\u2705 Done";
            case NO_FUEL  -> "\u274C No fuel";
            case EMPTY    -> "\u2014 Empty";
        };
    }

    private int stateColor(FurnaceState state) {
        return switch (state) {
            case SMELTING -> COLOR_SMELTING;
            case DONE     -> COLOR_DONE;
            case NO_FUEL  -> COLOR_NO_FUEL;
            case EMPTY    -> COLOR_EMPTY;
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, records.size() - MAX_VISIBLE_ROWS);
        if (verticalAmount < 0) scrollOffset = Math.min(scrollOffset + 1, maxScroll);
        else                    scrollOffset = Math.max(scrollOffset - 1, 0);
        return true;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    private void refreshRecords() {
        Map<BlockPos, FurnaceRecord> all = FurnaceTrackerManager.getWorldData().getAll();
        records = new ArrayList<>(all.values());
        // Sort: DONE first, then SMELTING by ETA, then NO_FUEL, then EMPTY
        records.sort((a, b) -> {
            int pa = statePriority(a.state), pb = statePriority(b.state);
            if (pa != pb) return Integer.compare(pa, pb);
            return Integer.compare(a.getEtaSeconds(), b.getEtaSeconds());
        });
    }

    private int statePriority(FurnaceState s) {
        return switch (s) {
            case DONE     -> 0;
            case SMELTING -> 1;
            case NO_FUEL  -> 2;
            case EMPTY    -> 3;
        };
    }
}