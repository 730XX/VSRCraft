package com.vsrcraft.client.audio;

import com.vsrcraft.client.config.VSRCraftSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Objects;
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
    private static String currentAudioPath = null;
    private static int musicBufferId = 0;
    private static int musicSourceId = 0;
    private static int musicSampleRate = 44100;
    private static long musicDurationMs = 0L;
    private static boolean musicReady = false;
    private static boolean musicPlaying = false;
    private static boolean musicPaused = false;
    private static boolean musicFinished = false;

    // Estado para el Preview de audio en el Selector de Canciones
    private static boolean pendingPreview = false;
    private static long pendingPreviewOffsetMs = 0L;
    private static boolean previewMode = false;

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
     */
    public static synchronized void preloadMusic(SoundEvent sound) {
        String resourcePath = resolveResourcePath(sound);
        preloadMusicFromResource(resourcePath);
    }

    public static synchronized void preloadMusicFromResource(String resourcePath) {
        stopMusic();

        if (resourcePath == null || resourcePath.isEmpty()) {
            resourcePath = "/assets/vsrcraft/sounds/music/whatsup.ogg";
        }

        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getMusicManager() != null) {
            client.getMusicManager().stopPlaying();
        }

        currentAudioPath = resourcePath;
        final String pathToLoad = resourcePath;
        preloadFuture = CompletableFuture.supplyAsync(() -> decodeOggResource(pathToLoad));
    }

    /**
     * Solicita reproducir el preview de una canción en el selector de beatmaps (sin bloquear el hilo de render).
     */
    public static synchronized void requestPreview(String resourcePath, long previewTimeMs) {
        if (resourcePath == null || resourcePath.isEmpty()) return;

        // Si ya está sonando o cargando exactamente este mismo preview, no reiniciarlo
        if (previewMode && Objects.equals(currentAudioPath, resourcePath) && (musicPlaying || preloadFuture != null)) {
            return;
        }

        preloadMusicFromResource(resourcePath);
        previewMode = true;
        pendingPreview = true;
        pendingPreviewOffsetMs = Math.max(0L, previewTimeMs);
    }

    /**
     * Llamado en cada fotograma de SongSelectScreen para arrancar el preview apenas termine la decodificación asíncrona.
     */
    public static synchronized void updatePreview() {
        if (!pendingPreview) return;
        if (!ensureMusicReady() || musicSourceId == 0) return;

        pendingPreview = false;
        previewMode = true;
        updateMusicVolume();
        AL10.alSourcei(musicSourceId, AL10.AL_LOOPING, AL10.AL_TRUE);
        AL10.alSourceRewind(musicSourceId);

        float seekSeconds = pendingPreviewOffsetMs / 1000.0f;
        if (musicDurationMs > 0 && pendingPreviewOffsetMs >= musicDurationMs) {
            seekSeconds = (musicDurationMs * 0.35f) / 1000.0f;
        }
        if (seekSeconds > 0.0f) {
            AL10.alSourcef(musicSourceId, AL11.AL_SEC_OFFSET, seekSeconds);
        }

        AL10.alSourcePlay(musicSourceId);
        musicPlaying = true;
        musicPaused = false;
        musicFinished = false;
    }

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
                updateMusicVolume();

                musicSampleRate = decoded.sampleRate;
                musicDurationMs = (decoded.totalSamples * 1000L) / Math.max(1, decoded.sampleRate);
                musicReady = true;
                return true;
            } finally {
                // STBVorbis aloca memoria internamente con malloc de C; debe liberarse con LibCStdlib.free
                // (MemoryUtil.memFree fallaría en Minecraft porque Minecraft usa jemalloc como alocador primario)
                LibCStdlib.free(decoded.pcm);
            }
        } catch (Exception e) {
            e.printStackTrace();
            preloadFuture = null;
            return false;
        }
    }

    public static synchronized void startMusic(SoundEvent sound) {
        if (!musicReady && preloadFuture == null) {
            preloadMusic(sound);
        }
        if (preloadFuture != null) {
            try {
                preloadFuture.join();
            } catch (Exception ignored) {}
            ensureMusicReady();
        }
        startPreloadedMusic();
    }

    public static synchronized void startPreloadedMusic() {
        if (!ensureMusicReady() || musicSourceId == 0) return;

        previewMode = false;
        pendingPreview = false;
        updateMusicVolume();
        AL10.alSourcei(musicSourceId, AL10.AL_LOOPING, AL10.AL_FALSE);
        AL10.alSourceRewind(musicSourceId);
        AL10.alSourcef(musicSourceId, AL11.AL_SEC_OFFSET, 0.0f);
        AL10.alSourcePlay(musicSourceId);

        long now = System.nanoTime();
        lastSampleOffset = 0;
        lastHardwareTimeMs = 0.0;
        lastHardwareUpdateNano = now;
        lastReturnedTimeMs = 0.0;
        musicFinishedNano = 0L;
        musicPlaying = true;
        musicPaused = false;
        musicFinished = false;
    }

    public static synchronized void pauseMusic() {
        if (musicPlaying && musicSourceId != 0 && !musicPaused) {
            AL10.alSourcePause(musicSourceId);
            musicPaused = true;
        }
    }

    public static synchronized void resumeMusic() {
        if (musicPlaying && musicSourceId != 0 && musicPaused) {
            AL10.alSourcePlay(musicSourceId);
            musicPaused = false;
            lastHardwareUpdateNano = System.nanoTime();
        }
    }

    public static synchronized void updateMusicVolume() {
        if (musicSourceId != 0) {
            float baseVol = getEffectiveVolume(SoundSource.MASTER);
            float customVol = VSRCraftSettings.get().musicVolume; // Por defecto 50%
            AL10.alSourcef(musicSourceId, AL10.AL_GAIN, baseVol * customVol);
        }
    }

    public static synchronized double getMusicTimeMs() {
        if (!musicPlaying || musicSourceId == 0) {
            return 0.0;
        }
        if (musicPaused) {
            return lastReturnedTimeMs;
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

            double elapsedSinceUpdateMs = (nowNano - lastHardwareUpdateNano) / 1_000_000.0;
            double interpolatedMs = lastHardwareTimeMs + Math.min(25.0, Math.max(0.0, elapsedSinceUpdateMs));

            if (interpolatedMs < lastReturnedTimeMs && (lastReturnedTimeMs - hardwareMs) < 25.0) {
                interpolatedMs = lastReturnedTimeMs;
            }

            lastReturnedTimeMs = interpolatedMs;
            return interpolatedMs;
        } else if (state == AL10.AL_STOPPED && lastSampleOffset >= 0) {
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
        return musicPlaying && !musicPaused;
    }

    public static synchronized void playHitSound() {
        VSRCraftSettings settings = VSRCraftSettings.get();
        if (!settings.enableHitSounds) return;

        initHitSoundPoolIfNeeded();
        if (!hitSoundInitialized || hitSoundBufferId == 0) return;

        int source = hitSoundSources[hitSoundCursor];
        hitSoundCursor = (hitSoundCursor + 1) % HIT_SOUND_POOL_SIZE;

        if (source != 0) {
            AL10.alSourceStop(source);
            float baseVol = getEffectiveVolume(SoundSource.MASTER);
            float gain = baseVol * settings.hitSoundVolume;
            AL10.alSourcef(source, AL10.AL_GAIN, gain);
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
            LibCStdlib.free(decoded.pcm);
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
        currentAudioPath = null;
        musicReady = false;
        musicPlaying = false;
        musicPaused = false;
        musicFinished = false;
        pendingPreview = false;
        previewMode = false;
        lastSampleOffset = -1;
        lastHardwareTimeMs = 0.0;
        lastReturnedTimeMs = 0.0;
    }

    private static InputStream openAudioStream(String resourcePath) throws Exception {
        if (resourcePath.startsWith("file:")) {
            File file = new File(resourcePath.substring("file:".length()));
            if (file.exists() && file.isFile()) {
                return new FileInputStream(file);
            }
            return null;
        }
        return AudioManager.class.getResourceAsStream(resourcePath);
    }

    private static DecodedAudio decodeOggResource(String resourcePath) {
        ByteBuffer vorbisBytes = null;
        try (InputStream stream = openAudioStream(resourcePath)) {
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
        String path = sound.location().getPath();
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
