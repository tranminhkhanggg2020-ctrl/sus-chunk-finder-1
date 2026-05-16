package com.suschunkfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint — wires together:
 *  • Keybind Y  → toggle HUD on/off
 *  • Each tick   → re-analyse chunks around the player
 *  • HUD render  → draw the chunk grid + detail panel
 */
public class SusChunkFinderClient implements ClientModInitializer {

    // ── Shared state (accessed by HUD renderer) ──────────────────────────────
    public static final SusChunkSettings SETTINGS = new SusChunkSettings();
    public static       ChunkScanner     SCANNER  = null;   // initialised on first tick
    public static       boolean          ACTIVE   = true;

    // ── Keybinds ─────────────────────────────────────────────────────────────
    private static KeyBinding keyToggle;

    private static int tickCounter = 0;
    /** Re-scan every N ticks (20 ticks = 1 second). */
    private static final int SCAN_INTERVAL = 20;

    // ────────────────────────────────────────────────────────────────────────

    @Override
    public void onInitializeClient() {

        // Register keybind Y — toggle HUD
        keyToggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.sus-chunk-finder.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "category.sus-chunk-finder"
        ));

        // Tick event: poll keybind + periodic re-scan
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Toggle on Y press
            while (keyToggle.wasPressed()) {
                ACTIVE = !ACTIVE;
            }

            if (!ACTIVE) return;
            if (client.player == null) return;

            tickCounter++;
            if (tickCounter < SCAN_INTERVAL) return;
            tickCounter = 0;

            // Lazy-init scanner
            if (SCANNER == null) SCANNER = new ChunkScanner(SETTINGS);

            int playerY = (int) client.player.getY();
            SCANNER.scan(client, playerY);
        });

        // Register HUD renderer
        HudRenderCallback.EVENT.register(new SusChunkHud());
    }
}
