package com.suschunkfinder;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the full detection result for a single chunk.
 * Mirrors the JS object returned by generateChunkData().
 */
public class ChunkDetectionResult {

    /** Chunk X offset relative to the player's chunk */
    public final int cx;
    /** Chunk Z offset relative to the player's chunk */
    public final int cz;

    // ── Sub-scores (each capped individually in the generator) ──────────────
    public int amethystScore;
    public int vinesScore;
    public int caveVinesScore;
    public int mobScore;
    public int deepScore;
    public int airScore;

    /** Sum of all sub-scores, capped at 100 */
    public int totalScore;

    /** Human-readable detection reasons, same as the JS reasons[] array */
    public final List<DetectionReason> reasons = new ArrayList<>();

    public ChunkDetectionResult(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }

    /** Returns true when totalScore meets or exceeds the given threshold. */
    public boolean isSuspicious(int threshold) {
        return totalScore >= threshold;
    }

    // ────────────────────────────────────────────────────────────────────────

    public static class DetectionReason {
        public enum Tag { AMETHYST, VINE, MOB, DEEP, AIR, CAVE }

        public final Tag    tag;
        public final String text;
        public final int    score;

        public DetectionReason(Tag tag, String text, int score) {
            this.tag   = tag;
            this.text  = text;
            this.score = score;
        }

        @Override
        public String toString() {
            return "[" + tag.name() + "] " + text + " (+" + score + ")";
        }
    }
}
