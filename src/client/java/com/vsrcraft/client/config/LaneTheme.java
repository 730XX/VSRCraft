package com.vsrcraft.client.config;

public class LaneTheme {
    private static final int[] RAINBOW_4K = {0xFFFF4D6D, 0xFF4CC9F0, 0xFF80ED99, 0xFFFFD166};
    private static final int[] RAINBOW_7K = {0xFFFF4D6D, 0xFFFF9F1C, 0xFFFFD166, 0xFF80ED99, 0xFF4CC9F0, 0xFF4361EE, 0xFFB5179E};

    public static int getLaneColor(int lane, int totalLanes) {
        VSRCraftSettings.ColorPreset preset = VSRCraftSettings.get().getColorPreset();
        int safeLane = Math.max(0, lane);

        return switch (preset) {
            case NEON_CYBER -> {
                if (totalLanes == 7 && safeLane == 3) yield 0xFFFFEA00;
                yield (safeLane == 0 || safeLane == totalLanes - 1 || (totalLanes == 7 && (safeLane == 1 || safeLane == 5)))
                        ? 0xFFFF2A85 : 0xFF00F0FF;
            }
            case OSU_PINK -> {
                if (totalLanes == 7 && safeLane == 3) yield 0xFFFFD54F;
                if (totalLanes == 4) yield (safeLane == 1 || safeLane == 2) ? 0xFFFF66AA : 0xFFFFFFFF;
                yield (safeLane % 2 == 1) ? 0xFFFF66AA : 0xFFFFFFFF;
            }
            case RAINBOW -> {
                if (totalLanes == 4) yield RAINBOW_4K[safeLane % RAINBOW_4K.length];
                if (totalLanes == 7) yield RAINBOW_7K[safeLane % RAINBOW_7K.length];
                yield RAINBOW_7K[safeLane % RAINBOW_7K.length];
            }
            case MONOCHROME -> {
                if (totalLanes == 7 && safeLane == 3) yield 0xFFFFFFFF;
                if (totalLanes == 4) yield (safeLane == 0 || safeLane == 3) ? 0xFFB0BEC5 : 0xFFFFFFFF;
                yield (safeLane % 2 == 0) ? 0xFFB0BEC5 : 0xFFFFFFFF;
            }
            case CLASSIC -> {
                if (totalLanes == 4) {
                    yield (safeLane == 0 || safeLane == 3) ? 0xFF3399FF : 0xFFFFFFFF;
                }
                if (totalLanes == 7) {
                    if (safeLane == 3) yield 0xFFFFCC00;
                    yield (safeLane == 1 || safeLane == 5) ? 0xFF3399FF : 0xFFFFFFFF;
                }
                yield (safeLane % 2 == 0) ? 0xFF3399FF : 0xFFFFFFFF;
            }
        };
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
    public static final int HIT_LINE = 0xFFFFFFFF;
}
