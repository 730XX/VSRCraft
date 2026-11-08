package com.vsrcraft.client.parser;

import com.vsrcraft.client.domain.Beatmap;
import com.vsrcraft.client.domain.Note;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class OsuBeatmapParser {

    public static Beatmap parse(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;

        String currentSection = "";
        String title = "Unknown Title";
        String artist = "Unknown Artist";
        String audioFilename = "";
        long audioLeadInMs = 0L;
        int circleSize = 4; // Teclas por defecto (4K)

        Beatmap beatmap = null;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;

            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length() - 1);
                if (currentSection.equals("HitObjects") && beatmap == null) {
                    beatmap = new Beatmap(title, artist, circleSize, audioFilename, audioLeadInMs);
                }
                continue;
            }

            switch (currentSection) {
                case "General" -> {
                    if (line.startsWith("AudioFilename:")) {
                        audioFilename = line.substring("AudioFilename:".length()).trim();
                    } else if (line.startsWith("AudioLeadIn:")) {
                        try {
                            audioLeadInMs = Long.parseLong(line.substring("AudioLeadIn:".length()).trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "Metadata" -> {
                    if (line.startsWith("Title:")) {
                        title = line.substring("Title:".length()).trim();
                    } else if (line.startsWith("Artist:")) {
                        artist = line.substring("Artist:".length()).trim();
                    }
                }
                case "Difficulty" -> {
                    if (line.startsWith("CircleSize:")) {
                        try {
                            circleSize = (int) Float.parseFloat(line.substring("CircleSize:".length()).trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "HitObjects" -> {
                    if (beatmap == null) {
                        beatmap = new Beatmap(title, artist, circleSize, audioFilename, audioLeadInMs);
                    }
                    parseHitObject(line, beatmap, circleSize);
                }
            }
        }

        if (beatmap == null) {
            beatmap = new Beatmap(title, artist, circleSize, audioFilename, audioLeadInMs);
        }

        beatmap.sortNotes();
        return beatmap;
    }

    private static void parseHitObject(String line, Beatmap beatmap, int keyCount) {
        // Formato: x,y,time,type,hitSound,[endTime:]hitSample
        String[] parts = line.split(",");
        if (parts.length < 4) return;

        try {
            int x = Integer.parseInt(parts[0].trim());
            long hitTimeMs = Long.parseLong(parts[2].trim());
            int type = Integer.parseInt(parts[3].trim());

            // Fórmula mania: lane = floor(x * keyCount / 512)
            int lane = (int) Math.floor((double) x * keyCount / 512.0);
            if (lane < 0) lane = 0;
            if (lane >= keyCount) lane = keyCount - 1;

            boolean isHold = (type & 128) != 0;
            long durationMs = 0;

            if (isHold && parts.length >= 6) {
                String endPart = parts[5].trim();
                int colonIdx = endPart.indexOf(':');
                if (colonIdx != -1) {
                    endPart = endPart.substring(0, colonIdx);
                }
                try {
                    long endTimeMs = Long.parseLong(endPart);
                    durationMs = Math.max(0, endTimeMs - hitTimeMs);
                } catch (NumberFormatException ignored) {}
            }

            Note.Type noteType = isHold ? Note.Type.HOLD : Note.Type.TAP;
            beatmap.addNote(new Note(lane, hitTimeMs, durationMs, noteType));
        } catch (NumberFormatException ignored) {}
    }
}
