package com.vsrcraft.client.gui.renderer;

import com.vsrcraft.client.config.LaneTheme;
import com.vsrcraft.client.config.RhythmConfig;
import com.vsrcraft.client.domain.Note;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public class NoteRenderer {

    // Mantener sobrecarga con long por compatibilidad
    public static void renderNotes(GuiGraphics graphics, List<Note> notes, long currentSongTimeMs, int boardStartX, int hitLineY, int screenHeight, int laneCount) {
        renderNotes(graphics, notes, (double) currentSongTimeMs, boardStartX, hitLineY, screenHeight, laneCount);
    }

    public static void renderNotes(GuiGraphics graphics, List<Note> notes, double currentSongTimeMs, int boardStartX, int hitLineY, int screenHeight, int laneCount) {
        int hitCenterY = hitLineY + 2; // Centro de la línea de impacto (grosor 4px)
        int halfNote = RhythmConfig.NOTE_HEIGHT / 2;

        for (Note note : notes) {
            if (note.isMissed()) continue;

            boolean isHold = note.getType() == Note.Type.HOLD && note.getDurationMs() > 0;
            long endOffsetMs = note.getOffsetMs() + note.getDurationMs();

            // Si es TAP y ya fue golpeada, o si es HOLD y ya terminó su duración, no renderizar
            if (note.isHit() && (!isHold || currentSongTimeMs >= endOffsetMs)) {
                continue;
            }

            int noteX = boardStartX + (note.getLane() * RhythmConfig.LANE_WIDTH) + 2;
            int noteWidth = RhythmConfig.LANE_WIDTH - 4;
            int noteColor = LaneTheme.getLaneColor(note.getLane(), laneCount);

            // Renderizar cola de nota HOLD (si aplica)
            if (isHold) {
                double headTimeMs = note.isHit() ? Math.max(note.getOffsetMs(), currentSongTimeMs) : note.getOffsetMs();
                double timeUntilHead = headTimeMs - currentSongTimeMs;
                double timeUntilTail = endOffsetMs - currentSongTimeMs;

                int headY = Math.round(hitCenterY - (float) (timeUntilHead * RhythmConfig.DEFAULT_SCROLL_SPEED));
                int tailY = Math.round(hitCenterY - (float) (timeUntilTail * RhythmConfig.DEFAULT_SCROLL_SPEED));

                if (headY >= 0 && tailY <= screenHeight && headY > tailY) {
                    int clampedTailY = Math.max(0, tailY);
                    int clampedHeadY = Math.min(screenHeight, headY);
                    int holdColor = (noteColor & 0x00FFFFFF) | 0x88000000; // Semi-transparente para la cola
                    graphics.fill(noteX + 4, clampedTailY, noteX + noteWidth - 4, clampedHeadY, holdColor);
                }
            }

            // Renderizar cabeza de la nota (centrada exactamente sobre hitCenterY cuando timeUntilHit == 0)
            double effectiveNoteTimeMs = (note.isHit() && isHold) ? currentSongTimeMs : note.getOffsetMs();
            double timeUntilHit = effectiveNoteTimeMs - currentSongTimeMs;
            int centerY = Math.round(hitCenterY - (float) (timeUntilHit * RhythmConfig.DEFAULT_SCROLL_SPEED));
            int noteTopY = centerY - halfNote;
            int noteBottomY = noteTopY + RhythmConfig.NOTE_HEIGHT;

            if (noteBottomY >= 0 && noteTopY <= screenHeight) {
                // Cuerpo de la nota
                graphics.fill(noteX, noteTopY, noteX + noteWidth, noteBottomY, noteColor);
                // Borde superior brillante
                graphics.fill(noteX, noteTopY, noteX + noteWidth, noteTopY + 2, 0xFFFFFFFF);
            }
        }
    }
}
