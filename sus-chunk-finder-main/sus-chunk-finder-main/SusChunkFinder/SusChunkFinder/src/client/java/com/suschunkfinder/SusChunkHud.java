package com.suschunkfinder;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;

/**
 * Draws the Sus Chunk Finder overlay onto the in-game HUD.
 *
 * Layout (top-left corner):
 * ┌──────────────────────────────────┐
 * │ SUS CHUNK FINDER [ACTIVE]  Y:64 │  ← status bar
 * │  □ □ □ □ □ □ □ □ □              │  ← 9×9 chunk grid
 * │  □ □ □ ■ □ □ □ □ □              │    (■ = sus, ● = you)
 * │  Threshold:70  Sus:3  Chunks:80 │  ← info bar
 * └──────────────────────────────────┘
 *
 * Colors mirror the HTML simulator's CSS classes:
 *   score 0–29  → transparent / very dark
 *   score 30–49 → green tint
 *   score 50–74 → yellow tint
 *   score 75–89 → red tint
 *   score 90+   → bright red (pulsing not supported in vanilla DrawContext)
 */
public class SusChunkHud implements HudRenderCallback {

    // ── Layout constants ─────────────────────────────────────────────────────
    private static final int MARGIN      = 6;   // px from screen edge
    private static final int CELL        = 14;  // px per chunk cell
    private static final int GAP         = 2;   // px gap between cells
    private static final int GRID_SIZE   = 9;   // 9×9 grid

    // ── Colours (ARGB) ───────────────────────────────────────────────────────
    private static final int COL_BG        = 0xCC111111;
    private static final int COL_BORDER    = 0xFF444444;
    private static final int COL_TEXT      = 0xFFE0E0E0;
    private static final int COL_MUTED     = 0xFF888888;
    private static final int COL_ACTIVE    = 0xFF4CAF50;   // green
    private static final int COL_INACTIVE  = 0xFFF44336;   // red
    private static final int COL_ACCENT    = 0xFFFF6B35;   // orange

    private static final int COL_LOW       = 0x664CAF50;   // green tint
    private static final int COL_MID       = 0x66FFC107;   // yellow tint
    private static final int COL_HIGH      = 0x66F44336;   // red tint
    private static final int COL_EXTREME   = 0xCCF44336;   // bright red
    private static final int COL_PLAYER    = 0xFFFFFFFF;   // white dot
    private static final int COL_CELL_DARK = 0x33222222;   // empty cell

    // ── Total HUD panel size ─────────────────────────────────────────────────
    private static final int PANEL_W = GRID_SIZE * (CELL + GAP) - GAP + MARGIN * 2;
    private static final int STATUS_H = 10;
    private static final int INFO_H   = 10;
    private static final int PANEL_H  = STATUS_H + MARGIN
                                      + GRID_SIZE * (CELL + GAP) - GAP
                                      + MARGIN + INFO_H + MARGIN * 2;

    // ────────────────────────────────────────────────────────────────────────

    @Override
    public void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!SusChunkFinderClient.ACTIVE) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        ChunkScanner scanner = SusChunkFinderClient.SCANNER;
        SusChunkSettings settings = SusChunkFinderClient.SETTINGS;

        int playerY  = scanner != null ? scanner.playerY  : (int) mc.player.getY();
        boolean live = playerY > 15;

        // ── Panel background ─────────────────────────────────────────────────
        ctx.fill(MARGIN, MARGIN, MARGIN + PANEL_W, MARGIN + PANEL_H, COL_BG);
        ctx.drawBorder(MARGIN, MARGIN, PANEL_W, PANEL_H, COL_BORDER);

        int y = MARGIN + MARGIN;
        int x = MARGIN + MARGIN;

        // ── Status bar ───────────────────────────────────────────────────────
        String statusText = live
            ? "SUS CHUNK FINDER [ACTIVE]  Y:" + playerY
            : "SUS CHUNK FINDER [INACTIVE — y<15]";
        int statusCol = live ? COL_ACTIVE : COL_INACTIVE;
        ctx.drawText(mc.textRenderer, statusText, x, y, statusCol, false);
        y += STATUS_H + MARGIN;

        // ── Chunk grid ───────────────────────────────────────────────────────
        List<ChunkScanner.ScanEntry> entries = scanner != null ? scanner.results : List.of();
        int threshold = settings.getThreshold();

        for (ChunkScanner.ScanEntry entry : entries) {
            int col = entry.dcx + GRID_SIZE / 2;
            int row = entry.dcz + GRID_SIZE / 2;
            int cx  = x + col * (CELL + GAP);
            int cy  = y + row * (CELL + GAP);

            if (entry.isPlayer) {
                // Player marker — white cell with orange border
                ctx.fill(cx, cy, cx + CELL, cy + CELL, COL_CELL_DARK);
                ctx.drawBorder(cx, cy, CELL, CELL, COL_ACCENT);
                // White dot in centre
                ctx.fill(cx + CELL/2 - 2, cy + CELL/2 - 2,
                         cx + CELL/2 + 2, cy + CELL/2 + 2, COL_PLAYER);
                continue;
            }

            if (entry.data == null || !live) {
                ctx.fill(cx, cy, cx + CELL, cy + CELL, COL_CELL_DARK);
                continue;
            }

            int score   = entry.data.totalScore;
            boolean sus = score >= threshold;
            int fillCol = sus ? scoreColor(score) : COL_CELL_DARK;

            ctx.fill(cx, cy, cx + CELL, cy + CELL, fillCol);

            // Show % label if enabled and suspicious
            if (settings.showPercentLabels && sus) {
                String label = score + "%";
                // Only draw if CELL is wide enough (label fits at 5px scale)
                int labelW = mc.textRenderer.getWidth(label);
                if (labelW <= CELL) {
                    ctx.drawText(mc.textRenderer, label,
                        cx + (CELL - labelW) / 2, cy + CELL / 2 - 3,
                        COL_TEXT, false);
                }
            }
        }

        y += GRID_SIZE * (CELL + GAP) - GAP + MARGIN;

        // ── Info bar ─────────────────────────────────────────────────────────
        int susCount = scanner != null ? scanner.susCount : 0;
        int scanned  = entries.size();
        String info  = "Thresh:" + threshold + "  Sus:" + susCount + "  Chunks:" + scanned;
        ctx.drawText(mc.textRenderer, info, x, y, COL_MUTED, false);
    }

    // ────────────────────────────────────────────────────────────────────────

    /** Maps a suspicion score to the matching ARGB cell fill colour. */
    private static int scoreColor(int score) {
        if (score >= 90) return COL_EXTREME;
        if (score >= 75) return COL_HIGH;
        if (score >= 50) return COL_MID;
        return COL_LOW;
    }
}
