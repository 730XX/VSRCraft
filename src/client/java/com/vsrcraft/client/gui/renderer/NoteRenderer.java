package com.vsrcraft.client.gui.renderer;

import com.vsrcraft.client.config.LaneTheme;
import com.vsrcraft.client.config.RhythmConfig;
import com.vsrcraft.client.domain.Note;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public class NoteRenderer {
    public static void renderNotes(GuiGraphics graphics, List<Note> notes, long currentSongTimeMs, int boardStartX, int hitLineY, int screenHeight, int laneCount) {
        for (Note note : notes) {
            if (note.isHit() || note.isMissed()) continue;

            long timeUntilHit = note.getOffsetMs() - currentSongTimeMs;
            int noteY = (int) (hitLineY - (timeUntilHit * RhythmConfig.DEFAULT_SCROLL_SPEED));

            if (noteY >= -RhythmConfig.NOTE_HEIGHT && noteY <= screenHeight) {
                int noteX = boardStartX + (note.getLane() * RhythmConfig.LANE_WIDTH) + 2;
                int noteColor = LaneTheme.getLaneColor(note.getLane(), laneCount);

                // Cuerpo
                graphics.fill(noteX, noteY, noteX + RhythmConfig.LANE_WIDTH - 4, noteY + RhythmConfig.NOTE_HEIGHT, noteColor);
                // Borde superior
                graphics.fill(noteX, noteY, noteX + RhythmConfig.LANE_WIDTH - 4, noteY + 2, 0xFFFFFFFF);
            }
        }
    }
}
