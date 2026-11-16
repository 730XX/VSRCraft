package com.vsrcraft.client.gui.renderer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Visualizador de teclas y KPS (Key Overlay / JKPS) en la esquina inferior derecha.
 * Muestra el nombre de cada tecla asignada, su contador de pulsaciones y los KPS en tiempo real,
 * iluminándose cada cuadro al presionar su carril.
 */
public class KeyOverlayRenderer {
    private final int keyCount;
    private final int[] pressCounts;
    private final float[] pressAlpha;
    private final Deque<Long> recentPressesNano = new ArrayDeque<>();
    private int peakKps = 0;
    private long lastRenderNano = System.nanoTime();

    public KeyOverlayRenderer(int keyCount) {
        this.keyCount = Math.max(1, keyCount);
        this.pressCounts = new int[this.keyCount];
        this.pressAlpha = new float[this.keyCount];
    }

    public void reset() {
        for (int i = 0; i < this.keyCount; i++) {
            this.pressCounts[i] = 0;
            this.pressAlpha[i] = 0.0f;
        }
        this.recentPressesNano.clear();
        this.peakKps = 0;
        this.lastRenderNano = System.nanoTime();
    }

    public void onLanePressed(int lane) {
        if (lane < 0 || lane >= this.keyCount) return;
        this.pressCounts[lane]++;
        this.pressAlpha[lane] = 1.0f;

        long now = System.nanoTime();
        this.recentPressesNano.addLast(now);
        pruneOldPresses(now);
        if (this.recentPressesNano.size() > this.peakKps) {
            this.peakKps = this.recentPressesNano.size();
        }
    }

    private void pruneOldPresses(long nowNano) {
        long cutoff = nowNano - 1_000_000_000L;
        while (!this.recentPressesNano.isEmpty() && this.recentPressesNano.peekFirst() < cutoff) {
            this.recentPressesNano.removeFirst();
        }
    }

    public void render(GuiGraphics g, Font font, int[] laneKeys, boolean[] lanePressed, int screenWidth, int screenHeight) {
        long now = System.nanoTime();
        float dtSec = (float) Math.min(0.05, Math.max(0.0, (now - this.lastRenderNano) / 1_000_000_000.0));
        this.lastRenderNano = now;

        pruneOldPresses(now);
        int currentKps = this.recentPressesNano.size();

        int boxW = (this.keyCount <= 4) ? 26 : 21;
        int boxH = 34;
        int gap = 3;
        int totalW = this.keyCount * boxW + (this.keyCount - 1) * gap;

        int startX = screenWidth - totalW - 12;
        int startY = screenHeight - boxH - 12;

        // Etiqueta KPS justo encima del visualizador de teclas
        String kpsText = String.format(Locale.ROOT, "%d KPS", currentKps);
        int kpsColor = (currentKps > 0) ? 0xFF00E5FF : 0xFF778899;
        int kpsW = font.width(kpsText);
        g.drawString(font, kpsText, startX + totalW - kpsW, startY - 11, kpsColor, true);

        for (int i = 0; i < this.keyCount; i++) {
            boolean held = (lanePressed != null && i < lanePressed.length && lanePressed[i]);
            if (held) {
                this.pressAlpha[i] = 1.0f;
            } else if (this.pressAlpha[i] > 0.0f) {
                this.pressAlpha[i] = Math.max(0.0f, this.pressAlpha[i] - dtSec * 10.0f);
            }

            float glow = this.pressAlpha[i];
            int bx = startX + i * (boxW + gap);
            int by = startY;

            // Fondo: negro translúcido en reposo, iluminado cian/blanco al presionar
            int bgAlpha = Math.round(170 + 50 * glow);
            int bgR = Math.round(10 + 75 * glow);
            int bgG = Math.round(14 + 135 * glow);
            int bgB = Math.round(22 + 175 * glow);
            int bgColor = (bgAlpha << 24) | (bgR << 16) | (bgG << 8) | bgB;
            g.fill(bx, by, bx + boxW, by + boxH, bgColor);

            // Borde fino estilo osu! Key Overlay
            int borderColor = (glow > 0.2f) ? 0xFF00FFFF : 0xFF4B7D99;
            g.fill(bx, by, bx + boxW, by + 1, borderColor);
            g.fill(bx, by + boxH - 1, bx + boxW, by + boxH, borderColor);
            g.fill(bx, by, bx + 1, by + boxH, borderColor);
            g.fill(bx + boxW - 1, by, bx + boxW, by + boxH, borderColor);

            // Nombre de la tecla arriba
            int keyCode = (laneKeys != null && i < laneKeys.length) ? laneKeys[i] : 0;
            String keyName = getShortKeyName(keyCode);
            int keyColor = (glow > 0.2f) ? 0xFFFFFFFF : 0xFFCFD8DC;
            g.drawCenteredString(font, keyName, bx + boxW / 2, by + 5, keyColor);

            // Contador de pulsaciones abajo
            String countStr = String.valueOf(this.pressCounts[i]);
            int countColor = (glow > 0.2f) ? 0xFFFFFFFF : 0xFF90A4AE;
            g.drawCenteredString(font, countStr, bx + boxW / 2, by + boxH - 12, countColor);
        }
    }

    private static String getShortKeyName(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) return "SPC";
        try {
            InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(keyCode);
            String name = key.getDisplayName().getString().toUpperCase(Locale.ROOT);
            if (name.length() > 3) {
                name = name.substring(0, 3);
            }
            return name;
        } catch (Exception e) {
            return "?";
        }
    }
}
