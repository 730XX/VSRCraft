package com.vsrcraft.client.domain;

import com.vsrcraft.client.parser.OsuBeatmapParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Catálogo centralizado de Beatmaps de VSRCraft.
 * Carga tanto los beatmaps integrados en los recursos del mod como cualquier
 * beatmap externo (.osu + .ogg) colocado en la carpeta "vsrg_beatmaps" de Minecraft.
 */
public class BeatmapRepository {
    private static final List<Beatmap> cachedBeatmaps = new ArrayList<>();
    private static boolean initialized = false;

    private record BundledBeatmapEntry(String osuResourcePath, String audioResourcePath) {}

    private static final BundledBeatmapEntry[] BUNDLED_BEATMAPS = {
            new BundledBeatmapEntry("/assets/vsrcraft/beatmaps/whatsup.osu", "/assets/vsrcraft/sounds/music/whatsup.ogg"),
            new BundledBeatmapEntry("/assets/vsrcraft/beatmaps/elpis.osu", "/assets/vsrcraft/sounds/music/elpis.ogg")
    };

    public static synchronized List<Beatmap> getBeatmaps() {
        if (!initialized) {
            reload();
        }
        return new ArrayList<>(cachedBeatmaps);
    }

    public static synchronized void reload() {
        cachedBeatmaps.clear();
        initialized = true;

        // 1. Cargar beatmaps integrados del mod
        for (BundledBeatmapEntry entry : BUNDLED_BEATMAPS) {
            try (InputStream stream = BeatmapRepository.class.getResourceAsStream(entry.osuResourcePath())) {
                if (stream != null) {
                    Beatmap map = OsuBeatmapParser.parse(stream, entry.audioResourcePath());
                    if (!map.getNotes().isEmpty()) {
                        cachedBeatmaps.add(map);
                    }
                }
            } catch (Exception e) {
                System.err.println("[VSRCraft] Error cargando beatmap integrado " + entry.osuResourcePath() + ": " + e.getMessage());
            }
        }

        // 2. Escanear carpeta externa .minecraft/vsrg_beatmaps/ para soporte de canciones personalizadas
        File externalDir = getExternalBeatmapsDir();
        if (!externalDir.exists()) {
            externalDir.mkdirs();
        }
        scanDirectoryRecursively(externalDir, 0);

        // Ordenar alfabéticamente por título y luego por dificultad estimada
        cachedBeatmaps.sort(
                Comparator.comparing((Beatmap b) -> b.getTitle().toLowerCase(Locale.ROOT))
                        .thenComparingDouble(Beatmap::getEstimatedStarRating)
        );
    }

    private static void scanDirectoryRecursively(File dir, int depth) {
        if (dir == null || !dir.isDirectory() || depth > 2) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectoryRecursively(file, depth + 1);
            } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".osu")) {
                loadExternalOsuFile(file);
            }
        }
    }

    private static void loadExternalOsuFile(File osuFile) {
        try (InputStream stream = new FileInputStream(osuFile)) {
            Beatmap map = OsuBeatmapParser.parse(stream, "");
            if (map.getNotes().isEmpty()) return;

            File parentDir = osuFile.getParentFile();
            String audioFileName = map.getAudioFilename();
            File audioFile = null;

            if (audioFileName != null && !audioFileName.isEmpty()) {
                File candidate = new File(parentDir, audioFileName);
                if (candidate.exists() && candidate.isFile()) {
                    audioFile = candidate;
                }
            }

            // Si el nombre en AudioFilename no coincide o es MP3 pero hay un .ogg en la carpeta, buscar el primer .ogg
            if (audioFile == null && parentDir != null) {
                File[] siblings = parentDir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".ogg"));
                if (siblings != null && siblings.length > 0) {
                    audioFile = siblings[0];
                }
            }

            if (audioFile != null && audioFile.exists()) {
                map.setAudioResourcePath("file:" + audioFile.getAbsolutePath());
                cachedBeatmaps.add(map);
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
