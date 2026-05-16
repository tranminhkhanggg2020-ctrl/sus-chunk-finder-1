package com.suschunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks the grid of chunks around the player, runs {@link ChunkAnalyser}
 * on each one, and stores the results for the HUD renderer to read.
 *
 * This mirrors the JS {@code initGrid()} function.
 */
public class ChunkScanner {

    private final SusChunkSettings settings;
    private final ChunkAnalyser    analyser;

    /** Latest scan results — read by {@link SusChunkHud} on the render thread. */
    public volatile List<ScanEntry> results   = new ArrayList<>();
    public volatile int             playerCX  = 0;
    public volatile int             playerCZ  = 0;
    public volatile int             playerY   = 64;
    public volatile int             susCount  = 0;

    public ChunkScanner(SusChunkSettings settings) {
        this.settings = settings;
        this.analyser = new ChunkAnalyser(settings);
    }

    // ────────────────────────────────────────────────────────────────────────

    public void scan(MinecraftClient client, int currentPlayerY) {
        if (client.player == null) return;

        ChunkPos pChunk = client.player.getChunkPos();
        playerCX = pChunk.x;
        playerCZ = pChunk.z;
        playerY  = currentPlayerY;

        // Mod only runs above y=15 (matches HTML simulator logic)
        if (currentPlayerY <= 15) {
            results  = new ArrayList<>();
            susCount = 0;
            return;
        }

        int dist = settings.simDist;
        List<ScanEntry> newResults = new ArrayList<>();
        int count = 0;

        for (int dcz = -dist; dcz <= dist; dcz++) {
            for (int dcx = -dist; dcx <= dist; dcx++) {
                if (dcx == 0 && dcz == 0) {
                    // Player's own chunk — mark but don't score
                    newResults.add(new ScanEntry(dcx, dcz, null, true));
                    continue;
                }

                boolean isSus = analyser.isSusCandidate(dcx, dcz);
                ChunkDetectionResult data = analyser.generateChunkData(dcx, dcz, isSus);
                newResults.add(new ScanEntry(dcx, dcz, data, false));

                if (data.isSuspicious(settings.getThreshold())) count++;
            }
        }

        results  = newResults;
        susCount = count;
    }

    // ── Data record ──────────────────────────────────────────────────────────

    public static class ScanEntry {
        public final int                  dcx, dcz;
        public final ChunkDetectionResult data;        // null for player chunk
        public final boolean              isPlayer;

        public ScanEntry(int dcx, int dcz, ChunkDetectionResult data, boolean isPlayer) {
            this.dcx      = dcx;
            this.dcz      = dcz;
            this.data     = data;
            this.isPlayer = isPlayer;
        }
    }
}
