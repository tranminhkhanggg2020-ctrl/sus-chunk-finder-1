package com.suschunkfinder;

/**
 * Runtime configuration, equivalent to the JS {@code settings} object.
 * All fields are public for easy mutation from the config screen / keybinds.
 */
public class SusChunkSettings {

    // ── General ─────────────────────────────────────────────────────────────
    /** 0–100. Higher = more false-positives but fewer misses. Default: 20 */
    public int sensitivity = 20;

    /** Chunk radius to analyse around the player. Default: 4 */
    public int simDist = 4;

    // ── Detection toggles ───────────────────────────────────────────────────
    public boolean detectAmethyst  = true;
    public boolean detectVines     = true;
    public boolean detectCaveVines = true;
    public boolean detectMob       = true;
    public boolean detectDeep      = true;
    public boolean detectAir       = false;   // off by default, same as HTML

    // ── Render / HUD ────────────────────────────────────────────────────────
    public boolean showPercentLabels = true;
    public boolean showDebug         = false;
    public boolean chatFeedback      = false;

    // ────────────────────────────────────────────────────────────────────────

    /**
     * Derives the suspicion threshold from sensitivity.
     * Mirrors {@code getThreshold()} in the JS simulator.
     */
    public int getThreshold() {
        return Math.max(10, 90 - sensitivity);
    }
}
