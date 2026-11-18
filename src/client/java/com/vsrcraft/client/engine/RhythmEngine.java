package com.vsrcraft.client.engine;

import com.vsrcraft.client.config.RhythmConfig;
import com.vsrcraft.client.domain.Beatmap;
import com.vsrcraft.client.domain.Judgement;
import com.vsrcraft.client.domain.Note;
import com.vsrcraft.client.domain.ScoreTracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Motor central de ritmo, juicios y supervivencia de VSRCraft.
 * Soporta:
 * - Evaluación precisa de Taps y Long Notes (cabeza, sostenimiento y liberación).
 * - Barra de vida lateral (HP) y condición de fallo (Stage Failed) según el HPDrainRate del beatmap.
 */
public class RhythmEngine {
    public record HitEvent(int lane, Judgement judgement) {}

    private final Beatmap beatmap;
    private final ScoreTracker scoreTracker;
    private final int keyCount;
    private final Note[] activeHolds;
    private final List<HitEvent> pendingVisualEvents = new ArrayList<>();
    private Judgement lastJudgement = null;

    // Sistema de Vida (HP) y Fallo (Fail Condition)
    private float hp = 100.0f;
    private boolean isFailed = false;
    private int firstActiveNoteIdx = 0;

    public RhythmEngine(Beatmap beatmap) {
        this.beatmap = beatmap;
        this.scoreTracker = new ScoreTracker();
        this.keyCount = beatmap.getKeyCount();
        this.activeHolds = new Note[this.keyCount];
        this.scoreTracker.reset(computeTotalJudgementUnits(beatmap));
    }

    public void start(long ignored) {
        this.scoreTracker.reset(computeTotalJudgementUnits(this.beatmap));
        this.hp = 100.0f;
        this.isFailed = false;
        this.firstActiveNoteIdx = 0;
        this.lastJudgement = null;
        this.pendingVisualEvents.clear();
        Arrays.fill(this.activeHolds, null);
        for (Note note : beatmap.getNotes()) {
            note.setHit(false);
            note.setMissed(false);
            note.setHoldState(Note.HoldState.NONE);
        }
    }

    private static int computeTotalJudgementUnits(Beatmap beatmap) {
        int units = 0;
        for (Note note : beatmap.getNotes()) {
            if (note.getType() == Note.Type.HOLD && note.getDurationMs() > 0) {
                units += 2; // Cabeza + cola del Hold
            } else {
                units += 1;
            }
        }
        return Math.max(1, units);
    }

    private void applyHpJudgement(Judgement judgement) {
        if (this.isFailed) return;

        float hpDrain = beatmap.getHpDrainRate();
        // Factor de drenaje suavizado para que sea justo y permita recuperarse
        float drainFactor = Math.max(0.4f, Math.min(1.3f, hpDrain / 8.0f));

        switch (judgement) {
            case MARVELOUS -> this.hp = Math.min(100.0f, this.hp + 2.2f);
            case PERFECT -> this.hp = Math.min(100.0f, this.hp + 1.6f);
            case GREAT -> this.hp = Math.min(100.0f, this.hp + 0.8f);
            case BAD -> this.hp = Math.max(0.0f, this.hp - 1.2f * drainFactor);
            case MISS -> this.hp = Math.max(0.0f, this.hp - 3.8f * drainFactor);
        }

        if (this.hp <= 0.0f) {
            this.hp = 0.0f;
            this.isFailed = true;
        }
    }

    public void update(long currentSongTimeMs) {
        if (this.isFailed) return;

        List<Note> notes = beatmap.getNotes();
        int n = notes.size();

        // Avanzar puntero de ventana activa para descartar notas ya resueltas en O(1) amortizado
        while (this.firstActiveNoteIdx < n) {
            Note first = notes.get(this.firstActiveNoteIdx);
            boolean isHold = first.getType() == Note.Type.HOLD && first.getDurationMs() > 0;
            if (!isHold) {
                if (first.isHit() || first.isMissed()) {
                    this.firstActiveNoteIdx++;
                    continue;
                }
            } else {
                if (first.isCompleted() || currentSongTimeMs >= first.getTailOffsetMs() + 150L) {
                    this.firstActiveNoteIdx++;
                    continue;
                }
            }
            break;
        }

        // 1. Chequear notas cuya cabeza pasó de largo sin ser presionadas (Miss) con early-break
        for (int i = this.firstActiveNoteIdx; i < n; i++) {
            Note note = notes.get(i);
            long timeUntilHit = note.getOffsetMs() - currentSongTimeMs;
            if (timeUntilHit >= -RhythmConfig.MAX_HIT_WINDOW_MS) {
                break; // Al estar ordenadas por offsetMs, ninguna nota posterior puede haber expirado
            }
            if (!note.isHit() && !note.isMissed()) {
                note.setMissed(true);
                if (note.getType() == Note.Type.HOLD) {
                    note.setHoldState(Note.HoldState.DROPPED);
                }
                this.lastJudgement = Judgement.MISS;
                this.scoreTracker.registerJudgement(Judgement.MISS);
                this.pendingVisualEvents.add(new HitEvent(note.getLane(), Judgement.MISS));
                applyHpJudgement(Judgement.MISS);
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
                    this.pendingVisualEvents.add(new HitEvent(lane, Judgement.MARVELOUS));
                    applyHpJudgement(Judgement.MARVELOUS);
                }
            }
        }
    }

    public Judgement onLanePressed(int lane, long currentSongTimeMs) {
        if (this.isFailed || lane < 0 || lane >= keyCount) return null;

        Note closestNote = null;
        long minDelta = Long.MAX_VALUE;

        List<Note> notes = beatmap.getNotes();
        int n = notes.size();
        for (int i = this.firstActiveNoteIdx; i < n; i++) {
            Note note = notes.get(i);
            long signedDiff = note.getOffsetMs() - currentSongTimeMs;
            if (signedDiff > RhythmConfig.MAX_HIT_WINDOW_MS) {
                break; // Notas futuras fuera de la ventana de golpe
            }
            if (!note.isHit() && !note.isMissed() && note.getLane() == lane) {
                long delta = Math.abs(signedDiff);
                if (delta < minDelta && delta <= RhythmConfig.MAX_HIT_WINDOW_MS) {
                    minDelta = delta;
                    closestNote = note;
                }
            }
        }

        if (closestNote != null) {
            closestNote.setHit(true);
            long signedError = currentSongTimeMs - closestNote.getOffsetMs();
            Judgement judgement = Judgement.fromDelta(minDelta);
            this.lastJudgement = judgement;
            this.scoreTracker.registerJudgement(judgement, currentSongTimeMs, signedError, true);
            this.pendingVisualEvents.add(new HitEvent(lane, judgement));
            applyHpJudgement(judgement);

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
        if (this.isFailed || lane < 0 || lane >= keyCount) return null;

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
                    this.pendingVisualEvents.add(new HitEvent(lane, Judgement.MISS));
                    applyHpJudgement(Judgement.MISS);
                    return Judgement.MISS;
                } else {
                    // Soltó dentro de la ventana de liberación final
                    holdNote.setHoldState(Note.HoldState.COMPLETED);
                    long absDelta = Math.abs(deltaFromTail);
                    Judgement tailJudgement = Judgement.fromDelta(absDelta);
                    this.lastJudgement = tailJudgement;
                    this.scoreTracker.registerJudgement(tailJudgement, currentSongTimeMs, deltaFromTail, true);
                    this.pendingVisualEvents.add(new HitEvent(lane, tailJudgement));
                    applyHpJudgement(tailJudgement);
                    return tailJudgement;
                }
            }
        }

        return null;
    }

    public List<HitEvent> drainVisualEvents() {
        if (this.pendingVisualEvents.isEmpty()) {
            return List.of();
        }
        List<HitEvent> events = new ArrayList<>(this.pendingVisualEvents);
        this.pendingVisualEvents.clear();
        return events;
    }

    public boolean isLaneHolding(int lane) {
        return lane >= 0 && lane < keyCount && this.activeHolds[lane] != null && this.activeHolds[lane].isHolding();
    }

    public boolean isCleared(long currentSongTimeMs) {
        List<Note> notes = this.beatmap.getNotes();
        if (this.isFailed || notes.isEmpty()) {
            return false;
        }
        if (currentSongTimeMs < this.beatmap.getDurationMs() + 1500L) {
            return false;
        }
        for (int i = this.firstActiveNoteIdx; i < notes.size(); i++) {
            Note note = notes.get(i);
            if (!note.isHit() && !note.isMissed()) {
                return false;
            }
            if (note.getType() == Note.Type.HOLD && note.getDurationMs() > 0 && note.isHolding()) {
                return false;
            }
        }
        return true;
    }

    public int getFirstActiveNoteIdx() { return firstActiveNoteIdx; }
    public float getHp() { return hp; }
    public boolean isFailed() { return isFailed; }
    public Beatmap getBeatmap() { return beatmap; }
    public ScoreTracker getScoreTracker() { return scoreTracker; }
    public Judgement getLastJudgement() { return lastJudgement; }
}
