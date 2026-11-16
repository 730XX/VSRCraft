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
        int noteHeight = RhythmConfig.getNoteHeight();
        // Dividimos por playbackRate para mantener constante la velocidad visual de caída (px/seg real)
        // y que al subir el rate solo aumente la densidad entre notas.
        float scrollSpeed = RhythmConfig.getScrollSpeed() / Math.max(0.5f, playbackRate);
        int laneWidth = RhythmConfig.getLaneWidth(laneCount);
        int halfNote = noteHeight / 2;
        VSRCraftSettings.NoteShape shape = VSRCraftSettings.get().getNoteShape();
        int lnCutMs = VSRCraftSettings.get().lnCutMs;
        int shapeRadius = Math.max(6, Math.min((laneWidth - 8) / 2, 18));
        // En Círculos/Flechas, los receptores descansan encima de hitLineY, así que el punto 0ms es su centro.
        int hitCenterY = (shape == VSRCraftSettings.NoteShape.BARS)
                ? (hitLineY + 2)
                : (hitLineY - shapeRadius);

        for (Note note : notes) {
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

            // 1. Renderizar cola / cuerpo de la nota HOLD (con Percy LN visual y punta redondeada)
            if (isHold) {
                double headTimeMs = isHolding ? currentSongTimeMs : note.getOffsetMs();
                double visualEndOffsetMs = Math.max(headTimeMs, endOffsetMs - lnCutMs);
                double timeUntilHead = headTimeMs - currentSongTimeMs;
                double timeUntilTail = visualEndOffsetMs - currentSongTimeMs;

                int headY = Math.round(hitCenterY - (float) (timeUntilHead * scrollSpeed));
                int tailY = Math.round(hitCenterY - (float) (timeUntilTail * scrollSpeed));

                if (headY >= 0 && tailY <= screenHeight && headY > tailY) {
                    int holdAlphaColor = isDropped ? 0x33445566 : ((noteColor & 0x00FFFFFF) | 0x88000000);
                    NoteShapeRenderer.drawRoundedHoldBody(
                            graphics,
                            noteX + 4,
                            noteX + noteWidth - 4,
                            tailY,
                            headY,
                            0,
                            screenHeight,
                            holdAlphaColor,
                            isHolding
                    );
                }
            }

            // 2. Renderizar cabeza de la nota según el shape elegido
            // Si está siendo sostenida, la cabeza se ancla fijamente en la línea de impacto
            double effectiveNoteTimeMs = isHolding ? currentSongTimeMs : note.getOffsetMs();
            double timeUntilHit = effectiveNoteTimeMs - currentSongTimeMs;
            int centerY = Math.round(hitCenterY - (float) (timeUntilHit * scrollSpeed));
            int centerX = noteX + noteWidth / 2;

            renderNoteHead(graphics, shape, noteX, noteWidth, centerX, centerY, halfNote, noteHeight, shapeRadius,
                    note.getLane(), laneCount, noteColor, isHolding, screenHeight);
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
                    int innerRingColor = isHolding ? 0xFFFFFFFF : 0x55FFFFFF;
                    NoteShapeRenderer.drawCircleOutline(graphics, centerX, centerY, Math.max(3, shapeRadius - 2), 1, innerRingColor);
                }
            }
            case ARROWS -> {
                if (centerY + shapeRadius >= 0 && centerY - shapeRadius <= screenHeight) {
                    NoteShapeRenderer.ArrowDir dir = NoteShapeRenderer.getArrowDirection(lane, laneCount);
                    NoteShapeRenderer.drawArrow(graphics, centerX, centerY, shapeRadius, dir, noteColor, false);
                    if (isHolding) {
                        NoteShapeRenderer.drawArrow(graphics, centerX, centerY, Math.max(3, shapeRadius - 3), dir, 0xCCFFFFFF, true);
                    }
                }
            }
            case BARS -> {
                int noteTopY = centerY - halfNote;
                int noteBottomY = noteTopY + noteHeight;
                if (noteBottomY >= 0 && noteTopY <= screenHeight) {
                    graphics.fill(noteX, noteTopY, noteX + noteWidth, noteBottomY, noteColor);
                    if (isHolding) {
                        graphics.fill(noteX + 1, noteTopY + 1, noteX + noteWidth - 1, noteTopY + 3, 0xFFFFFFFF);
                    }
                }
            }
        }
    }
}
