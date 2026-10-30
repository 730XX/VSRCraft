package com.vsrcraft.client.domain;

import java.util.ArrayList;
import java.util.List;

public class Beatmap {
    private final String title;
    private final String artist;
    private final int keyCount;
    private final List<Note> notes;

    public Beatmap(String title, String artist, int keyCount) {
        this.title = title;
        this.artist = artist;
        this.keyCount = keyCount;
        this.notes = new ArrayList<>();
    }

    public void addNote(Note note) {
        this.notes.add(note);
    }

    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public int getKeyCount() { return keyCount; }
    public List<Note> getNotes() { return notes; }
}

