package com.vsrcraft.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Modelo de datos persistente para las configuraciones y personalizaciones de VSRCraft.
 */
public class VSRCraftSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "vsrcraft.json");

    private static VSRCraftSettings instance;

    // 1. Audio
    public float musicVolume = 0.5f;       // Por defecto 50%
    public float hitSoundVolume = 0.7f;    // Por defecto 70%
    public boolean enableHitSounds = true; // Hitsounds activados por defecto
    public long globalAudioOffsetMs = 0L;  // Offset en ms

    // 2. Jugabilidad / Scroll
    public float scrollSpeed = 0.5f;       // Píxeles por ms (Mania ~2.5)
    public int noteHeight = 12;            // Grosor de notas (px)

    // 3. Estética / Pista
    public float laneAlpha = 0.8f;         // Opacidad de pista (0.0 = transparente, 1.0 = opaco)

    public static synchronized VSRCraftSettings get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static synchronized void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                instance = GSON.fromJson(reader, VSRCraftSettings.class);
            } catch (Exception e) {
                System.err.println("[VSRCraft] Error cargando configuración: " + e.getMessage());
            }
        }
        if (instance == null) {
            instance = new VSRCraftSettings();
            save();
        }
    }

    public static synchronized void save() {
        if (instance == null) {
            instance = new VSRCraftSettings();
        }
        try {
            File dir = CONFIG_FILE.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(instance, writer);
            }
        } catch (Exception e) {
            System.err.println("[VSRCraft] Error guardando configuración: " + e.getMessage());
        }
    }
}
