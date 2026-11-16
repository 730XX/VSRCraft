package com.vsrcraft.client.config;

public class RhythmConfig {
    public static final int LANE_WIDTH = 45;
    public static final long MAX_HIT_WINDOW_MS = 150L;

    public static int getLaneWidth(int keyCount) {
        VSRCraftSettings settings = VSRCraftSettings.get();
        if (keyCount == 4) {
            return Math.max(20, settings.laneWidth4K);
        }
        return Math.max(16, settings.laneWidth7K);
    }

    public static int getHitLineOffsetBottom() {
        return VSRCraftSettings.get().hitPositionOffsetBottom;
    }

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
