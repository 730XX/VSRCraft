package com.vsrcraft.sound;

import com.vsrcraft.VSRCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class ModSounds {
    public static final Identifier HIT_CLAP_ID = Identifier.fromNamespaceAndPath(VSRCraft.MOD_ID, "hit_clap");
    public static final SoundEvent HIT_CLAP = SoundEvent.createVariableRangeEvent(HIT_CLAP_ID);

    public static final Identifier MUSIC_ELPIS_ID = Identifier.fromNamespaceAndPath(VSRCraft.MOD_ID, "music.elpis");
    public static final SoundEvent MUSIC_ELPIS = SoundEvent.createVariableRangeEvent(MUSIC_ELPIS_ID);

    public static final Identifier MUSIC_WHATSUP_ID = Identifier.fromNamespaceAndPath(VSRCraft.MOD_ID, "music.whatsup");
    public static final SoundEvent MUSIC_WHATSUP = SoundEvent.createVariableRangeEvent(MUSIC_WHATSUP_ID);

    public static void registerSounds() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, HIT_CLAP_ID, HIT_CLAP);
        Registry.register(BuiltInRegistries.SOUND_EVENT, MUSIC_ELPIS_ID, MUSIC_ELPIS);
        Registry.register(BuiltInRegistries.SOUND_EVENT, MUSIC_WHATSUP_ID, MUSIC_WHATSUP);
    }
}
