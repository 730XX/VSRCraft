package com.vsrcraft.client.engine;

import com.vsrcraft.client.config.RhythmConfig;
import com.vsrcraft.client.domain.Beatmap;
import com.vsrcraft.client.domain.Judgement;
import com.vsrcraft.client.domain.Note;
import com.vsrcraft.client.domain.ScoreTracker;

import java.util.Arrays;

/**
 * Motor central de ritmo y juicios de VSRCraft.
 * Soporta evaluación precisa de Taps y mecánica completa de Long Notes (Hold Notes):
 * - Juicio inicial al presionar la cabeza (Head Hit).
 * - Mantenimiento activo durante la duración del cuerpo (Holding).
 * - Juicio de corte prematuro (MISS) si se suelta antes de tiempo.
 * - Juicio de liberación final (Tail Release) al soltar a tiempo o mantener hasta el final.
 */
public class RhythmEngine {
    private final Beatmap beatmap;
    private final ScoreTracker scoreTracker;
    private final int keyCount;
    private final Note[] activeHolds;
    private Judgement lastJudgement = null;

    public RhythmEngine(Beatmap beatmap) {
        this.beatmap = beatmap;
        this.scoreTracker = new ScoreTracker();
        this.keyCount = beatmap.getKeyCount();
        this.activeHolds = new Note[this.keyCount];
    }

    public void start(long ignored) {
        this.scoreTracker.reset();
        Arrays.fill(this.activeHolds, null);
        for (Note note : beatmap.getNotes()) {
            note.setHit(false);
            note.setMissed(false);
            note.setHoldState(Note.HoldState.NONE);
        }
    }

    public void update(long currentSongTimeMs) {
        // 1. Chequear notas cuya cabeza pasó de largo sin ser presionadas (Miss)
        for (Note note : beatmap.getNotes()) {
            if (!note.isHit() && !note.isMissed()) {
                long timeUntilHit = note.getOffsetMs() - currentSongTimeMs;
                if (timeUntilHit < -RhythmConfig.MAX_HIT_WINDOW_MS) {
                    note.setMissed(true);
                    if (note.getType() == Note.Type.HOLD) {
                        note.setHoldState(Note.HoldState.DROPPED);
                    }
                    this.lastJudgement = Judgement.MISS;
                    this.scoreTracker.registerJudgement(Judgement.MISS);
                }
            }
        }

        // 2. Chequear notas HOLD activas que el jugador mantuvo presionadas hasta el final con éxito
        for (int lane = 0; lane < keyCount; lane++) {
            Note holdNote = this.activeHolds[lane];
            if (holdNote != null && holdNote.isHolding()) {
                if (currentSongTimeMs >= holdNote.getTailOffsetMs()) {
                    // Mantuvo la tecla durante toda la nota sin soltar: juicio de liberación perfecto
                    holdNote.setHoldState(Note.HoldState.COMPLETED);
                    this.activeHolds[lane] = null;
                    this.lastJudgement = Judgement.MARVELOUS;
                    this.scoreTracker.registerJudgement(Judgement.MARVELOUS);
                }
            }
        }
    }

    public Judgement onLanePressed(int lane, long currentSongTimeMs) {
        if (lane < 0 || lane >= keyCount) return null;

        Note closestNote = null;
        long minDelta = Long.MAX_VALUE;

        for (Note note : beatmap.getNotes()) {
            if (!note.isHit() && !note.isMissed() && note.getLane() == lane) {
                long delta = Math.abs(currentSongTimeMs - note.getOffsetMs());
                if (delta < minDelta && delta <= RhythmConfig.MAX_HIT_WINDOW_MS) {
                    minDelta = delta;
                    closestNote = note;
                }
            }
        }

        if (closestNote != null) {
            closestNote.setHit(true);
            Judgement judgement = Judgement.fromDelta(minDelta);
            this.lastJudgement = judgement;
            this.scoreTracker.registerJudgement(judgement);

            // Si es una nota HOLD y no se falló, entra en estado HOLDING activo
            if (judgement != Judgement.MISS && closestNote.getType() == Note.Type.HOLD && closestNote.getDurationMs() > 0) {
                closestNote.setHoldState(Note.HoldState.HOLDING);
                this.activeHolds[lane] = closestNote;
            }

            return judgement;
        }

        return null;
    }

    public Judgement onLaneReleased(int lane, long currentSongTimeMs) {
        if (lane < 0 || lane >= keyCount) return null;

        Note holdNote = this.activeHolds[lane];
        if (holdNote != null) {
            this.activeHolds[lane] = null;

            if (holdNote.isHolding()) {
                long tailTime = holdNote.getTailOffsetMs();
                long deltaFromTail = currentSongTimeMs - tailTime; // Negativo si soltó antes del final

                if (currentSongTimeMs < tailTime - RhythmConfig.MAX_HIT_WINDOW_MS) {
                    // El jugador soltó la tecla mucho antes de tiempo -> Corte de nota (MISS)
                    holdNote.setHoldState(Note.HoldState.DROPPED);
                    this.lastJudgement = Judgement.MISS;
                    this.scoreTracker.registerJudgement(Judgement.MISS);
                    return Judgement.MISS;
                } else {
                    // Soltó dentro de la ventana de liberación final
                    holdNote.setHoldState(Note.HoldState.COMPLETED);
                    long absDelta = Math.abs(deltaFromTail);
                    Judgement tailJudgement = Judgement.fromDelta(absDelta);
                    this.lastJudgement = tailJudgement;
                    this.scoreTracker.registerJudgement(tailJudgement);
                    return tailJudgement;
                }
            }
        }

        return null;
    }

    public boolean isLaneHolding(int lane) {
        return lane >= 0 && lane < keyCount && this.activeHolds[lane] != null && this.activeHolds[lane].isHolding();
    }

    public Beatmap getBeatmap() { return beatmap; }
    public ScoreTracker getScoreTracker() { return scoreTracker; }
    public Judgement getLastJudgement() { return lastJudgement; }
}
