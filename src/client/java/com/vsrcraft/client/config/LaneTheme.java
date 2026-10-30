package com.vsrcraft.client.config;

public class LaneTheme {
    public static int getLaneColor(int lane, int totalLanes) {
        if (totalLanes == 4) {
            return (lane == 0 || lane == 3) ? 0xFF3399FF : 0xFFFFFFFF;
        }
        // Colores por defecto para otros modos
        return (lane % 2 == 0) ? 0xFF3399FF : 0xFFFFFFFF;
    }

    public static final int TRACK_BACKGROUND = 0xCC101015;
    public static final int LANE_DIVIDER = 0x33FFFFFF;
    public static final int LANE_PRESS_GLOW = 0x3344AAFF;
    public static final int HIT_LINE = 0xFFFFCC00;
}

