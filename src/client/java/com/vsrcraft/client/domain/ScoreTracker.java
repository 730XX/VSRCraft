package com.vsrcraft.client.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ScoreTracker {
    public static final int MAX_SCORE = 1_000_000;

    public record HitSample(long songTimeMs, long errorMs, Judgement judgement) {}

    private double rawScore = 0.0;
    private int score = 0;
    private int combo = 0;
    private int maxCombo = 0;
    private int totalJudgementUnits = 1;
    private final Map<Judgement, Integer> judgementCounts = new EnumMap<>(Judgement.class);
    private final List<HitSample> hitSamples = new ArrayList<>();

    public ScoreTracker() {
        reset(1);
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
            double perJudgementMax = (double) MAX_SCORE / Math.max(1, totalJudgementUnits);
            rawScore += perJudgementMax * judgement.getScoreWeight();
            score = (int) Math.min(MAX_SCORE, Math.round(rawScore));
        }
    }

    public void registerJudgement(Judgement judgement, long songTimeMs, long errorMs, boolean recordTimingSample) {
        registerJudgement(judgement);
        if (recordTimingSample && judgement != Judgement.MISS) {
            hitSamples.add(new HitSample(songTimeMs, errorMs, judgement));
        }
    }

    public void reset() {
        reset(this.totalJudgementUnits);
    }

    public void reset(int totalJudgementUnits) {
        this.totalJudgementUnits = Math.max(1, totalJudgementUnits);
        rawScore = 0.0;
        score = 0;
        combo = 0;
        maxCombo = 0;
        hitSamples.clear();
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
    public List<HitSample> getHitSamples() { return hitSamples; }

    public String getFormattedMaPfRatio() {
        int ma = judgementCounts.getOrDefault(Judgement.MARVELOUS, 0);
        int pf = judgementCounts.getOrDefault(Judgement.PERFECT, 0);
        if (pf == 0) {
            return ma > 0 ? "∞ : 1" : "0.0 : 1";
        }
        return String.format(Locale.ROOT, "%.2f : 1", (double) ma / pf);
    }

    public double getMeanErrorMs() {
        if (hitSamples.isEmpty()) return 0.0;
        double sum = 0.0;
        for (HitSample s : hitSamples) {
            sum += s.errorMs();
        }
        return sum / hitSamples.size();
    }

    public double getUnstableRate() {
        if (hitSamples.size() < 2) return 0.0;
        double mean = getMeanErrorMs();
        double sqSum = 0.0;
        for (HitSample s : hitSamples) {
            double d = s.errorMs() - mean;
            sqSum += d * d;
        }
        double stdev = Math.sqrt(sqSum / hitSamples.size());
        return stdev * 10.0;
    }

    public static ScoreTracker fromCounts(int score, int maxCombo, int marvelous, int perfect, int great, int bad, int miss) {
        ScoreTracker t = new ScoreTracker();
        t.score = score;
        t.maxCombo = maxCombo;
        t.judgementCounts.put(Judgement.MARVELOUS, Math.max(0, marvelous));
        t.judgementCounts.put(Judgement.PERFECT, Math.max(0, perfect));
        t.judgementCounts.put(Judgement.GREAT, Math.max(0, great));
        t.judgementCounts.put(Judgement.BAD, Math.max(0, bad));
        t.judgementCounts.put(Judgement.MISS, Math.max(0, miss));
        return t;
    }
}
