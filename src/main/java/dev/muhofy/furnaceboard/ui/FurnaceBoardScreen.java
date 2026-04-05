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
 * Shows all tracked furnaces with state, progress bar, and ETA.
 * Each row has a "Remove" button to exclude that furnace from tracking.
 */
public class FurnaceBoardScreen extends Screen {

    private static final int ROW_HEIGHT       = 38;
    private static final int MAX_VISIBLE_ROWS = 6;
    private static final int PANEL_WIDTH      = 300;
    private static final int HEADER_HEIGHT    = 24;
    private static final int FOOTER_HEIGHT    = 32;
    private static final int PADDING          = 8;
    private static final int BAR_HEIGHT       = 5;

    private static final int COLOR_SMELTING   = 0xFF55FF55;
    private static final int COLOR_DONE       = 0xFFFFAA00;
    private static final int COLOR_NO_FUEL    = 0xFFFF5555;
    private static final int COLOR_EMPTY      = 0xFFAAAAAA;
    private static final int COLOR_WHITE      = 0xFFFFFFFF;
    private static final int COLOR_DIM        = 0xFFAAAAAA;
    private static final int COLOR_BAR_BG     = 0xFF444444;
    private static final int COLOR_PANEL_BG   = 0xCC111111;
    private static final int COLOR_DIVIDER    = 0x44FFFFFF;

    private int scrollOffset = 0;
    private List<FurnaceRecord> records = new ArrayList<>();

    public FurnaceBoardScreen() {
        super(Text.translatable("screen.furnaceboard.title"));
    }

    @Override
    protected void init() {
        refreshRecords();
        buildButtons();
    }

    private void buildButtons() {
        clearChildren();
        refreshRecords();

        int panelX = (width - PANEL_WIDTH) / 2;
        int visRows = Math.min(records.size(), MAX_VISIBLE_ROWS);
        int panelH  = HEADER_HEIGHT + visRows * ROW_HEIGHT + FOOTER_HEIGHT + PADDING;
        int panelY  = (height - panelH) / 2;
        int footerY = panelY + panelH - FOOTER_HEIGHT + 6;

        // Per-row Remove buttons
        int rowY = panelY + HEADER_HEIGHT + PADDING;
        int end  = Math.min(scrollOffset + MAX_VISIBLE_ROWS, records.size());
        for (int i = scrollOffset; i < end; i++) {
            final BlockPos pos = records.get(i).pos;
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("✕"),
                    btn -> {
                        FurnaceTrackerManager.excludeFurnace(pos);
                        buildButtons();
                    }
            ).dimensions(panelX + PANEL_WIDTH - PADDING - 20, rowY + 10, 20, 14).build());
            rowY += ROW_HEIGHT;
        }

        // Footer buttons
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("button.furnaceboard.toggle_hud"),
                btn -> FurnaceBoardHudWidget.toggle()
        ).dimensions(panelX + PADDING, footerY, 110, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("button.furnaceboard.close"),
                btn -> close()
        ).dimensions(panelX + PANEL_WIDTH - 60 - PADDING, footerY, 60, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        refreshRecords();

        int visRows = Math.min(records.size(), MAX_VISIBLE_ROWS);
        int panelH  = HEADER_HEIGHT + visRows * ROW_HEIGHT + FOOTER_HEIGHT + PADDING;
        int panelX  = (width - PANEL_WIDTH) / 2;
        int panelY  = (height - panelH) / 2;

        // Panel background
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, COLOR_PANEL_BG);

        // Header
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("screen.furnaceboard.title"),
                width / 2, panelY + PADDING, COLOR_WHITE);

        // Divider
        context.fill(panelX + PADDING, panelY + HEADER_HEIGHT,
                panelX + PANEL_WIDTH - PADDING, panelY + HEADER_HEIGHT + 1, COLOR_DIVIDER);

        if (records.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("No furnaces tracked yet."),
                    width / 2, panelY + HEADER_HEIGHT + PADDING + 10, COLOR_DIM);
        }

        // Rows
        int rowY = panelY + HEADER_HEIGHT + PADDING;
        int end  = Math.min(scrollOffset + MAX_VISIBLE_ROWS, records.size());
        for (int i = scrollOffset; i < end; i++) {
            renderRow(context, records.get(i), panelX + PADDING, rowY, PANEL_WIDTH - PADDING * 2 - 26);
            rowY += ROW_HEIGHT;
        }

        // Scroll indicator
        if (records.size() > MAX_VISIBLE_ROWS) {
            String hint = scrollOffset + MAX_VISIBLE_ROWS < records.size() ? "▼" : "▲";
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(hint),
                    width / 2, rowY, COLOR_DIM);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderRow(DrawContext context, FurnaceRecord r, int x, int y, int w) {
        int col = stateColor(r.state);

        // Position
        String pos = r.pos.getX() + ", " + r.pos.getY() + ", " + r.pos.getZ();
        context.drawTextWithShadow(textRenderer, pos, x, y, COLOR_DIM);

        // Item
        String item = r.inputItem != null
                ? r.inputItem.getPath().replace("_", " ") + " x" + r.inputCount
                : Text.translatable("label.furnaceboard.empty").getString();
        context.drawTextWithShadow(textRenderer, item, x, y + 11, COLOR_WHITE);

        // Progress bar
        int barW = w - 56;
        context.fill(x, y + 24, x + barW, y + 24 + BAR_HEIGHT, COLOR_BAR_BG);
        if (r.state == FurnaceState.SMELTING && r.cookTimeTotal > 0) {
            int filled = (int) ((float) r.cookTime / r.cookTimeTotal * barW);
            context.fill(x, y + 24, x + filled, y + 24 + BAR_HEIGHT, col);
        } else if (r.state == FurnaceState.DONE) {
            context.fill(x, y + 24, x + barW, y + 24 + BAR_HEIGHT, COLOR_DONE);
        }

        // State label
        context.drawTextWithShadow(textRenderer, stateLabel(r), x + barW + 4, y + 22, col);
    }

    private String stateLabel(FurnaceRecord r) {
        return switch (r.state) {
            case SMELTING -> FurnaceBoardHudWidget.formatEta(r.getEtaSeconds());
            case DONE     -> "\u2705 Done";
            case NO_FUEL  -> "\u274C No fuel";
            case EMPTY    -> "\u2014";
        };
    }

    private int stateColor(FurnaceState s) {
        return switch (s) {
            case SMELTING -> COLOR_SMELTING;
            case DONE     -> COLOR_DONE;
            case NO_FUEL  -> COLOR_NO_FUEL;
            case EMPTY    -> COLOR_EMPTY;
        };
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int max = Math.max(0, records.size() - MAX_VISIBLE_ROWS);
        scrollOffset = (int) Math.max(0, Math.min(scrollOffset - v, max));
        buildButtons();
        return true;
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }

    private void refreshRecords() {
        Map<BlockPos, FurnaceRecord> all = FurnaceTrackerManager.getWorldData().getAll();
        records = new ArrayList<>(all.values());
        records.sort((a, b) -> {
            int pa = priority(a.state), pb = priority(b.state);
            if (pa != pb) return Integer.compare(pa, pb);
            return Integer.compare(a.getEtaSeconds(), b.getEtaSeconds());
        });
    }

    private int priority(FurnaceState s) {
        return switch (s) {
            case DONE     -> 0;
            case SMELTING -> 1;
            case NO_FUEL  -> 2;
            case EMPTY    -> 3;
        };
    }
}