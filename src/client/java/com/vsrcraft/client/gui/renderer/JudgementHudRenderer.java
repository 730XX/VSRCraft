package com.vsrcraft.client.gui.renderer;

import com.vsrcraft.client.domain.Judgement;
import com.vsrcraft.client.domain.ScoreTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class JudgementHudRenderer {
    public static void renderHud(GuiGraphics graphics, Font font, ScoreTracker tracker, Judgement lastJudgement, int boardStartX, int hitLineY, int screenWidth, int screenHeight) {
        // 1. SCORE arriba en el borde izquierdo (debajo del título del mapa)
        graphics.drawString(font, "SCORE: " + tracker.getScore(), 12, 22, 0xFFFFFFFF, true);

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
    }
}
