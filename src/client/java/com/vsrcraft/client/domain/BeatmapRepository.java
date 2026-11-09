package com.vsrcraft.client.domain;

import com.vsrcraft.client.parser.OsuBeatmapParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catálogo centralizado de Beatmaps y Canciones (BeatmapSets) de VSRCraft.
 * Agrupa automáticamente las dificultades por canción/set para un selector limpio.
 */
public class BeatmapRepository {
    private static final List<BeatmapSet> cachedSets = new ArrayList<>();
    private static final List<Beatmap> cachedBeatmaps = new ArrayList<>();
    private static boolean initialized = false;

    private record BundledBeatmapEntry(String osuResourcePath, String audioResourcePath) {}

    private static final BundledBeatmapEntry[] BUNDLED_BEATMAPS = {
            new BundledBeatmapEntry("/assets/vsrcraft/beatmaps/whatsup.osu", "/assets/vsrcraft/sounds/music/whatsup.ogg"),
            new BundledBeatmapEntry("/assets/vsrcraft/beatmaps/elpis.osu", "/assets/vsrcraft/sounds/music/elpis.ogg")
    };

    public static synchronized List<BeatmapSet> getBeatmapSets() {
        if (!initialized) {
            reload();
        }
        return new ArrayList<>(cachedSets);
    }

    public static synchronized List<Beatmap> getBeatmaps() {
        if (!initialized) {
            reload();
        }
        return new ArrayList<>(cachedBeatmaps);
    }

    public static synchronized BeatmapSet findSetForBeatmap(Beatmap beatmap) {
        if (beatmap == null) return null;
        for (BeatmapSet set : getBeatmapSets()) {
            for (Beatmap diff : set.getDifficulties()) {
                if (diff.getId().equals(beatmap.getId())) {
                    return set;
                }
            }
        }
        return null;
    }

    public static synchronized void reload() {
        cachedSets.clear();
        cachedBeatmaps.clear();
        initialized = true;

        Map<String, BeatmapSet> setMap = new LinkedHashMap<>();

        // 1. Cargar beatmaps integrados del mod
        for (BundledBeatmapEntry entry : BUNDLED_BEATMAPS) {
            try (InputStream stream = BeatmapRepository.class.getResourceAsStream(entry.osuResourcePath())) {
                if (stream != null) {
                    Beatmap map = OsuBeatmapParser.parse(stream, entry.audioResourcePath());
                    if (!map.getNotes().isEmpty()) {
                        registerBeatmap(map, setMap);
                    }
                }
            } catch (Exception e) {
                System.err.println("[VSRCraft] Error cargando beatmap integrado " + entry.osuResourcePath() + ": " + e.getMessage());
            }
        }

        // 2. Escanear carpeta externa .minecraft/vsrg_beatmaps/ para canciones personalizadas
        File externalDir = getExternalBeatmapsDir();
        if (!externalDir.exists()) {
            externalDir.mkdirs();
        }
        scanDirectoryRecursively(externalDir, 0, setMap);

        // 3. Consolidar y ordenar canciones y dificultades
        cachedSets.addAll(setMap.values());
        cachedSets.sort(Comparator.comparing((BeatmapSet s) -> s.getTitle().toLowerCase(Locale.ROOT)));

        for (BeatmapSet set : cachedSets) {
            cachedBeatmaps.addAll(set.getDifficulties());
        }
    }

    private static void registerBeatmap(Beatmap map, Map<String, BeatmapSet> setMap) {
        String rawSetId = map.getBeatmapSetId();
        final String setId = (rawSetId != null && !rawSetId.isEmpty() && !rawSetId.equals("0"))
                ? rawSetId
                : (map.getArtist() + "_" + map.getTitle()).replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase(Locale.ROOT);

        BeatmapSet set = setMap.computeIfAbsent(setId, k -> new BeatmapSet(
                k,
                map.getTitle(),
                map.getArtist(),
                map.getCreator(),
                map.getSource()
        ));
        set.addDifficulty(map);
    }

    private static void scanDirectoryRecursively(File dir, int depth, Map<String, BeatmapSet> setMap) {
        if (dir == null || !dir.isDirectory() || depth > 2) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectoryRecursively(file, depth + 1, setMap);
            } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".osu")) {
                loadExternalOsuFile(file, setMap);
            }
        }
    }

    private static void loadExternalOsuFile(File osuFile, Map<String, BeatmapSet> setMap) {
        try (InputStream stream = new FileInputStream(osuFile)) {
            Beatmap map = OsuBeatmapParser.parse(stream, "");
            // Aceptar exclusivamente mapas de Mania (Mode: 3)
            if (map.getNotes().isEmpty() || map.getGameMode() != 3) {
                return;
            }

            File parentDir = osuFile.getParentFile();
            String audioFileName = map.getAudioFilename();
            File audioFile = null;

            if (audioFileName != null && !audioFileName.isEmpty()) {
                File candidate = new File(parentDir, audioFileName);
                if (candidate.exists() && candidate.isFile()) {
                    audioFile = candidate;
                }
            }

            // Si el nombre no coincide exactamente pero hay un .ogg en la carpeta, usar el primer .ogg
            if (audioFile == null && parentDir != null) {
                File[] siblings = parentDir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".ogg"));
                if (siblings != null && siblings.length > 0) {
                    audioFile = siblings[0];
                }
            }

            if (audioFile != null && audioFile.exists()) {
                map.setAudioResourcePath("file:" + audioFile.getAbsolutePath());
                registerBeatmap(map, setMap);
            }
        } catch (Exception e) {
            System.err.println("[VSRCraft] Error leyendo beatmap externo " + osuFile.getName() + ": " + e.getMessage());
        }
    }

    public static File getExternalBeatmapsDir() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        return gameDir.resolve("vsrg_beatmaps").toFile();
    }
}
