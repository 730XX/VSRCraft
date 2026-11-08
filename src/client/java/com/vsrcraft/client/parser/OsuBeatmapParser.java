package com.vsrcraft.client.parser;

import com.vsrcraft.client.domain.Beatmap;
import com.vsrcraft.client.domain.Note;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class OsuBeatmapParser {

    public static Beatmap parse(InputStream inputStream) throws Exception {
        return parse(inputStream, "");
    }

    public static Beatmap parse(InputStream inputStream, String defaultAudioResourcePath) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;

        String currentSection = "";
        String beatmapId = "";
        String title = "Unknown Title";
        String artist = "Unknown Artist";
        String creator = "Unknown Mapper";
        String version = "Normal";
        String source = "";
        String tags = "";
        String audioFilename = "";
        long audioLeadInMs = 0L;
        long previewTimeMs = 0L;
        int circleSize = 4; // Teclas por defecto (4K)
        float overallDifficulty = 7.0f;
        float hpDrainRate = 7.0f;
        int bpm = 0;

        List<String> rawHitObjects = new ArrayList<>();

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;

            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length() - 1);
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
                    } else if (line.startsWith("PreviewTime:")) {
                        try {
                            previewTimeMs = Math.max(0L, Long.parseLong(line.substring("PreviewTime:".length()).trim()));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "Metadata" -> {
                    if (line.startsWith("Title:")) {
                        title = line.substring("Title:".length()).trim();
                    } else if (line.startsWith("Artist:")) {
                        artist = line.substring("Artist:".length()).trim();
                    } else if (line.startsWith("Creator:")) {
                        creator = line.substring("Creator:".length()).trim();
                    } else if (line.startsWith("Version:")) {
                        version = line.substring("Version:".length()).trim();
                    } else if (line.startsWith("Source:")) {
                        source = line.substring("Source:".length()).trim();
                    } else if (line.startsWith("Tags:")) {
                        tags = line.substring("Tags:".length()).trim();
                    } else if (line.startsWith("BeatmapID:")) {
                        beatmapId = line.substring("BeatmapID:".length()).trim();
                    }
                }
                case "Difficulty" -> {
                    if (line.startsWith("CircleSize:")) {
                        try {
                            circleSize = Math.max(1, (int) Float.parseFloat(line.substring("CircleSize:".length()).trim()));
                        } catch (NumberFormatException ignored) {}
                    } else if (line.startsWith("OverallDifficulty:")) {
                        try {
                            overallDifficulty = Float.parseFloat(line.substring("OverallDifficulty:".length()).trim());
                        } catch (NumberFormatException ignored) {}
                    } else if (line.startsWith("HPDrainRate:")) {
                        try {
                            hpDrainRate = Float.parseFloat(line.substring("HPDrainRate:".length()).trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "TimingPoints" -> {
                    if (bpm <= 0) {
                        String[] parts = line.split(",");
                        if (parts.length >= 2) {
                            try {
                                double beatLength = Double.parseDouble(parts[1].trim());
                                if (beatLength > 0.0) {
                                    bpm = (int) Math.round(60000.0 / beatLength);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                case "HitObjects" -> rawHitObjects.add(line);
            }
        }

        if (bpm <= 0) {
            bpm = 120;
        }
        if (beatmapId.isEmpty() || beatmapId.equals("0")) {
            beatmapId = (title + "_" + artist + "_" + version).replaceAll("[^a-zA-Z0-9_-]", "_");
        }

        Beatmap beatmap = new Beatmap(
                beatmapId,
                title,
                artist,
                creator,
                version,
                source,
                tags,
                circleSize,
                overallDifficulty,
                hpDrainRate,
                bpm,
                audioFilename,
                defaultAudioResourcePath,
                audioLeadInMs,
                previewTimeMs
        );

        for (String objLine : rawHitObjects) {
            parseHitObject(objLine, beatmap, circleSize);
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
