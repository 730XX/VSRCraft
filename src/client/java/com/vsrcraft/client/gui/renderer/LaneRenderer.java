package com.vsrcraft.client.gui.renderer;

import com.vsrcraft.client.config.LaneTheme;
import com.vsrcraft.client.config.RhythmConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class LaneRenderer {
    public static void renderLanes(GuiGraphics graphics, Font font, int boardStartX, int boardWidth, int hitLineY, int screenHeight, int laneCount, boolean[] lanePressed) {
        // Fondo de la pista
        graphics.fill(boardStartX - 2, 0, boardStartX + boardWidth + 2, screenHeight, LaneTheme.TRACK_BACKGROUND);

        // Separadores y luces de presión
        for (int i = 0; i < laneCount; i++) {
            int laneX = boardStartX + (i * RhythmConfig.LANE_WIDTH);
            graphics.fill(laneX, 0, laneX + 1, screenHeight, LaneTheme.LANE_DIVIDER);

            if (lanePressed != null && i < lanePressed.length && lanePressed[i]) {
                graphics.fill(laneX + 1, 0, laneX + RhythmConfig.LANE_WIDTH, screenHeight, LaneTheme.LANE_PRESS_GLOW);
            }
        }
        graphics.fill(boardStartX + boardWidth, 0, boardStartX + boardWidth + 1, screenHeight, LaneTheme.LANE_DIVIDER);

        // Línea de impacto (Hit Line)
        graphics.fill(boardStartX, hitLineY, boardStartX + boardWidth, hitLineY + 4, LaneTheme.HIT_LINE);
    }
}

