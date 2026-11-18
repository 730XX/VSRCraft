package com.vsrcraft.client.config;

/**
 * Paleta oficial de colores de dificultad de osu! (espectro continuo y categorías).
 * Basada en la escala oficial: Easy, Normal, Hard, Insane, Expert y Expert+.
 */
public enum OsuDifficultyColor {
    EASY(0.0f, 1.99f, 0xFF4FC0FF, "Easy"),
    NORMAL(2.0f, 2.69f, 0xFF66FF4F, "Normal"),
    HARD(2.7f, 3.99f, 0xFFF6F05C, "Hard"),
    INSANE(4.0f, 5.29f, 0xFFFF4E6F, "Insane"),
    EXPERT(5.3f, 6.49f, 0xFFC645B8, "Expert"),
    EXPERT_PLUS(6.5f, Float.MAX_VALUE, 0xFF6563DE, "Expert+");

    private final float minStars;
    private final float maxStars;
    private final int color;
    private final String label;

    // Puntos de control del espectro continuo oficial de osu! (starRating -> ARGB)
    private static final float[] SPECTRUM_STARS = {
            0.1f, 1.25f, 2.0f, 2.5f, 3.3f, 4.2f, 4.9f, 5.8f, 6.7f, 7.7f, 9.0f
    };
    private static final int[] SPECTRUM_COLORS = {
            0xFF4290FB, // 0.1 Azul cielo
            0xFF4FC0FF, // 1.25 Cian Easy
            0xFF4FFFD5, // 2.0 Turquesa
            0xFF7CFF4F, // 2.5 Verde Normal
            0xFFF6F05C, // 3.3 Amarillo Hard
            0xFFFF8068, // 4.2 Naranja coral
            0xFFFF4E6F, // 4.9 Rosa/Rojo Insane
            0xFFC645B8, // 5.8 Magenta Expert
            0xFF6563DE, // 6.7 Índigo Expert+
            0xFF3E35B8, // 7.7 Índigo profundo (visible sobre fondo oscuro)
            0xFF281F66  // 9.0+ Violeta oscuro
    };

    OsuDifficultyColor(float minStars, float maxStars, int color, String label) {
        this.minStars = minStars;
        this.maxStars = maxStars;
        this.color = color;
        this.label = label;
    }

    public int getColor() {
        return color;
    }

    public String getLabel() {
        return label;
    }

    public static OsuDifficultyColor fromStars(float stars) {
        for (OsuDifficultyColor tier : values()) {
            if (stars <= tier.maxStars) {
                return tier;
            }
        }
        return EXPERT_PLUS;
    }

    /**
     * Devuelve el color discreto de categoría oficial para el Star Rating dado.
     */
    public static int getTierColor(float stars) {
        return fromStars(stars).getColor();
    }

    /**
     * Interpola suavemente a lo largo del espectro oficial continuo de osu! según el Star Rating.
     */
    public static int getSpectrumColor(float stars) {
        if (stars <= SPECTRUM_STARS[0]) return SPECTRUM_COLORS[0];
        int last = SPECTRUM_STARS.length - 1;
        if (stars >= SPECTRUM_STARS[last]) return SPECTRUM_COLORS[last];

        for (int i = 0; i < last; i++) {
            float s0 = SPECTRUM_STARS[i];
            float s1 = SPECTRUM_STARS[i + 1];
            if (stars <= s1) {
                float t = (stars - s0) / Math.max(0.0001f, s1 - s0);
                return lerpArgb(SPECTRUM_COLORS[i], SPECTRUM_COLORS[i + 1], t);
            }
        }
        return SPECTRUM_COLORS[last];
    }

    /**
     * Convierte una densidad local en NPS al color del espectro oficial de osu!.
     */
    public static int getColorForNps(float nps) {
        float equivStars = Math.max(0.1f, nps / 3.2f);
        return getSpectrumColor(equivStars);
    }

    private static int lerpArgb(int c0, int c1, float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        int a0 = (c0 >>> 24) & 0xFF, r0 = (c0 >>> 16) & 0xFF, g0 = (c0 >>> 8) & 0xFF, b0 = c0 & 0xFF;
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int a = Math.round(a0 + (a1 - a0) * clamped);
        int r = Math.round(r0 + (r1 - r0) * clamped);
        int g = Math.round(g0 + (g1 - g0) * clamped);
        int b = Math.round(b0 + (b1 - b0) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
