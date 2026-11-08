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

    public int getTotalJudged() {
        int total = 0;
        for (int count : judgementCounts.values()) {
            total += count;
        }
        return total;
    }

    public float getAccuracy() {
        int total = getTotalJudged();
        if (total <= 0) return 100.0f;

        double weightedSum =
                judgementCounts.getOrDefault(Judgement.MARVELOUS, 0) * 300.0 +
                judgementCounts.getOrDefault(Judgement.PERFECT, 0) * 300.0 +
                judgementCounts.getOrDefault(Judgement.GREAT, 0) * 200.0 +
                judgementCounts.getOrDefault(Judgement.BAD, 0) * 50.0;
        double maxPossible = total * 300.0;
        return (float) ((weightedSum / maxPossible) * 100.0);
    }

    public String getRank() {
        float acc = getAccuracy();
        int misses = judgementCounts.getOrDefault(Judgement.MISS, 0);
        if (acc >= 99.99f && misses == 0) return "SS";
        if (acc >= 95.0f) return "S";
        if (acc >= 90.0f) return "A";
        if (acc >= 80.0f) return "B";
        if (acc >= 70.0f) return "C";
        return "D";
    }

    public int getScore() { return score; }
    public int getCombo() { return combo; }
    public int getMaxCombo() { return maxCombo; }
    public Map<Judgement, Integer> getJudgementCounts() { return judgementCounts; }
}
