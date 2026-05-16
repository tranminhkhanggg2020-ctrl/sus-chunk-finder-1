package com.suschunkfinder;

import com.suschunkfinder.ChunkDetectionResult.DetectionReason;
import com.suschunkfinder.ChunkDetectionResult.DetectionReason.Tag;

/**
 * Core detection engine for Sus Chunk Finder (Fabric 1.21.x).
 *
 * Contains direct Java ports of the two key functions from the HTML simulator:
 *   • seedRng(cx, cz)          → {@link #seedRng(int, int)}
 *   • generateChunkData(...)   → {@link #generateChunkData(int, int, boolean)}
 *
 * Usage:
 * <pre>{@code
 *   SusChunkSettings settings = new SusChunkSettings();
 *   ChunkAnalyser    analyser = new ChunkAnalyser(settings);
 *
 *   // dcx / dcz = chunk offset relative to the player chunk
 *   double bias  = analyser.seedRng(dcx * 7 + dcz * 13, dcx - dcz);
 *   boolean isSus = bias < 0.22;
 *
 *   ChunkDetectionResult result = analyser.generateChunkData(dcx, dcz, isSus);
 *   if (result.isSuspicious(settings.getThreshold())) {
 *       // highlight chunk on HUD …
 *   }
 * }</pre>
 */
public class ChunkAnalyser {

    private final SusChunkSettings settings;

    public ChunkAnalyser(SusChunkSettings settings) {
        this.settings = settings;
    }

    // ════════════════════════════════════════════════════════════════════════
    // seedRng — Java port of JS seedRng(cx, cz)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Deterministic pseudo-random number in [0.0, 1.0) derived from two
     * integer seeds.  Mirrors the JavaScript version exactly:
     *
     * <pre>
     * function seedRng(cx, cz) {
     *   let h = (cx * 374761393 + cz * 1431655765) >>> 0;
     *   h = (h ^ (h >>> 16)) * 2246822519 >>> 0;
     *   h = (h ^ (h >>> 13)) * 3266489917 >>> 0;
     *   h = (h ^ (h >>> 16)) >>> 0;
     *   return h / 4294967296;
     * }
     * </pre>
     *
     * Key porting note:
     *   JS {@code >>> 0} forces unsigned 32-bit by truncating to the low 32
     *   bits and treating them as unsigned.  Java {@code int} is always
     *   signed 32-bit, so the bit-pattern is the same but the numeric value
     *   may differ.  We use {@code Integer.toUnsignedLong()} at the end to
     *   convert the final signed int to the correct unsigned long before
     *   dividing — this reproduces the JS {@code h / 4294967296} result.
     *
     * @param cx first seed (typically chunk X or a linear combination)
     * @param cz second seed (typically chunk Z or a linear combination)
     * @return pseudo-random double in [0.0, 1.0)
     */
    public double seedRng(int cx, int cz) {
        // Step 1: mix cx and cz — JS: (cx * 374761393 + cz * 1431655765) >>> 0
        // Java int multiply already wraps mod 2^32 (signed), same bit pattern
        int h = cx * 374761393 + cz * 1431655765;

        // Step 2: avalanche — JS: (h ^ (h >>> 16)) * 2246822519 >>> 0
        // Java >>> is logical right-shift (zero-fill), identical to JS >>>
        h = (h ^ (h >>> 16)) * 0x85EBCA6B;   // 0x85EBCA6B == 2246822519u

        // Step 3: avalanche — JS: (h ^ (h >>> 13)) * 3266489917 >>> 0
        h = (h ^ (h >>> 13)) * 0xC2B2AE35;   // 0xC2B2AE35 == 3266489917u

        // Step 4: finalise — JS: (h ^ (h >>> 16)) >>> 0
        h = h ^ (h >>> 16);

        // Convert the signed Java int to unsigned long so the division
        // gives exactly the same result as JS "h / 4294967296"
        return Integer.toUnsignedLong(h) / 4294967296.0;
    }

    // ════════════════════════════════════════════════════════════════════════
    // generateChunkData — Java port of JS generateChunkData(cx, cz, isSus)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Analyses a single chunk and returns a {@link ChunkDetectionResult}.
     *
     * Mirrors the JavaScript function directly — same seed inputs, same score
     * caps, same branch conditions.
     *
     * @param cx    chunk X offset relative to the player's chunk
     * @param cz    chunk Z offset relative to the player's chunk
     * @param isSus whether this chunk was pre-classified as suspicious
     *              (caller computes: {@code seedRng(dcx*7+dcz*13, dcx-dcz) < 0.22})
     * @return fully populated {@link ChunkDetectionResult}
     */
    public ChunkDetectionResult generateChunkData(int cx, int cz, boolean isSus) {

        // Three independent pseudo-random values — same seeds as the JS
        double r  = seedRng(cx,      cz);
        double r2 = seedRng(cz,      cx + 1337);
        double r3 = seedRng(cx + 42, cz + 99);

        ChunkDetectionResult result = new ChunkDetectionResult(cx, cz);

        if (isSus) {
            // ── Amethyst ──────────────────────────────────────────────────
            if (settings.detectAmethyst && r < 0.7) {
                int count = (int) Math.floor(r * 8 + 2);
                result.amethystScore = Math.min(40, count * 8);
                result.reasons.add(new DetectionReason(
                    Tag.AMETHYST,
                    count + " budding amethyst, no geode shell",
                    result.amethystScore
                ));
            }

            // ── Vines ─────────────────────────────────────────────────────
            if (settings.detectVines && r2 < 0.6) {
                int count = (int) Math.floor(r2 * 5 + 1);
                result.vinesScore = Math.min(20, count * 4);
                result.reasons.add(new DetectionReason(
                    Tag.VINE,
                    count + " vines on deepslate surface",
                    result.vinesScore
                ));
            }

            // ── Rotated Deepslate ──────────────────────────────────────────
            if (settings.detectDeep && r3 < 0.8) {
                int count = (int) Math.floor(r3 * 12 + 3);
                result.deepScore = Math.min(35, count * 3);
                result.reasons.add(new DetectionReason(
                    Tag.DEEP,
                    count + " processed deepslate blocks",
                    result.deepScore
                ));
            }

            // ── Mob Anomaly ───────────────────────────────────────────────
            if (settings.detectMob && r < 0.5) {
                int mobs = (int) Math.floor(r * 8 + 3);
                result.mobScore = Math.min(25, mobs * 3);
                result.reasons.add(new DetectionReason(
                    Tag.MOB,
                    mobs + " hostiles above y=15",
                    result.mobScore
                ));
            }

            // ── Cave Vines ────────────────────────────────────────────────
            if (settings.detectCaveVines && r2 < 0.45) {
                result.caveVinesScore = 12;
                result.reasons.add(new DetectionReason(
                    Tag.CAVE,
                    "Glow berry cluster, no lush cave",
                    12
                ));
            }

            // ── Air Pockets ───────────────────────────────────────────────
            if (settings.detectAir && r3 < 0.55) {
                result.airScore = 14;
                result.reasons.add(new DetectionReason(
                    Tag.AIR,
                    "Rectangular void at y=-60 zone",
                    14
                ));
            }

        } else {
            // ── Non-suspicious chunk: minor natural signals only ───────────

            if (settings.detectAmethyst && r < 0.1) {
                result.amethystScore = (int) Math.floor(r * 12);
                if (result.amethystScore > 3) {
                    result.reasons.add(new DetectionReason(
                        Tag.AMETHYST,
                        "Minor amethyst cluster",
                        result.amethystScore
                    ));
                }
            }

            if (settings.detectMob && r2 < 0.08) {
                result.mobScore = (int) Math.floor(r2 * 10);
                if (result.mobScore > 3) {
                    result.reasons.add(new DetectionReason(
                        Tag.MOB,
                        "Small mob grouping",
                        result.mobScore
                    ));
                }
            }
        }

        // ── Total score (capped at 100) ────────────────────────────────────
        result.totalScore = Math.min(100,
            result.amethystScore  +
            result.vinesScore     +
            result.caveVinesScore +
            result.mobScore       +
            result.deepScore      +
            result.airScore
        );

        return result;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Helper used by the HUD scanner (mirrors initGrid bias check)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Returns true when the chunk at relative position (dcx, dcz) should be
     * pre-classified as suspicious before running the full scorer.
     *
     * Mirrors the JS line:
     * <pre>const isSus = seedRng(dcx * 7 + dcz * 13, dcx - dcz) &lt; 0.22;</pre>
     */
    public boolean isSusCandidate(int dcx, int dcz) {
        return seedRng(dcx * 7 + dcz * 13, dcx - dcz) < 0.22;
    }
}
