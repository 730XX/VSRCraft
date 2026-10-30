package com.vsrcraft.client.gui.renderer;

import com.vsrcraft.client.domain.Judgement;
import com.vsrcraft.client.domain.ScoreTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class JudgementHudRenderer {
    public static void renderHud(GuiGraphics graphics, Font font, ScoreTracker tracker, Judgement lastJudgement, int boardStartX, int hitLineY, int screenWidth, int screenHeight) {
        // Score y Combo
        graphics.drawString(font, "SCORE: " + tracker.getScore(), boardStartX, 20, 0xFFFFFFFF, true);
        graphics.drawString(font, "COMBO: " + tracker.getCombo(), boardStartX, 35, 0xFF00FFCC, true);

        // Juicio actual
        if (lastJudgement != null) {
            String label = lastJudgement.getLabel();
            int judWidth = font.width(label);
            int judX = (screenWidth - judWidth) / 2;
            graphics.drawString(font, label, judX, hitLineY - 45, lastJudgement.getColor(), true);
        }
    }
}

