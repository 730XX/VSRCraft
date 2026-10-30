package com.vsrcraft.client.audio;

import com.vsrcraft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

public class AudioManager {
    private static SimpleSoundInstance currentMusicInstance;

    public static void playHitSound() {
        Minecraft client = Minecraft.getInstance();
        if (client.getSoundManager() != null) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.HIT_CLAP, 1.0f, 1.0f));
        }
    }

    public static void startMusic(SoundEvent sound) {
        stopMusic();
        Minecraft client = Minecraft.getInstance();
        if (client.getSoundManager() != null && sound != null) {
            currentMusicInstance = SimpleSoundInstance.forUI(sound, 1.0f, 1.0f);
            client.getSoundManager().play(currentMusicInstance);
        }
    }

    public static void stopMusic() {
        Minecraft client = Minecraft.getInstance();
        if (client.getSoundManager() != null && currentMusicInstance != null) {
            client.getSoundManager().stop(currentMusicInstance);
            currentMusicInstance = null;
        }
    }
}
