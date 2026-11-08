package com.vsrcraft.client.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Beatmap {
    private final String title;
    private final String artist;
    private final int keyCount;
    private final String audioFilename;
    private final long audioLeadInMs;
    private final List<Note> notes;

    public Beatmap(String title, String artist, int keyCount) {
        this(title, artist, keyCount, "", 0L);
    }

    public Beatmap(String title, String artist, int keyCount, String audioFilename, long audioLeadInMs) {
        this.title = title;
        this.artist = artist;
        this.keyCount = keyCount;
        this.audioFilename = audioFilename;
        this.audioLeadInMs = audioLeadInMs;
        this.notes = new ArrayList<>();
    }

    public void addNote(Note note) {
        this.notes.add(note);
    }

    public void sortNotes() {
        this.notes.sort(Comparator.comparingLong(Note::getOffsetMs));
    }

    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public int getKeyCount() { return keyCount; }
    public String getAudioFilename() { return audioFilename; }
    public long getAudioLeadInMs() { return audioLeadInMs; }
    public List<Note> getNotes() { return notes; }
}
