package com.vsrcraft.client.domain;

public class Note {
    public enum Type {
        TAP,
        HOLD
    }

    private final int lane;
    private final long offsetMs; // Tiempo en ms relativo al inicio de la canción
    private final long durationMs;
    private final Type type;
    private boolean hit = false;
    private boolean missed = false;

    public Note(int lane, long offsetMs) {
        this(lane, offsetMs, 0, Type.TAP);
    }

    public Note(int lane, long offsetMs, long durationMs, Type type) {
        this.lane = lane;
        this.offsetMs = offsetMs;
        this.durationMs = durationMs;
        this.type = type;
    }

    public int getLane() { return lane; }
    public long getOffsetMs() { return offsetMs; }
    public long getDurationMs() { return durationMs; }
    public Type getType() { return type; }
    public boolean isHit() { return hit; }
    public void setHit(boolean hit) { this.hit = hit; }
    public boolean isMissed() { return missed; }
    public void setMissed(boolean missed) { this.missed = missed; }
}
