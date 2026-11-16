package com.vsrcraft.client.gui.renderer;

import com.vsrcraft.client.config.LaneTheme;
import com.vsrcraft.client.config.RhythmConfig;
import com.vsrcraft.client.config.VSRCraftSettings;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class LaneRenderer {
    public static void renderLanes(GuiGraphics graphics, Font font, int boardStartX, int boardWidth, int hitLineY, int screenHeight, int laneCount, boolean[] lanePressed) {
        // Fondo de la pista con opacidad configurable
        graphics.fill(boardStartX - 2, 0, boardStartX + boardWidth + 2, screenHeight, LaneTheme.getTrackBackgroundColor());

        // Separadores y luces de presión (hit glow)
        int dividerColor = LaneTheme.getLaneDividerColor();
        boolean allowGlow = VSRCraftSettings.get().enableLaneGlow;

        int laneWidth = RhythmConfig.getLaneWidth(laneCount);
        for (int i = 0; i < laneCount; i++) {
            int laneX = boardStartX + (i * laneWidth);
            graphics.fill(laneX, 0, laneX + 1, screenHeight, dividerColor);

            if (allowGlow && lanePressed != null && i < lanePressed.length && lanePressed[i]) {
                graphics.fill(laneX + 1, 0, laneX + laneWidth, screenHeight, LaneTheme.LANE_PRESS_GLOW);
            }
        }
        graphics.fill(boardStartX + boardWidth, 0, boardStartX + boardWidth + 1, screenHeight, dividerColor);

        // Línea de impacto (Hit Line)
        graphics.fill(boardStartX, hitLineY, boardStartX + boardWidth, hitLineY + 4, LaneTheme.HIT_LINE);
    }
}
