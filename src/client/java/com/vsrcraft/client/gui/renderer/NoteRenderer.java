package com.vsrcraft.client.gui.renderer;

import com.vsrcraft.client.config.LaneTheme;
import com.vsrcraft.client.config.RhythmConfig;
import com.vsrcraft.client.config.VSRCraftSettings;
import com.vsrcraft.client.domain.Note;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public class NoteRenderer {

    public static void renderNotes(GuiGraphics graphics, List<Note> notes, long currentSongTimeMs, int boardStartX, int hitLineY, int screenHeight, int laneCount) {
        renderNotes(graphics, notes, (double) currentSongTimeMs, boardStartX, hitLineY, screenHeight, laneCount);
    }

    public static void renderNotes(GuiGraphics graphics, List<Note> notes, double currentSongTimeMs, int boardStartX, int hitLineY, int screenHeight, int laneCount) {
        renderNotes(graphics, notes, currentSongTimeMs, boardStartX, hitLineY, screenHeight, laneCount, 1.0f);
    }

    public static void renderNotes(GuiGraphics graphics, List<Note> notes, double currentSongTimeMs, int boardStartX, int hitLineY, int screenHeight, int laneCount, float playbackRate) {
        renderNotes(graphics, notes, currentSongTimeMs, boardStartX, hitLineY, screenHeight, laneCount, playbackRate, 0);
    }

    public static void renderNotes(GuiGraphics graphics, List<Note> notes, double currentSongTimeMs, int boardStartX, int hitLineY, int screenHeight, int laneCount, float playbackRate, int firstActiveIdx) {
        int noteHeight = RhythmConfig.getNoteHeight();
        // Dividimos por playbackRate para mantener constante la velocidad visual de caída (px/seg real)
        // y que al subir el rate solo aumente la densidad entre notas.
        float scrollSpeed = Math.max(0.02f, RhythmConfig.getScrollSpeed() / Math.max(0.5f, playbackRate));
        int laneWidth = RhythmConfig.getLaneWidth(laneCount);
        int halfNote = noteHeight / 2;
        VSRCraftSettings.NoteShape shape = VSRCraftSettings.get().getNoteShape();
        int lnCutMs = VSRCraftSettings.get().lnCutMs;
        int shapeRadius = Math.max(6, Math.min((laneWidth - 8) / 2, 18));
        // El punto 0ms descansa sobre hitLineY (en BARS, el borde inferior de la nota toca hitLineY)
        int hitCenterY = (shape == VSRCraftSettings.NoteShape.BARS)
                ? (hitLineY - halfNote)
                : (hitLineY - shapeRadius);

        // Culling superior O(1): cualquier nota cuyo offsetMs supere este límite está todavía por encima de y = -32
        double maxVisibleSongTimeMs = currentSongTimeMs + ((hitCenterY + 32) / (double) scrollSpeed);
        int totalNotes = notes.size();

        for (int i = Math.max(0, firstActiveIdx); i < totalNotes; i++) {
            Note note = notes.get(i);
            if (note.getOffsetMs() > maxVisibleSongTimeMs) {
                break; // Al estar ordenadas por tiempo, todas las siguientes están aún más arriba fuera de pantalla
            }

            // Notas completadas con éxito ya no se dibujan
            if (note.isCompleted()) continue;

            boolean isHold = note.getType() == Note.Type.HOLD && note.getDurationMs() > 0;
            long endOffsetMs = note.getTailOffsetMs();

            // Si es TAP y ya se golpeó o falló, o si es HOLD y ya terminó su tiempo en la pantalla
            if (!isHold) {
                if (note.isHit() || note.isMissed()) continue;
            } else {
                if (currentSongTimeMs >= endOffsetMs + 100) continue;
            }

            int noteX = boardStartX + (note.getLane() * laneWidth) + 2;
            int noteWidth = laneWidth - 4;
            int baseColor = LaneTheme.getLaneColor(note.getLane(), laneCount);

            boolean isHolding = note.isHolding();
            boolean isDropped = note.isDropped();

            // Color de renderizado: si fue cortada o fallada, se oscurece a un gris tenue
            int noteColor = isDropped ? 0x66556677 : baseColor;

            // 1. Renderizar cola / cuerpo de la nota HOLD (con Percy LN visual, punta redondeada y precisión sub-píxel)
            if (isHold) {
                double headTimeMs = isHolding ? currentSongTimeMs : note.getOffsetMs();
                double visualEndOffsetMs = Math.max(headTimeMs, endOffsetMs - lnCutMs);
                double timeUntilHead = headTimeMs - currentSongTimeMs;
                double timeUntilTail = visualEndOffsetMs - currentSongTimeMs;

                double exactHeadY = hitCenterY - (timeUntilHead * scrollSpeed);
                double exactTailY = hitCenterY - (timeUntilTail * scrollSpeed);
                int baseTailY = (int) Math.floor(exactTailY);
                float tailFracY = (float) (exactTailY - baseTailY);
                int adjHeadY = (int) Math.round(exactHeadY - tailFracY);

                if (adjHeadY >= 0 && baseTailY <= screenHeight && adjHeadY > baseTailY) {
                    int holdAlphaColor = isDropped ? 0x33445566 : ((noteColor & 0x00FFFFFF) | 0x88000000);
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(0.0f, tailFracY);
                    NoteShapeRenderer.drawRoundedHoldBody(
                            graphics,
                            noteX + 4,
                            noteX + noteWidth - 4,
                            baseTailY,
                            adjHeadY,
                            0,
                            screenHeight,
                            holdAlphaColor,
                            isHolding
                    );
                    graphics.pose().popMatrix();
                }
            }

            // 2. Renderizar cabeza de la nota según el shape elegido con traslación sub-píxel (float)
            // Si está siendo sostenida, la cabeza se ancla fijamente en la línea de impacto
            double effectiveNoteTimeMs = isHolding ? currentSongTimeMs : note.getOffsetMs();
            double timeUntilHit = effectiveNoteTimeMs - currentSongTimeMs;
            double exactCenterY = hitCenterY - (timeUntilHit * scrollSpeed);
            int baseCenterY = (int) Math.floor(exactCenterY);

            // Culling antes de tocar la matriz graphics.pose()
            int cullHalfH = (shape == VSRCraftSettings.NoteShape.BARS) ? halfNote + 2 : shapeRadius + 2;
            if (baseCenterY + cullHalfH < 0 || baseCenterY - cullHalfH > screenHeight) {
                continue;
            }

            float fracY = (float) (exactCenterY - baseCenterY);
            int centerX = noteX + noteWidth / 2;

            graphics.pose().pushMatrix();
            graphics.pose().translate(0.0f, fracY);
            renderNoteHead(graphics, shape, noteX, noteWidth, centerX, baseCenterY, halfNote, noteHeight, shapeRadius,
                    note.getLane(), laneCount, noteColor, isHolding, screenHeight);
            graphics.pose().popMatrix();
        }
    }

    private static void renderNoteHead(
            GuiGraphics graphics,
            VSRCraftSettings.NoteShape shape,
            int noteX,
            int noteWidth,
            int centerX,
            int centerY,
            int halfNote,
            int noteHeight,
            int shapeRadius,
            int lane,
            int laneCount,
            int noteColor,
            boolean isHolding,
            int screenHeight
    ) {
        switch (shape) {
            case CIRCLES -> {
                if (centerY + shapeRadius >= 0 && centerY - shapeRadius <= screenHeight) {
                    NoteShapeRenderer.drawFilledCircle(graphics, centerX, centerY, shapeRadius, noteColor);
                    // Anillo interior sutil estilo osu!mania
                    NoteShapeRenderer.drawCircleOutline(graphics, centerX, centerY, Math.max(3, shapeRadius - 2), 1, 0x55FFFFFF);
                }
            }
            case ARROWS -> {
                if (centerY + shapeRadius >= 0 && centerY - shapeRadius <= screenHeight) {
                    NoteShapeRenderer.ArrowDir dir = NoteShapeRenderer.getArrowDirection(lane, laneCount);
                    NoteShapeRenderer.drawArrow(graphics, centerX, centerY, shapeRadius, dir, noteColor, false);
                }
            }
            case BARS -> {
                int noteTopY = centerY - halfNote;
                int noteBottomY = noteTopY + noteHeight;
                if (noteBottomY >= 0 && noteTopY <= screenHeight) {
                    graphics.fill(noteX, noteTopY, noteX + noteWidth, noteBottomY, noteColor);
                }
            }
        }
    }
}
