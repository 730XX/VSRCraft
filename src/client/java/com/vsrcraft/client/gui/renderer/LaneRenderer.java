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
        VSRCraftSettings.NoteShape shape = VSRCraftSettings.get().getNoteShape();
        int receptorRadius = Math.max(6, Math.min((laneWidth - 8) / 2, 18));
        int hitCenterY = hitLineY + 2;

        for (int i = 0; i < laneCount; i++) {
            int laneX = boardStartX + (i * laneWidth);
            graphics.fill(laneX, 0, laneX + 1, screenHeight, dividerColor);

            boolean isPressed = lanePressed != null && i < lanePressed.length && lanePressed[i];
            if (allowGlow && isPressed) {
                graphics.fill(laneX + 1, 0, laneX + laneWidth, screenHeight, LaneTheme.LANE_PRESS_GLOW);
            }
        }
        graphics.fill(boardStartX + boardWidth, 0, boardStartX + boardWidth + 1, screenHeight, dividerColor);

        // Receptores según la forma de nota elegida
        if (shape == VSRCraftSettings.NoteShape.BARS) {
            graphics.fill(boardStartX, hitLineY, boardStartX + boardWidth, hitLineY + 4, LaneTheme.HIT_LINE);
            for (int i = 0; i < laneCount; i++) {
                boolean isPressed = lanePressed != null && i < lanePressed.length && lanePressed[i];
                if (isPressed) {
                    int laneX = boardStartX + (i * laneWidth);
                    int laneColor = LaneTheme.getLaneColor(i, laneCount);
                    graphics.fill(laneX + 2, hitLineY, laneX + laneWidth - 2, hitLineY + 4, laneColor);
                }
            }
        } else {
            // Línea de juicio en la base inferior donde inician los receptores
            int guideLineY = hitCenterY + receptorRadius;
            graphics.fill(boardStartX, guideLineY, boardStartX + boardWidth, guideLineY + 2, 0x66FFFFFF);
            for (int i = 0; i < laneCount; i++) {
                int laneCenterX = boardStartX + (i * laneWidth) + (laneWidth / 2);
                boolean isPressed = lanePressed != null && i < lanePressed.length && lanePressed[i];
                int laneColor = LaneTheme.getLaneColor(i, laneCount);

                if (shape == VSRCraftSettings.NoteShape.CIRCLES) {
                    if (isPressed) {
                        int fillAlpha = (laneColor & 0x00FFFFFF) | 0x55000000;
                        NoteShapeRenderer.drawFilledCircle(graphics, laneCenterX, hitCenterY, receptorRadius, fillAlpha);
                    }
                    int ringColor = isPressed ? laneColor : 0xAAFFFFFF;
                    NoteShapeRenderer.drawCircleOutline(graphics, laneCenterX, hitCenterY, receptorRadius, 2, ringColor);
                } else {
                    NoteShapeRenderer.ArrowDir dir = NoteShapeRenderer.getArrowDirection(i, laneCount);
                    if (isPressed) {
                        int fillAlpha = (laneColor & 0x00FFFFFF) | 0x66000000;
                        NoteShapeRenderer.drawArrow(graphics, laneCenterX, hitCenterY, receptorRadius, dir, fillAlpha, false);
                    }
                    int outlineColor = isPressed ? laneColor : 0xAAFFFFFF;
                    NoteShapeRenderer.drawArrow(graphics, laneCenterX, hitCenterY, receptorRadius, dir, outlineColor, true);
                }
            }
        }
    }
}
