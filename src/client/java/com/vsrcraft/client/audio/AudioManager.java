package com.vsrcraft.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Motor de audio de baja latencia basado directamente en OpenAL + STBVorbis.
 * Actúa como el Reloj Maestro (Master Audio Clock) estilo osu! para garantizar
 * sincronización exacta a nivel de muestras (AL_SAMPLE_OFFSET) con interpolación sub-milisegundo.
 */
public class AudioManager {

    private static class DecodedAudio {
        final ShortBuffer pcm;
        final int channels;
        final int sampleRate;
        final int totalSamples;

        DecodedAudio(ShortBuffer pcm, int channels, int sampleRate, int totalSamples) {
            this.pcm = pcm;
            this.channels = channels;
            this.sampleRate = sampleRate;
            this.totalSamples = totalSamples;
        }
    }

    // Estado de la pista musical en OpenAL
    private static CompletableFuture<DecodedAudio> preloadFuture = null;
    private static int musicBufferId = 0;
    private static int musicSourceId = 0;
    private static int musicSampleRate = 44100;
    private static long musicDurationMs = 0L;
    private static boolean musicReady = false;
    private static boolean musicPlaying = false;
    private static boolean musicFinished = false;

    // Estado del Reloj Maestro Híbrido (OpenAL Sample Offset + Interpolación NanoTime)
    private static int lastSampleOffset = -1;
    private static double lastHardwareTimeMs = 0.0;
    private static long lastHardwareUpdateNano = 0L;
    private static double lastReturnedTimeMs = 0.0;
    private static long musicFinishedNano = 0L;

    // Pool de baja latencia para Hitsounds (evita cortes en acordes de múltiples notas)
    private static int hitSoundBufferId = 0;
    private static final int HIT_SOUND_POOL_SIZE = 8;
    private static final int[] hitSoundSources = new int[HIT_SOUND_POOL_SIZE];
    private static int hitSoundCursor = 0;
    private static boolean hitSoundInitialized = false;

    /**
     * Precarga en segundo plano el archivo .ogg asociado al SoundEvent o ruta de recursos.
     * Se ejecuta durante el Lead-In para que al llegar al ms 0 el arranque sea instantáneo (0ms de latencia).
     */
    public static synchronized void preloadMusic(SoundEvent sound) {
        String resourcePath = resolveResourcePath(sound);
        preloadMusicFromResource(resourcePath);
    }

    public static synchronized void preloadMusicFromResource(String resourcePath) {
        stopMusic();

        // Detener música ambiental de Minecraft para que no se mezcle con el beatmap
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getMusicManager() != null) {
            client.getMusicManager().stopPlaying();
        }

        preloadFuture = CompletableFuture.supplyAsync(() -> decodeOggResource(resourcePath));
    }

    /**
     * Verifica si la decodificación en segundo plano terminó y sube el buffer PCM a OpenAL.
     * Debe llamarse desde el hilo principal (render/tick).
     */
    public static synchronized boolean ensureMusicReady() {
        if (musicReady) return true;
        if (preloadFuture == null || !preloadFuture.isDone()) return false;

        try {
            DecodedAudio decoded = preloadFuture.get();
            preloadFuture = null;
            if (decoded == null || decoded.pcm == null) {
                return false;
            }

            try {
                int format = (decoded.channels == 1) ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
                musicBufferId = AL10.alGenBuffers();
                AL10.alBufferData(musicBufferId, format, decoded.pcm, decoded.sampleRate);

                musicSourceId = AL10.alGenSources();
                AL10.alSourcei(musicSourceId, AL10.AL_BUFFER, musicBufferId);
                AL10.alSourcei(musicSourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                AL10.alSource3f(musicSourceId, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f);
                AL10.alSourcef(musicSourceId, AL10.AL_ROLLOFF_FACTOR, 0.0f);
                AL10.alSourcef(musicSourceId, AL10.AL_GAIN, getEffectiveVolume(SoundSource.MASTER));

                musicSampleRate = decoded.sampleRate;
                musicDurationMs = (decoded.totalSamples * 1000L) / Math.max(1, decoded.sampleRate);
                musicReady = true;
                return true;
            } finally {
                MemoryUtil.memFree(decoded.pcm);
            }
        } catch (Exception e) {
            e.printStackTrace();
            preloadFuture = null;
            return false;
        }
    }

    /**
     * Inicia la reproducción inmediata de la música precargada en OpenAL.
     */
    public static synchronized void startMusic(SoundEvent sound) {
        if (!musicReady && preloadFuture == null) {
            preloadMusic(sound);
        }
        if ( preloadFuture != null ) {
            // Si aún estaba terminando de decodificar, esperar brevemente y subir a OpenAL
            try {
                preloadFuture.join();
            } catch (Exception ignored) {}
            ensureMusicReady();
        }
        startPreloadedMusic();
    }

    public static synchronized void startPreloadedMusic() {
        if (!ensureMusicReady() || musicSourceId == 0) return;

        AL10.alSourcef(musicSourceId, AL10.AL_GAIN, getEffectiveVolume(SoundSource.MASTER));
        AL10.alSourceRewind(musicSourceId);
        AL10.alSourcePlay(musicSourceId);

        long now = System.nanoTime();
        lastSampleOffset = 0;
        lastHardwareTimeMs = 0.0;
        lastHardwareUpdateNano = now;
        lastReturnedTimeMs = 0.0;
        musicFinishedNano = 0L;
        musicPlaying = true;
        musicFinished = false;
    }

    /**
     * Devuelve la posición exacta actual de la canción en milisegundos (con precisión sub-ms)
     * sincronizada directamente con el cursor de hardware de OpenAL (AL_SAMPLE_OFFSET).
     */
    public static synchronized double getMusicTimeMs() {
        if (!musicPlaying || musicSourceId == 0) {
            return 0.0;
        }

        long nowNano = System.nanoTime();
        int state = AL10.alGetSourcei(musicSourceId, AL10.AL_SOURCE_STATE);

        if (state == AL10.AL_PLAYING) {
            int sampleOffset = AL10.alGetSourcei(musicSourceId, AL11.AL_SAMPLE_OFFSET);
            double hardwareMs = (sampleOffset * 1000.0) / musicSampleRate;

            if (sampleOffset != lastSampleOffset) {
                lastSampleOffset = sampleOffset;
                lastHardwareTimeMs = hardwareMs;
                lastHardwareUpdateNano = nowNano;
            }

            // Interpolación suave entre períodos de mezcla de OpenAL (~10-20ms)
            double elapsedSinceUpdateMs = (nowNano - lastHardwareUpdateNano) / 1_000_000.0;
            double interpolatedMs = lastHardwareTimeMs + Math.min(25.0, Math.max(0.0, elapsedSinceUpdateMs));

            // Garantizar avance monótono sin micro-saltos hacia atrás, manteniéndose atado al hardware
            if (interpolatedMs < lastReturnedTimeMs && (lastReturnedTimeMs - hardwareMs) < 25.0) {
                interpolatedMs = lastReturnedTimeMs;
            }

            lastReturnedTimeMs = interpolatedMs;
            return interpolatedMs;
        } else if (state == AL10.AL_STOPPED && lastSampleOffset >= 0) {
            // La canción llegó al final; continuar el reloj suavemente para que terminen las notas finales
            if (!musicFinished) {
                musicFinished = true;
                musicFinishedNano = nowNano;
                lastReturnedTimeMs = Math.max(lastReturnedTimeMs, musicDurationMs);
            }
            double extraMs = (nowNano - musicFinishedNano) / 1_000_000.0;
            return lastReturnedTimeMs + extraMs;
        }

        return lastReturnedTimeMs;
    }

    public static synchronized boolean isMusicPlaying() {
        return musicPlaying;
    }

    public static synchronized void playHitSound() {
        initHitSoundPoolIfNeeded();
        if (!hitSoundInitialized || hitSoundBufferId == 0) return;

        int source = hitSoundSources[hitSoundCursor];
        hitSoundCursor = (hitSoundCursor + 1) % HIT_SOUND_POOL_SIZE;

        if (source != 0) {
            AL10.alSourceStop(source);
            AL10.alSourcef(source, AL10.AL_GAIN, getEffectiveVolume(SoundSource.MASTER) * 0.85f);
            AL10.alSourcePlay(source);
        }
    }

    private static void initHitSoundPoolIfNeeded() {
        if (hitSoundInitialized) return;
        hitSoundInitialized = true;

        DecodedAudio decoded = decodeOggResource("/assets/vsrcraft/sounds/sfx/hit_clap.ogg");
        if (decoded == null || decoded.pcm == null) return;

        try {
            int format = (decoded.channels == 1) ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            hitSoundBufferId = AL10.alGenBuffers();
            AL10.alBufferData(hitSoundBufferId, format, decoded.pcm, decoded.sampleRate);

            for (int i = 0; i < HIT_SOUND_POOL_SIZE; i++) {
                int src = AL10.alGenSources();
                AL10.alSourcei(src, AL10.AL_BUFFER, hitSoundBufferId);
                AL10.alSourcei(src, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                AL10.alSource3f(src, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f);
                AL10.alSourcef(src, AL10.AL_ROLLOFF_FACTOR, 0.0f);
                hitSoundSources[i] = src;
            }
        } finally {
            MemoryUtil.memFree(decoded.pcm);
        }
    }

    public static synchronized void stopMusic() {
        if (preloadFuture != null) {
            preloadFuture.cancel(true);
            preloadFuture = null;
        }
        if (musicSourceId != 0) {
            AL10.alSourceStop(musicSourceId);
            AL10.alDeleteSources(musicSourceId);
            musicSourceId = 0;
        }
        if (musicBufferId != 0) {
            AL10.alDeleteBuffers(musicBufferId);
            musicBufferId = 0;
        }
        musicReady = false;
        musicPlaying = false;
        musicFinished = false;
        lastSampleOffset = -1;
        lastHardwareTimeMs = 0.0;
        lastReturnedTimeMs = 0.0;
    }

    private static DecodedAudio decodeOggResource(String resourcePath) {
        ByteBuffer vorbisBytes = null;
        try (InputStream stream = AudioManager.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                System.err.println("[VSRCraft] No se encontró el recurso de audio: " + resourcePath);
                return null;
            }
            byte[] rawBytes = stream.readAllBytes();
            vorbisBytes = MemoryUtil.memAlloc(rawBytes.length);
            vorbisBytes.put(rawBytes).flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer channelsBuf = stack.mallocInt(1);
                IntBuffer sampleRateBuf = stack.mallocInt(1);
                ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(vorbisBytes, channelsBuf, sampleRateBuf);
                if (pcm == null) {
                    System.err.println("[VSRCraft] Error decodificando OGG con STBVorbis: " + resourcePath);
                    return null;
                }
                int channels = channelsBuf.get(0);
                int sampleRate = sampleRateBuf.get(0);
                int totalSamples = pcm.limit() / Math.max(1, channels);
                return new DecodedAudio(pcm, channels, sampleRate, totalSamples);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (vorbisBytes != null) {
                MemoryUtil.memFree(vorbisBytes);
            }
        }
    }

    private static String resolveResourcePath(SoundEvent sound) {
        if (sound == null) return "/assets/vsrcraft/sounds/music/whatsup.ogg";
        String path = sound.location().getPath(); // ej: "music.whatsup" o "music.elpis"
        if (path.startsWith("music.")) {
            String trackName = path.substring("music.".length());
            return "/assets/vsrcraft/sounds/music/" + trackName + ".ogg";
        }
        return "/assets/vsrcraft/sounds/" + path.replace('.', '/') + ".ogg";
    }

    private static float getEffectiveVolume(SoundSource source) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.options != null) {
            float vol = client.options.getFinalSoundSourceVolume(source);
            return Math.max(0.05f, Math.min(1.0f, vol));
        }
        return 1.0f;
    }
}
