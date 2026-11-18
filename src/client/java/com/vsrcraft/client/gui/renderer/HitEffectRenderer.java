package com.vsrcraft.client.gui.renderer;

import com.vsrcraft.client.config.RhythmConfig;
import com.vsrcraft.client.config.VSRCraftSettings;
import com.vsrcraft.client.domain.Judgement;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Renderizador de efectos de golpe "Voxel Sparks" (chispas cúbicas de Minecraft) y parpadeo de fallo.
 * Las partículas se dispersan por debajo de la línea de impacto para mantener el 100% de visibilidad
 * sobre las notas que caen desde arriba, incluso en mapas rápidos de 5★+.
 */
public class HitEffectRenderer {

    private static class VoxelSpark {
        float x;
        float y;
        float vx;
        float vy;
        int size;
        int rgb;
        long spawnNano;
        double lifeMs;
    }

    private static class RadialGlow {
        int centerX;
        int centerY;
        float maxRadius;
        int rgb;
        long spawnNano;
        double lifeMs;
    }

    private static final int MAX_SPARKS = 140;
    private static final int MAX_GLOWS = 32;
    private static final double MISS_FLASH_DURATION_MS = 180.0;
    private static final long HOLD_SPARK_INTERVAL_NS = 75_000_000L;

    private final List<VoxelSpark> sparks = new ArrayList<>(64);
    private final List<RadialGlow> glows = new ArrayList<>(16);
    private final long[] missFlashNano = new long[8];
    private final long[] lastHoldSparkNano = new long[8];
    private long lastUpdateNano = System.nanoTime();

    public void clear() {
        this.sparks.clear();
        this.glows.clear();
        for (int i = 0; i < this.missFlashNano.length; i++) {
            this.missFlashNano[i] = 0L;
            this.lastHoldSparkNano[i] = 0L;
        }
        this.lastUpdateNano = System.nanoTime();
    }

    public void spawnHitEffect(int lane, int laneCount, Judgement judgement, int boardStartX, int hitLineY) {
        if (!VSRCraftSettings.get().enableHitEffects || judgement == null || lane < 0) {
            return;
        }

        if (judgement == Judgement.MISS) {
            if (lane < this.missFlashNano.length) {
                this.missFlashNano[lane] = System.nanoTime();
            }
            return;
        }

        int count = switch (judgement) {
            case MARVELOUS -> 6;
            case PERFECT -> 4;
            case GREAT -> 3;
            case BAD -> 2;
            default -> 0;
        };

        int laneWidth = RhythmConfig.getLaneWidth(laneCount);
        int laneCenterX = boardStartX + (lane * laneWidth) + (laneWidth / 2);
        int spawnY = hitLineY + 4;
        long now = System.nanoTime();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Glow radial compacto del color del juicio centrado en el punto de impacto
        if (this.glows.size() >= MAX_GLOWS) {
            this.glows.remove(0);
        }
        RadialGlow glow = new RadialGlow();
        glow.centerX = laneCenterX;
        glow.centerY = hitLineY + 2;
        glow.maxRadius = switch (judgement) {
            case MARVELOUS -> 26.0f;
            case PERFECT -> 24.0f;
            case GREAT -> 22.0f;
            case BAD -> 20.0f;
            default -> 10.0f;
        };
        glow.rgb = pickSparkColor(judgement, 0);
        glow.spawnNano = now;
        glow.lifeMs = (judgement == Judgement.MARVELOUS) ? 180.0 : 150.0;
        this.glows.add(glow);

        for (int i = 0; i < count; i++) {
            if (this.sparks.size() >= MAX_SPARKS) {
                this.sparks.remove(0);
            }
            VoxelSpark s = new VoxelSpark();
            s.x = laneCenterX + rng.nextFloat(-laneWidth * 0.36f, laneWidth * 0.36f);
            s.y = spawnY + rng.nextFloat(0.0f, 3.0f);
            s.vx = rng.nextFloat(-65.0f, 65.0f);
            s.vy = rng.nextFloat(25.0f, 95.0f);
            s.size = (judgement == Judgement.MARVELOUS && i % 2 == 0) ? 3 : 2;
            s.rgb = pickSparkColor(judgement, i);
            s.spawnNano = now;
            s.lifeMs = (judgement == Judgement.MARVELOUS)
                    ? rng.nextDouble(210.0, 280.0)
                    : rng.nextDouble(170.0, 240.0);
            this.sparks.add(s);
        }
    }

    public void spawnHoldTick(int lane, int laneCount, int boardStartX, int hitLineY) {
        if (!VSRCraftSettings.get().enableHitEffects || lane < 0 || lane >= this.lastHoldSparkNano.length) {
            return;
        }
        long now = System.nanoTime();
        if (now - this.lastHoldSparkNano[lane] < HOLD_SPARK_INTERVAL_NS) {
            return;
        }
        this.lastHoldSparkNano[lane] = now;

        if (this.sparks.size() >= MAX_SPARKS) {
            this.sparks.remove(0);
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int laneWidth = RhythmConfig.getLaneWidth(laneCount);
        int laneCenterX = boardStartX + (lane * laneWidth) + (laneWidth / 2);

        VoxelSpark s = new VoxelSpark();
        s.x = laneCenterX + rng.nextFloat(-laneWidth * 0.30f, laneWidth * 0.30f);
        s.y = hitLineY + 4;
        s.vx = rng.nextFloat(-40.0f, 40.0f);
        s.vy = rng.nextFloat(30.0f, 70.0f);
        s.size = 2;
        s.rgb = rng.nextBoolean() ? 0x00FFFF : 0xFFD54F;
        s.spawnNano = now;
        s.lifeMs = rng.nextDouble(140.0, 200.0);
        this.sparks.add(s);
    }

    public void render(GuiGraphics graphics, int boardStartX, int hitLineY, int screenHeight, int laneCount) {
        if (!VSRCraftSettings.get().enableHitEffects) {
            return;
        }

        long now = System.nanoTime();
        float dtSec = (float) Math.min(0.05, Math.max(0.0, (now - this.lastUpdateNano) / 1_000_000_000.0));
        this.lastUpdateNano = now;

        int laneWidth = RhythmConfig.getLaneWidth(laneCount);
        // 1. Parpadeo rojo tenue en la base del carril tras un MISS
        int baseTop = hitLineY + 4;
        if (baseTop < screenHeight) {
            for (int lane = 0; lane < Math.min(laneCount, this.missFlashNano.length); lane++) {
                long flashStart = this.missFlashNano[lane];
                if (flashStart == 0L) continue;
                double elapsedMs = (now - flashStart) / 1_000_000.0;
                if (elapsedMs >= MISS_FLASH_DURATION_MS) {
                    this.missFlashNano[lane] = 0L;
                    continue;
                }
                float alphaNorm = 1.0f - (float) (elapsedMs / MISS_FLASH_DURATION_MS);
                int alpha = Math.round(alphaNorm * alphaNorm * 90.0f); // Max ~35% opacidad, muy sutil
                if (alpha > 0) {
                    int lx = boardStartX + (lane * laneWidth) + 1;
                    int lw = laneWidth - 1;
                    graphics.fill(lx, baseTop, lx + lw, screenHeight, (alpha << 24) | 0xFF1744);
                }
            }
        }

        // 2. Renderizar pequeños glows radiales del color del juicio
        Iterator<RadialGlow> glowIt = this.glows.iterator();
        while (glowIt.hasNext()) {
            RadialGlow g = glowIt.next();
            double elapsedMs = (now - g.spawnNano) / 1_000_000.0;
            if (elapsedMs >= g.lifeMs) {
                glowIt.remove();
                continue;
            }
            float t = (float) (elapsedMs / g.lifeMs);
            float easeOut = 1.0f - (1.0f - t) * (1.0f - t);
            float currentRadius = g.maxRadius * (0.55f + 0.45f * easeOut);
            float fade = (1.0f - t) * (1.0f - t);
            drawRadialGlow(graphics, g.centerX, g.centerY, currentRadius, g.rgb, fade);
        }

        // 3. Actualizar y renderizar chispas cúbicas (Voxel Sparks) debajo de la línea de impacto
        Iterator<VoxelSpark> it = this.sparks.iterator();
        while (it.hasNext()) {
            VoxelSpark s = it.next();
            double elapsedMs = (now - s.spawnNano) / 1_000_000.0;
            if (elapsedMs >= s.lifeMs) {
                it.remove();
                continue;
            }

            s.x += s.vx * dtSec;
            s.y += s.vy * dtSec;
            s.vy += 260.0f * dtSec; // Gravedad ligera hacia abajo

            float lifeRatio = (float) (elapsedMs / s.lifeMs);
            int alpha = Math.max(0, Math.min(255, Math.round((1.0f - lifeRatio * lifeRatio) * 255.0f)));
            int px = Math.round(s.x);
            int py = Math.round(s.y);
            if (py >= hitLineY + 2 && py < screenHeight) {
                graphics.fill(px, py, px + s.size, py + s.size, (alpha << 24) | (s.rgb & 0x00FFFFFF));
            }
        }
    }

    private static void drawRadialGlow(GuiGraphics g, int cx, int cy, float radius, int rgb, float fade) {
        int rgbMask = rgb & 0x00FFFFFF;
        float radiusX = radius * 1.35f;
        float radiusY = radius * 0.45f;
        int steps = 3;
        for (int step = steps; step >= 1; step--) {
            float frac = (float) step / (float) steps;
            int rx = Math.max(2, Math.round(radiusX * frac));
            int ry = Math.max(1, Math.round(radiusY * frac));
            float layerAlpha = (1.0f - frac * 0.68f) * 0.25f * fade;
            int alpha = Math.max(0, Math.min(255, Math.round(layerAlpha * 255.0f)));
            if (alpha <= 1) continue;
            int color = (alpha << 24) | rgbMask;
            drawFastOval(g, cx, cy, rx, ry, color);
        }
    }

    private static void drawFastOval(GuiGraphics g, int cx, int cy, int rx, int ry, int color) {
        if (ry <= 2) {
            g.fill(cx - rx, cy - ry, cx + rx + 1, cy + ry + 1, color);
            return;
        }
        // Aproximación ovalada en 3 bandas horizontales fusionadas (sin Math.sqrt ni fill por fila)
        int midHalfY = Math.max(1, (ry * 45) / 100);
        int shoulderHalfY = Math.max(midHalfY + 1, (ry * 80) / 100);
        int shoulderRx = (rx * 84) / 100;
        int tipRx = (rx * 52) / 100;

        // Banda central
        g.fill(cx - rx, cy - midHalfY, cx + rx + 1, cy + midHalfY + 1, color);
        // Bandas intermedias (arriba y abajo)
        g.fill(cx - shoulderRx, cy - shoulderHalfY, cx + shoulderRx + 1, cy - midHalfY, color);
        g.fill(cx - shoulderRx, cy + midHalfY + 1, cx + shoulderRx + 1, cy + shoulderHalfY + 1, color);
        // Tapas superior e inferior
        if (ry > shoulderHalfY) {
            g.fill(cx - tipRx, cy - ry, cx + tipRx + 1, cy - shoulderHalfY, color);
            g.fill(cx - tipRx, cy + shoulderHalfY + 1, cx + tipRx + 1, cy + ry + 1, color);
        }
    }

    private static int pickSparkColor(Judgement judgement, int index) {
        return switch (judgement) {
            case MARVELOUS -> (index % 2 == 0) ? 0x00FFFF : 0xFFFFFF;
            case PERFECT -> (index % 2 == 0) ? 0xFFD54F : 0xFFEE88;
            case GREAT -> 0x66FF88;
            case BAD -> 0xFF8A44;
            default -> 0xFF3355;
        };
    }
}
