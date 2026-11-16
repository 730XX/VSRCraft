package com.vsrcraft.client.domain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vsrcraft.client.parser.OsrReplayParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Repositorio para guardar y cargar replays en .minecraft/vsrg_replays/.
 */
public class ReplayRepository {
    private static final Gson GSON = new GsonBuilder().create();

    public static File getReplaysDir() {
        return FabricLoader.getInstance().getGameDir().resolve("vsrg_replays").toFile();
    }

    private static File getReplayFile(String beatmapId) {
        String safeId = (beatmapId == null || beatmapId.isEmpty())
                ? "unknown"
                : beatmapId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return new File(getReplaysDir(), safeId + ".json");
    }

    public static boolean hasReplay(String beatmapId) {
        if (beatmapId == null || beatmapId.isEmpty()) return false;
        File file = getReplayFile(beatmapId);
        return file.exists() && file.isFile() && file.length() > 0;
    }

    public static boolean hasReplay(Beatmap beatmap) {
        if (beatmap == null) return false;
        return hasReplay(beatmap.getId()) || findMatchingOsrFile(beatmap.getOsuMd5()) != null;
    }

    public static void saveBestReplay(ReplayData replay) {
        if (replay == null || replay.beatmapId == null || replay.beatmapId.isEmpty() || replay.events == null || replay.events.isEmpty()) {
            return;
        }
        try {
            File dir = getReplaysDir();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File target = getReplayFile(replay.beatmapId);
            try (FileWriter writer = new FileWriter(target, StandardCharsets.UTF_8)) {
                GSON.toJson(replay, writer);
            }
        } catch (Exception e) {
            System.err.println("[VSRCraft] Error guardando replay (" + replay.beatmapId + "): " + e.getMessage());
        }
    }

    public static ReplayData loadReplay(String beatmapId) {
        if (beatmapId == null || beatmapId.isEmpty()) return null;
        File file = getReplayFile(beatmapId);
        if (!file.exists() || !file.isFile()) return null;
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            ReplayData data = GSON.fromJson(reader, ReplayData.class);
            if (data != null && data.events != null && !data.events.isEmpty()) {
                return data;
            }
        } catch (Exception e) {
            System.err.println("[VSRCraft] Error cargando replay (" + beatmapId + "): " + e.getMessage());
        }
        return null;
    }

    public static ReplayData loadReplay(Beatmap beatmap) {
        if (beatmap == null) return null;
        ReplayData local = loadReplay(beatmap.getId());
        if (local != null) {
            return local;
        }
        File osrFile = findMatchingOsrFile(beatmap.getOsuMd5());
        if (osrFile != null) {
            try {
                return OsrReplayParser.parse(osrFile.toPath(), beatmap.getKeyCount(), beatmap.getId());
            } catch (Exception e) {
                System.err.println("[VSRCraft] Error cargando .osr (" + osrFile.getName() + "): " + e.getMessage());
            }
        }
        return null;
    }

    public static File findMatchingOsrFile(String osuMd5) {
        if (osuMd5 == null || osuMd5.isEmpty()) return null;
        File dir = getReplaysDir();
        if (!dir.exists() || !dir.isDirectory()) return null;
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".osr"));
        if (files == null) return null;
        for (File file : files) {
            try {
                OsrReplayParser.OsrHeader header = OsrReplayParser.readHeader(file.toPath());
                if (header.gameMode() == 3 && osuMd5.equalsIgnoreCase(header.beatmapMd5())) {
                    return file;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static ReplayData loadOsrFile(Path osrPath, Beatmap targetBeatmap) throws Exception {
        if (targetBeatmap == null) {
            throw new IllegalArgumentException("No hay un beatmap destino para cargar el replay.");
        }
        return OsrReplayParser.parse(osrPath, targetBeatmap.getKeyCount(), targetBeatmap.getId());
    }

    public static void saveImportedOsr(Path sourceOsrPath, ReplayData parsedReplay) {
        saveBestReplay(parsedReplay);
    }
}
