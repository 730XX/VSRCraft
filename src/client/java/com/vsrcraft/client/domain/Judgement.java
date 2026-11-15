package com.vsrcraft.client.domain;

public enum Judgement {
    MARVELOUS(30, 300, 1.0, 0xFF00FFFF, "MARVELOUS!!"),
    PERFECT(65, 200, 0.95, 0xFFFFD700, "PERFECT"),
    GREAT(100, 100, 2.0 / 3.0, 0xFF55FF55, "GREAT"),
    BAD(150, 50, 1.0 / 6.0, 0xFFFF8800, "BAD"),
    MISS(Integer.MAX_VALUE, 0, 0.0, 0xFFFF3333, "MISS");

    private final int windowMs;
    private final int scoreValue;
    private final double scoreWeight;
    private final int color;
    private final String label;

    Judgement(int windowMs, int scoreValue, double scoreWeight, int color, String label) {
        this.windowMs = windowMs;
        this.scoreValue = scoreValue;
        this.scoreWeight = scoreWeight;
        this.color = color;
        this.label = label;
    }

    public int getWindowMs() { return windowMs; }
    public int getScoreValue() { return scoreValue; }
    public double getScoreWeight() { return scoreWeight; }
    public int getColor() { return color; }
    public String getLabel() { return label; }

    public static Judgement fromDelta(long deltaMs) {
        if (deltaMs <= MARVELOUS.windowMs) return MARVELOUS;
        if (deltaMs <= PERFECT.windowMs) return PERFECT;
        if (deltaMs <= GREAT.windowMs) return GREAT;
        if (deltaMs <= BAD.windowMs) return BAD;
        return MISS;
    }
}

