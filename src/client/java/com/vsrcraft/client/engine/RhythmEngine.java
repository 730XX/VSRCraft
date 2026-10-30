package com.vsrcraft.client.engine;

import com.vsrcraft.client.config.RhythmConfig;
import com.vsrcraft.client.domain.Beatmap;
import com.vsrcraft.client.domain.Judgement;
import com.vsrcraft.client.domain.Note;
import com.vsrcraft.client.domain.ScoreTracker;

public class RhythmEngine {
    private final Beatmap beatmap;
    private final ScoreTracker scoreTracker;
    private Judgement lastJudgement = null;

    public RhythmEngine(Beatmap beatmap) {
        this.beatmap = beatmap;
        this.scoreTracker = new ScoreTracker();
    }

    public void start(long ignored) {
        this.scoreTracker.reset();
        for (Note note : beatmap.getNotes()) {
            note.setHit(false);
            note.setMissed(false);
        }
    }

    public void update(long currentSongTimeMs) {
        // Chequear notas que pasaron de largo sin ser presionadas (Miss)
        for (Note note : beatmap.getNotes()) {
            if (!note.isHit() && !note.isMissed()) {
                long timeUntilHit = note.getOffsetMs() - currentSongTimeMs;
                if (timeUntilHit < -RhythmConfig.MAX_HIT_WINDOW_MS) {
                    note.setMissed(true);
                    lastJudgement = Judgement.MISS;
                    scoreTracker.registerJudgement(Judgement.MISS);
                }
            }
        }
    }

    public Judgement onLanePressed(int lane, long currentSongTimeMs) {
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
            return judgement;
        }

        return null;
    }

    public Beatmap getBeatmap() { return beatmap; }
    public ScoreTracker getScoreTracker() { return scoreTracker; }
    public Judgement getLastJudgement() { return lastJudgement; }
}
