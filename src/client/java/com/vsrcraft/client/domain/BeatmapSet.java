package com.vsrcraft.client.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Representa una canción completa (BeatmapSet en osu!) que contiene una o más
 * dificultades (versiones) agrupadas.
 */
public class BeatmapSet {
    private final String id;
    private final String title;
    private final String artist;
    private final String creator;
    private final String source;
    private final List<Beatmap> difficulties = new ArrayList<>();

    public BeatmapSet(String id, String title, String artist, String creator, String source) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.creator = creator;
        this.source = source;
    }

    public void addDifficulty(Beatmap beatmap) {
        // Evitar duplicados por id
        for (int i = 0; i < difficulties.size(); i++) {
            if (difficulties.get(i).getId().equals(beatmap.getId())) {
                difficulties.set(i, beatmap);
                sortDifficulties();
                return;
            }
        }
        difficulties.add(beatmap);
        sortDifficulties();
    }

    private void sortDifficulties() {
        difficulties.sort(Comparator.comparingDouble(Beatmap::getEstimatedStarRating));
    }

    public float getMinStars() {
        if (difficulties.isEmpty()) return 0.0f;
        return difficulties.get(0).getEstimatedStarRating();
    }

    public float getMaxStars() {
        if (difficulties.isEmpty()) return 0.0f;
        return difficulties.get(difficulties.size() - 1).getEstimatedStarRating();
    }

    public String getFormattedStarRange() {
        if (difficulties.isEmpty()) return "★ 0.0";
        float min = getMinStars();
        float max = getMaxStars();
        if (Math.abs(min - max) < 0.05f) {
            return String.format(Locale.ROOT, "★ %.1f", min);
        }
        return String.format(Locale.ROOT, "★ %.1f - %.1f", min, max);
    }

    public String getKeyModesSummary() {
        Set<Integer> keys = new LinkedHashSet<>();
        for (Beatmap b : difficulties) {
            keys.add(b.getKeyCount());
        }
        if (keys.isEmpty()) return "[4K]";
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (int k : keys) {
            if (count++ > 0) sb.append("/");
            sb.append(k).append("K");
        }
        sb.append("]");
        return sb.toString();
    }

    public boolean matchesQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return true;
        String[] tokens = rawQuery.toLowerCase(Locale.ROOT).trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        sb.append(title).append(" ").append(artist).append(" ").append(creator).append(" ").append(source).append(" ");
        for (Beatmap b : difficulties) {
            sb.append(b.getVersion()).append(" ").append(b.getTags()).append(" ").append(b.getKeyCount()).append("k ");
        }
        String searchable = sb.toString().toLowerCase(Locale.ROOT);

        for (String token : tokens) {
            if (!searchable.contains(token)) {
                return false;
            }
        }
        return true;
    }

    public Beatmap getDefaultBeatmap() {
        if (difficulties.isEmpty()) return null;
        return difficulties.get(0);
    }

    public boolean hasKeyCount(int keyCount) {
        for (Beatmap b : difficulties) {
            if (b.getKeyCount() == keyCount) {
                return true;
            }
        }
        return false;
    }

    public Beatmap getFirstForKeyCount(int keyCount) {
        for (Beatmap b : difficulties) {
            if (b.getKeyCount() == keyCount) {
                return b;
            }
        }
        return getDefaultBeatmap();
    }

    public int getPrimaryBpm() {
        return difficulties.isEmpty() ? 0 : difficulties.get(0).getBpm();
    }

    public String getAudioResourcePath() {
        for (Beatmap b : difficulties) {
            if (b.getAudioResourcePath() != null && !b.getAudioResourcePath().isEmpty()) {
                return b.getAudioResourcePath();
            }
        }
        return "";
    }

    public long getPreviewTimeMs() {
        for (Beatmap b : difficulties) {
            if (b.getPreviewTimeMs() > 0) {
                return b.getPreviewTimeMs();
            }
        }
        return 0L;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getCreator() { return creator; }
    public String getSource() { return source; }
    public List<Beatmap> getDifficulties() { return difficulties; }
}
