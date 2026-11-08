package com.vsrcraft.client.config;

public class RhythmConfig {
    public static final int LANE_WIDTH = 45;
    public static final int HIT_LINE_OFFSET_BOTTOM = 60;
    public static final long MAX_HIT_WINDOW_MS = 150L;

    public static float getScrollSpeed() {
        return VSRCraftSettings.get().scrollSpeed;
    }

    public static int getNoteHeight() {
        return VSRCraftSettings.get().noteHeight;
    }

    public static long getGlobalAudioOffsetMs() {
        return VSRCraftSettings.get().globalAudioOffsetMs;
    }

    public static void setGlobalAudioOffsetMs(long offset) {
        VSRCraftSettings.get().globalAudioOffsetMs = offset;
        VSRCraftSettings.save();
    }
}
