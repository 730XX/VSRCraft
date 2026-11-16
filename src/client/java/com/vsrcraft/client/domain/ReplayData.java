package com.vsrcraft.client.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Datos serializables de una partida grabada (Modo Replay) para VSRCraft.
 */
public class ReplayData {
    public static class ReplayEvent {
        public long timeMs;
        public int lane;
        public boolean pressed;

        public ReplayEvent() {}

        public ReplayEvent(long timeMs, int lane, boolean pressed) {
            this.timeMs = timeMs;
            this.lane = lane;
            this.pressed = pressed;
        }
    }

    public String beatmapId = "";
    public int score = 0;
    public float accuracy = 0.0f;
    public int maxCombo = 0;
    public String rank = "D";
    public long recordedAtMs = 0L;
    public List<ReplayEvent> events = new ArrayList<>();

    public ReplayData() {}

    public ReplayData(String beatmapId, int score, float accuracy, int maxCombo, String rank, List<ReplayEvent> events) {
        this.beatmapId = beatmapId;
        this.score = score;
        this.accuracy = accuracy;
        this.maxCombo = maxCombo;
        this.rank = rank;
        this.recordedAtMs = System.currentTimeMillis();
        this.events = (events != null) ? new ArrayList<>(events) : new ArrayList<>();
    }
}
