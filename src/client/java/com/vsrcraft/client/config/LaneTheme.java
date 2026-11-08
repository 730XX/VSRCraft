package com.vsrcraft.client.config;

public class LaneTheme {
    public static int getLaneColor(int lane, int totalLanes) {
        if (totalLanes == 4) {
            return (lane == 0 || lane == 3) ? 0xFF3399FF : 0xFFFFFFFF;
        }
        // Colores por defecto para otros modos
        return (lane % 2 == 0) ? 0xFF3399FF : 0xFFFFFFFF;
    }

    public static int getTrackBackgroundColor() {
        float alpha = VSRCraftSettings.get().laneAlpha;
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (a << 24) | 0x101015;
    }

    public static int getLaneDividerColor() {
        float alpha = VSRCraftSettings.get().laneAlpha;
        int a = Math.max(0, Math.min(255, Math.round(alpha * 0.4f * 255)));
        return (a << 24) | 0xFFFFFF;
    }

    public static final int LANE_PRESS_GLOW = 0x3344AAFF;
    public static final int HIT_LINE = 0xFFFFCC00;
}
