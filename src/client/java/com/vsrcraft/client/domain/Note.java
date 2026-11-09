package com.vsrcraft.client.domain;

public class Note {
    public enum Type {
        TAP,
        HOLD
    }

    public enum HoldState {
        NONE,       // No es hold o aún no se ha presionado la cabeza
        HOLDING,    // El jugador acertó la cabeza y actualmente mantiene la tecla presionada
        COMPLETED,  // Se mantuvo y soltó con éxito hasta el final de la nota
        DROPPED     // El jugador la soltó antes de tiempo o nunca la presionó (Miss/Corte)
    }

    private final int lane;
    private final long offsetMs; // Tiempo en ms de inicio (cabeza de la nota)
    private final long durationMs; // Duración en ms de la cola
    private final Type type;
    private boolean hit = false;
    private boolean missed = false;
    private HoldState holdState = HoldState.NONE;

    public Note(int lane, long offsetMs) {
        this(lane, offsetMs, 0, Type.TAP);
    }

    public Note(int lane, long offsetMs, long durationMs, Type type) {
        this.lane = lane;
        this.offsetMs = offsetMs;
        this.durationMs = durationMs;
        this.type = type;
        this.holdState = (type == Type.HOLD && durationMs > 0) ? HoldState.NONE : HoldState.NONE;
    }

    public long getTailOffsetMs() {
        return offsetMs + durationMs;
    }

    public int getLane() { return lane; }
    public long getOffsetMs() { return offsetMs; }
    public long getDurationMs() { return durationMs; }
    public Type getType() { return type; }
    public boolean isHit() { return hit; }
    public void setHit(boolean hit) { this.hit = hit; }
    public boolean isMissed() { return missed; }
    public void setMissed(boolean missed) { this.missed = missed; }

    public HoldState getHoldState() { return holdState; }
    public void setHoldState(HoldState holdState) { this.holdState = holdState; }

    public boolean isHolding() {
        return this.holdState == HoldState.HOLDING;
    }

    public boolean isDropped() {
        return this.holdState == HoldState.DROPPED;
    }

    public boolean isCompleted() {
        return this.holdState == HoldState.COMPLETED;
    }
}
