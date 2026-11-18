package com.vsrcraft.client.gui.renderer;

import com.vsrcraft.client.domain.Judgement;
import com.vsrcraft.client.domain.ScoreTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public class JudgementHudRenderer {
    public static void renderHud(GuiGraphics graphics, Font font, ScoreTracker tracker, Judgement lastJudgement, int boardStartX, int hitLineY, int screenWidth, int screenHeight) {
        // 1. SCORE arriba en el borde izquierdo (debajo del título del mapa)
        graphics.drawString(font, String.format(java.util.Locale.ROOT, "SCORE: %,d", tracker.getScore()), 12, 22, 0xFFFFFFFF, true);

        // 2. COMBO en el centro de la pantalla, un poco arriba
        int combo = tracker.getCombo();
        if (combo > 0) {
            String comboText = combo + " COMBO";
            int comboWidth = font.width(comboText);
            int comboX = (screenWidth - comboWidth) / 2;
            int comboY = (screenHeight / 2) - 30; // En el medio, un poco arriba
            graphics.drawString(font, comboText, comboX, comboY, 0xFF00FFCC, true);
        }

        // 3. JUICIO (MARVELOUS, PERFECT, GREAT, BAD, MISS) justo debajo del COMBO
        if (lastJudgement != null) {
            String label = lastJudgement.getLabel();
            int judWidth = font.width(label);
            int judX = (screenWidth - judWidth) / 2;
            int judY = (screenHeight / 2) - 16; // Justo debajo del combo
            graphics.drawString(font, label, judX, judY, lastJudgement.getColor(), true);
        }

        // 4. Hit Error Bar en vivo (Early <- | -> Late) debajo del juicio
        renderLiveHitErrorBar(graphics, tracker, screenWidth / 2, (screenHeight / 2) - 2);
    }

    private static void renderLiveHitErrorBar(GuiGraphics g, ScoreTracker tracker, int centerX, int barY) {
        int barW = 132;
        int barH = 4;
        int leftX = centerX - (barW / 2);

        // Fondo oscuro y línea central de 0ms
        g.fill(leftX, barY, leftX + barW, barY + barH, 0x77080B12);
        // Bandas sutiles de referencia (±45ms)
        int halfW = barW / 2;
        int px45 = Math.round((45.0f / 140.0f) * halfW);
        g.fill(centerX - px45, barY + 1, centerX + px45, barY + barH - 1, 0x2200E5FF);
        g.fill(centerX - 1, barY - 2, centerX + 1, barY + barH + 2, 0xCCFFFFFF);

        List<ScoreTracker.HitSample> samples = tracker.getHitSamples();
        if (samples.isEmpty()) return;

        int maxVisible = 24;
        int startIdx = Math.max(0, samples.size() - maxVisible);
        int count = samples.size() - startIdx;
        double sumErr = 0.0;

        for (int i = startIdx; i < samples.size(); i++) {
            ScoreTracker.HitSample sample = samples.get(i);
            sumErr += sample.errorMs();
            float ageRatio = (float) (i - startIdx + 1) / (float) count; // más reciente = más brillante
            int alpha = Math.max(60, Math.min(245, Math.round(ageRatio * 245)));
            int color = (sample.judgement().getColor() & 0x00FFFFFF) | (alpha << 24);

            double clamped = Math.max(-140.0, Math.min(140.0, sample.errorMs()));
            int tickX = centerX + (int) Math.round((clamped / 140.0) * (halfW - 2));
            g.fill(tickX, barY - 2, tickX + 2, barY + barH + 2, color);
        }

        // Aguja promedio de los últimos hits
        double mean = Math.max(-140.0, Math.min(140.0, sumErr / count));
        int avgX = centerX + (int) Math.round((mean / 140.0) * (halfW - 2));
        g.fill(avgX - 1, barY - 4, avgX + 1, barY - 1, 0xFFFFD54F);
        g.fill(avgX, barY - 1, avgX + 1, barY + barH + 2, 0xFFFFD54F);
    }
}
