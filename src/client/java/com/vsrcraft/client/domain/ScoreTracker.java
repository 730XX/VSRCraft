package com.vsrcraft.client.domain;

import java.util.EnumMap;
import java.util.Map;

public class ScoreTracker {
    private int score = 0;
    private int combo = 0;
    private int maxCombo = 0;
    private final Map<Judgement, Integer> judgementCounts = new EnumMap<>(Judgement.class);

    public ScoreTracker() {
        reset();
    }

    public void registerJudgement(Judgement judgement) {
        judgementCounts.put(judgement, judgementCounts.getOrDefault(judgement, 0) + 1);

        if (judgement == Judgement.MISS) {
            combo = 0;
        } else {
            combo++;
            if (combo > maxCombo) {
                maxCombo = combo;
            }
            score += judgement.getScoreValue();
        }
    }

    public void reset() {
        score = 0;
        combo = 0;
        maxCombo = 0;
        for (Judgement j : Judgement.values()) {
            judgementCounts.put(j, 0);
        }
    }

    public int getScore() { return score; }
    public int getCombo() { return combo; }
    public int getMaxCombo() { return maxCombo; }
    public Map<Judgement, Integer> getJudgementCounts() { return judgementCounts; }
}

