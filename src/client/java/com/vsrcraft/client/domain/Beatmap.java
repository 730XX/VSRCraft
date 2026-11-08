package com.vsrcraft.client.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class Beatmap {
    private final String id;
    private final String title;
    private final String artist;
    private final String creator;
    private final String version;
    private final String source;
    private final String tags;
    private final int keyCount;
    private final float overallDifficulty;
    private final float hpDrainRate;
    private final int bpm;
    private final String audioFilename;
    private String audioResourcePath;
    private final long audioLeadInMs;
    private final long previewTimeMs;
    private final List<Note> notes;

    // Estadísticas cacheadas
    private int tapCount = -1;
    private int holdCount = -1;
    private long durationMs = -1L;

    public Beatmap(String title, String artist, int keyCount) {
        this(title + "_" + artist, title, artist, "Unknown", "Normal", "", "", keyCount, 5.0f, 5.0f, 120, "", "", 0L, 0L);
    }

    public Beatmap(String title, String artist, int keyCount, String audioFilename, long audioLeadInMs) {
        this(title + "_" + artist, title, artist, "Unknown", "Normal", "", "", keyCount, 5.0f, 5.0f, 120, audioFilename, "", audioLeadInMs, 0L);
    }

    public Beatmap(
            String id,
            String title,
            String artist,
            String creator,
            String version,
            String source,
            String tags,
            int keyCount,
            float overallDifficulty,
            float hpDrainRate,
            int bpm,
            String audioFilename,
            String audioResourcePath,
            long audioLeadInMs,
            long previewTimeMs
    ) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.creator = creator;
        this.version = version;
        this.source = source;
        this.tags = tags;
        this.keyCount = keyCount;
        this.overallDifficulty = overallDifficulty;
        this.hpDrainRate = hpDrainRate;
        this.bpm = bpm;
        this.audioFilename = audioFilename;
        this.audioResourcePath = audioResourcePath;
        this.audioLeadInMs = audioLeadInMs;
        this.previewTimeMs = previewTimeMs;
        this.notes = new ArrayList<>();
    }

    public void addNote(Note note) {
        this.notes.add(note);
        this.tapCount = -1;
        this.holdCount = -1;
        this.durationMs = -1L;
    }

    public void sortNotes() {
        this.notes.sort(Comparator.comparingLong(Note::getOffsetMs));
        recomputeStats();
    }

    private void recomputeStats() {
        int taps = 0;
        int holds = 0;
        long maxEnd = 0L;
        for (Note n : notes) {
            if (n.getType() == Note.Type.HOLD) {
                holds++;
                maxEnd = Math.max(maxEnd, n.getOffsetMs() + n.getDurationMs());
            } else {
                taps++;
                maxEnd = Math.max(maxEnd, n.getOffsetMs());
            }
        }
        this.tapCount = taps;
        this.holdCount = holds;
        this.durationMs = maxEnd;
    }

    public int getTapCount() {
        if (tapCount < 0) recomputeStats();
        return tapCount;
    }

    public int getHoldCount() {
        if (holdCount < 0) recomputeStats();
        return holdCount;
    }

    public long getDurationMs() {
        if (durationMs < 0) recomputeStats();
        return durationMs;
    }

    public String getFormattedDuration() {
        long totalSec = Math.max(0L, getDurationMs() / 1000L);
        long mins = totalSec / 60L;
        long secs = totalSec % 60L;
        return String.format(Locale.ROOT, "%02d:%02d", mins, secs);
    }

    /**
     * Estima las estrellas de dificultad en función de la densidad de notas (NPS) y cantidad de Holds.
     */
    public float getEstimatedStarRating() {
        long durMs = Math.max(1000L, getDurationMs());
        float nps = (notes.size() * 1000.0f) / durMs;
        float holdBonus = notes.isEmpty() ? 0.0f : (getHoldCount() / (float) notes.size()) * 0.6f;
        return Math.max(0.5f, Math.min(9.99f, nps * 0.38f + holdBonus));
    }

    public boolean matchesQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return true;
        String[] tokens = rawQuery.toLowerCase(Locale.ROOT).trim().split("\\s+");
        String searchable = (title + " " + artist + " " + creator + " " + version + " " + source + " " + tags + " " + keyCount + "k")
                .toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (!searchable.contains(token)) {
                return false;
            }
        }
        return true;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getCreator() { return creator; }
    public String getVersion() { return version; }
    public String getSource() { return source; }
    public String getTags() { return tags; }
    public int getKeyCount() { return keyCount; }
    public float getOverallDifficulty() { return overallDifficulty; }
    public float getHpDrainRate() { return hpDrainRate; }
    public int getBpm() { return bpm; }
    public String getAudioFilename() { return audioFilename; }
    public String getAudioResourcePath() { return audioResourcePath; }
    public void setAudioResourcePath(String audioResourcePath) { this.audioResourcePath = audioResourcePath; }
    public long getAudioLeadInMs() { return audioLeadInMs; }
    public long getPreviewTimeMs() { return previewTimeMs; }
    public List<Note> getNotes() { return notes; }
}
