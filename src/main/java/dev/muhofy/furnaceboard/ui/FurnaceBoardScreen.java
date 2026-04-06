package dev.muhofy.furnaceboard.ui;

import dev.muhofy.furnaceboard.data.FurnaceRecord;
import dev.muhofy.furnaceboard.data.FurnaceState;
import dev.muhofy.furnaceboard.tracker.FurnaceTrackerManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dashboard screen — grid layout per furnace.
 *
 * Per row:
 *   [Input slot]              [Output slot]   ETA / State
 *   [Fuel slot ]   →arrow→                   X, Y, Z
 *
 * No remove button — all furnaces tracked automatically.
 */
public class FurnaceBoardScreen extends Screen {

    // Layout
    private static final int SLOT_SIZE      = 18;
    private static final int ROW_HEIGHT     = 46;
    private static final int MAX_VISIBLE    = 5;
    private static final int PANEL_WIDTH    = 280;
    private static final int HEADER_HEIGHT  = 22;
    private static final int FOOTER_HEIGHT  = 30;
    private static final int PADDING        = 8;

    // Colors
    private static final int COLOR_WHITE    = 0xFFFFFFFF;
    private static final int COLOR_DIM      = 0xFFAAAAAA;
    private static final int COLOR_SMELTING = 0xFF55FF55;
    private static final int COLOR_DONE     = 0xFFFFAA00;
    private static final int COLOR_NO_FUEL  = 0xFFFF5555;
    private static final int COLOR_EMPTY    = 0xFFAAAAAA;
    private static final int COLOR_BG       = 0xCC111111;
    private static final int COLOR_SLOT_BG  = 0xFF555555;
    private static final int COLOR_DIVIDER  = 0x44FFFFFF;
    private static final int COLOR_ARROW    = 0xFF888888;
    private static final int COLOR_BAR_BG   = 0xFF444444;
    private static final int COLOR_BAR_FG   = 0xFF55FF55;

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

        int panelX = (width - PANEL_WIDTH) / 2;
        int visRows = Math.min(records.size(), MAX_VISIBLE);
        int panelH  = HEADER_HEIGHT + Math.max(visRows, 1) * ROW_HEIGHT + FOOTER_HEIGHT + PADDING;
        int panelY  = (height - panelH) / 2;
        int footerY = panelY + panelH - FOOTER_HEIGHT + 6;

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

        int visRows = Math.min(records.size(), MAX_VISIBLE);
        int panelH  = HEADER_HEIGHT + Math.max(visRows, 1) * ROW_HEIGHT + FOOTER_HEIGHT + PADDING;
        int panelX  = (width - PANEL_WIDTH) / 2;
        int panelY  = (height - panelH) / 2;

        // Background
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, COLOR_BG);

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
                    width / 2, panelY + HEADER_HEIGHT + ROW_HEIGHT / 2, COLOR_DIM);
        }

        int rowY = panelY + HEADER_HEIGHT + PADDING / 2;
        int end  = Math.min(scrollOffset + MAX_VISIBLE, records.size());
        for (int i = scrollOffset; i < end; i++) {
            renderRow(context, records.get(i), panelX + PADDING, rowY, PANEL_WIDTH - PADDING * 2);
            rowY += ROW_HEIGHT;
        }

        // Scroll hint
        if (records.size() > MAX_VISIBLE) {
            String hint = (scrollOffset + MAX_VISIBLE < records.size()) ? "▼ more" : "▲ top";
            context.drawTextWithShadow(textRenderer, hint,
                    panelX + PANEL_WIDTH - 40, rowY - 4, COLOR_DIM);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderRow(DrawContext context, FurnaceRecord r, int x, int y, int w) {
        int col = stateColor(r.state);

        // --- Slot backgrounds ---
        // Input slot (top-left)
        int slotInputX = x;
        int slotInputY = y + 2;
        context.fill(slotInputX, slotInputY, slotInputX + SLOT_SIZE, slotInputY + SLOT_SIZE, COLOR_SLOT_BG);

        // Fuel slot (bottom-left)
        int slotFuelX = x;
        int slotFuelY = y + 2 + SLOT_SIZE + 2;
        context.fill(slotFuelX, slotFuelY, slotFuelX + SLOT_SIZE, slotFuelY + SLOT_SIZE, COLOR_SLOT_BG);

        // Output slot (right, vertically centered)
        int slotOutX = x + SLOT_SIZE + 30;
        int slotOutY = y + 2 + (SLOT_SIZE / 2);
        context.fill(slotOutX, slotOutY, slotOutX + SLOT_SIZE, slotOutY + SLOT_SIZE, COLOR_SLOT_BG);

        // --- Item icons ---
        ItemStack inputItem  = getItemStack(r.inputItem);
        ItemStack outputItem = getOutputStack(r);

        context.drawItem(inputItem, slotInputX + 1, slotInputY + 1);
        context.drawItem(outputItem, slotOutX + 1, slotOutY + 1);

        // --- Arrow between slots ---
        int arrowX = x + SLOT_SIZE + 6;
        int arrowY = y + 2 + SLOT_SIZE / 2 + 4;
        context.drawTextWithShadow(textRenderer, "→", arrowX, arrowY, COLOR_ARROW);

        // Progress bar under arrow (only when smelting)
        if (r.state == FurnaceState.SMELTING && r.cookTimeTotal > 0) {
            int barW = 22;
            int barX = arrowX;
            int barY = arrowY + 12;
            context.fill(barX, barY, barX + barW, barY + 3, COLOR_BAR_BG);
            int filled = (int) ((float) r.cookTime / r.cookTimeTotal * barW);
            context.fill(barX, barY, barX + filled, barY + 3, col);
        }

        // --- Right side: state + ETA + position ---
        int textX = slotOutX + SLOT_SIZE + 6;
        int textY = y + 4;

        // State label
        String stateStr = stateLabel(r);
        context.drawTextWithShadow(textRenderer, stateStr, textX, textY, col);

        // ETA
        if (r.state == FurnaceState.SMELTING && r.getEtaSeconds() > 0) {
            String eta = FurnaceBoardHudWidget.formatEta(r.getEtaSeconds());
            context.drawTextWithShadow(textRenderer, eta, textX, textY + 11, COLOR_WHITE);
        }

        // Position
        String pos = r.pos.getX() + ", " + r.pos.getY() + ", " + r.pos.getZ();
        context.drawTextWithShadow(textRenderer, pos, textX, textY + 22, COLOR_DIM);
    }

    private ItemStack getItemStack(@org.jetbrains.annotations.Nullable Identifier id) {
        if (id == null) return ItemStack.EMPTY;
        return Registries.ITEM.getOptionalValue(id)
                .map(item -> new ItemStack(item))
                .orElse(ItemStack.EMPTY);
    }

    private ItemStack getOutputStack(FurnaceRecord r) {
        if (r.state != FurnaceState.DONE || r.inputItem == null) return ItemStack.EMPTY;
        // Show a generic output icon — we don't track output item yet
        return new ItemStack(Items.IRON_INGOT);
    }

    private String stateLabel(FurnaceRecord r) {
        return switch (r.state) {
            case SMELTING -> "Smelting";
            case DONE     -> "✅ Done";
            case NO_FUEL  -> "❌ No fuel";
            case EMPTY    -> "—";
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
        int max = Math.max(0, records.size() - MAX_VISIBLE);
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