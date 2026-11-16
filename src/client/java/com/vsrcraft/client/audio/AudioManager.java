package com.vsrcraft.client.audio;

import com.vsrcraft.client.config.VSRCraftSettings;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Motor de audio de baja latencia basado directamente en OpenAL (con soporte multi-formato OGG, MP3 y WAV).
 * Actúa como el Reloj Maestro (Master Audio Clock) estilo osu! para garantizar
 * sincronización exacta a nivel de muestras (AL_SAMPLE_OFFSET) con interpolación sub-milisegundo.
 */
public class AudioManager {

    private static class DecodedAudio {
        final ShortBuffer pcm;
        final int channels;
        final int sampleRate;
        final int totalSamples;
        final boolean allocatedByStb;

        DecodedAudio(ShortBuffer pcm, int channels, int sampleRate, int totalSamples, boolean allocatedByStb) {
            this.pcm = pcm;
            this.channels = channels;
            this.sampleRate = sampleRate;
            this.totalSamples = totalSamples;
            this.allocatedByStb = allocatedByStb;
        }

        void freePcm() {
            if (pcm == null) return;
            if (allocatedByStb) {
                // STBVorbis aloca memoria internamente con malloc de C; debe liberarse con LibCStdlib.free
                // (MemoryUtil.memFree fallaría en Minecraft porque Minecraft usa jemalloc como alocador primario)
                LibCStdlib.free(pcm);
            } else {
                MemoryUtil.memFree(pcm);
            }
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
    private static float musicPlaybackRate = 1.0f;

    // Estado para el Preview de audio en el Selector de Canciones (Fade-In suave al abrir, rápido al cambiar)
    private static final long PREVIEW_INITIAL_DELAY_MS = 200L;
    private static final long PREVIEW_SWITCH_DELAY_MS = 40L;
    private static final double PREVIEW_INITIAL_FADE_IN_MS = 1800.0;
    private static final double PREVIEW_SWITCH_FADE_IN_MS = 450.0;
    private static boolean pendingPreview = false;
    private static long pendingPreviewOffsetMs = 0L;
    private static long currentPreviewDelayMs = PREVIEW_INITIAL_DELAY_MS;
    private static double currentPreviewFadeInMs = PREVIEW_INITIAL_FADE_IN_MS;
    private static long previewRequestNano = 0L;
    private static long previewFadeStartNano = 0L;
    private static boolean previewMode = false;

    // Estado de Fade-Out al salir de pantallas
    private static boolean fadeOutActive = false;
    private static long fadeOutStartNano = 0L;
    private static double fadeOutDurationMs = 250.0;

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
     * Precarga en segundo plano el archivo de audio asociado al SoundEvent o ruta de recursos.
     */
    public static synchronized void preloadMusic(SoundEvent sound) {
        String resourcePath = resolveResourcePath(sound);
        preloadMusicFromResource(resourcePath);
    }

    public static synchronized void preloadMusicFromResource(String resourcePath) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            resourcePath = "/assets/vsrcraft/sounds/music/whatsup.ogg";
        }

        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getMusicManager() != null) {
            client.getMusicManager().stopPlaying();
        }

        // Si este mismo archivo ya fue decodificado (ej. por el preview del selector) o se está decodificando,
        // reutilizamos el buffer de OpenAL sin volver a decodificar todo el MP3/OGG desde cero.
        if (Objects.equals(currentAudioPath, resourcePath) && (musicReady || preloadFuture != null)) {
            if (musicSourceId != 0) {
                AL10.alSourceStop(musicSourceId);
                AL10.alSourcei(musicSourceId, AL10.AL_LOOPING, AL10.AL_FALSE);
                AL10.alSourceRewind(musicSourceId);
                AL10.alSourcef(musicSourceId, AL11.AL_SEC_OFFSET, 0.0f);
            }
            musicPlaying = false;
            musicPaused = false;
            musicFinished = false;
            pendingPreview = false;
            previewMode = false;
            fadeOutActive = false;
            lastSampleOffset = -1;
            lastHardwareTimeMs = 0.0;
            lastReturnedTimeMs = 0.0;
            return;
        }

        stopMusic();
        currentAudioPath = resourcePath;
        final String pathToLoad = resourcePath;
        preloadFuture = CompletableFuture.supplyAsync(() -> decodeAudioResource(pathToLoad));
    }

    /**
     * Solicita reproducir el preview de una canción en el selector de beatmaps (sin bloquear el hilo de render).
     */
    public static synchronized void requestPreview(String resourcePath, long previewTimeMs) {
        requestPreview(resourcePath, previewTimeMs, true);
    }

    /**
     * Solicita reproducir el preview con fade-in largo al abrir el selector (1.8s) o rápido al cambiar de canción (~0.45s).
     */
    public static synchronized void requestPreview(String resourcePath, long previewTimeMs, boolean isInitialOpen) {
        if (resourcePath == null || resourcePath.isEmpty()) return;

        // Si ya está sonando o cargando exactamente este mismo preview, no reiniciarlo
        if (previewMode && Objects.equals(currentAudioPath, resourcePath) && (musicPlaying || preloadFuture != null)) {
            return;
        }

        preloadMusicFromResource(resourcePath);
        previewMode = true;
        pendingPreview = true;
        pendingPreviewOffsetMs = Math.max(0L, previewTimeMs);
        currentPreviewDelayMs = isInitialOpen ? PREVIEW_INITIAL_DELAY_MS : PREVIEW_SWITCH_DELAY_MS;
        currentPreviewFadeInMs = isInitialOpen ? PREVIEW_INITIAL_FADE_IN_MS : PREVIEW_SWITCH_FADE_IN_MS;
        previewRequestNano = System.nanoTime();
        fadeOutActive = false;
    }

    /**
     * Inicia un desvanecimiento suave (Fade-Out) de la música actual antes de cambiar de pantalla.
     */
    public static synchronized void fadeOutMusic(long durationMs) {
        if (!musicPlaying || musicSourceId == 0) return;
        fadeOutActive = true;
        fadeOutStartNano = System.nanoTime();
        fadeOutDurationMs = Math.max(1.0, durationMs);
    }

    /**
     * Actualiza el Fade-In del preview y cualquier Fade-Out activo en cada fotograma.
     */
    public static synchronized void updatePreview() {
        if (pendingPreview) {
            double waitedMs = (System.nanoTime() - previewRequestNano) / 1_000_000.0;
            if (waitedMs < currentPreviewDelayMs) {
                return;
            }
            if (!ensureMusicReady() || musicSourceId == 0) return;

            pendingPreview = false;
            previewMode = true;
            previewFadeStartNano = System.nanoTime();

            AL10.alSourcef(musicSourceId, AL10.AL_GAIN, 0.0f);
            AL10.alSourcef(musicSourceId, AL10.AL_PITCH, 1.0f);
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
            return;
        }

        if (musicPlaying && musicSourceId != 0 && !musicPaused) {
            if (previewMode || fadeOutActive) {
                updateMusicVolume();
            }
        }
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
                decoded.freePcm();
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

    public static synchronized void setMusicPlaybackRate(float rate) {
        musicPlaybackRate = Math.max(0.5f, Math.min(2.0f, rate));
        if (musicSourceId != 0 && !previewMode) {
            AL10.alSourcef(musicSourceId, AL10.AL_PITCH, musicPlaybackRate);
        }
    }

    public static synchronized float getMusicPlaybackRate() {
        return musicPlaybackRate;
    }

    public static synchronized void startPreloadedMusic() {
        if (!ensureMusicReady() || musicSourceId == 0) return;

        previewMode = false;
        pendingPreview = false;
        fadeOutActive = false;
        updateMusicVolume();
        AL10.alSourcef(musicSourceId, AL10.AL_PITCH, musicPlaybackRate);
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
            float fadeMultiplier = 1.0f;

            if (previewMode && previewFadeStartNano > 0L) {
                double fadeElapsedMs = (System.nanoTime() - previewFadeStartNano) / 1_000_000.0;
                float t = (float) Math.max(0.0, Math.min(1.0, fadeElapsedMs / currentPreviewFadeInMs));
                fadeMultiplier *= t * t * (3.0f - 2.0f * t);
            }
            if (fadeOutActive) {
                double outElapsedMs = (System.nanoTime() - fadeOutStartNano) / 1_000_000.0;
                float t = (float) Math.max(0.0, Math.min(1.0, outElapsedMs / fadeOutDurationMs));
                fadeMultiplier *= (1.0f - (t * t * (3.0f - 2.0f * t)));
            }

            AL10.alSourcef(musicSourceId, AL10.AL_GAIN, baseVol * customVol * fadeMultiplier);
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

            double elapsedSinceUpdateMs = ((nowNano - lastHardwareUpdateNano) / 1_000_000.0) * musicPlaybackRate;
            double interpolatedMs = lastHardwareTimeMs + Math.min(25.0 * musicPlaybackRate, Math.max(0.0, elapsedSinceUpdateMs));

            if (interpolatedMs < lastReturnedTimeMs && (lastReturnedTimeMs - hardwareMs) < 25.0 * musicPlaybackRate) {
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
            double extraMs = ((nowNano - musicFinishedNano) / 1_000_000.0) * musicPlaybackRate;
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

        DecodedAudio decoded = decodeAudioResource("/assets/vsrcraft/sounds/sfx/hit_clap.ogg");
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
            decoded.freePcm();
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
        fadeOutActive = false;
        previewFadeStartNano = 0L;
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

    /**
     * Decodifica automáticamente archivos .ogg, .mp3 o .wav en PCM de 16 bits para OpenAL.
     * Incluye fallback automático por si un mapa de osu! tiene la extensión cambiada (ej. MP3 nombrado como .ogg).
     */
    private static DecodedAudio decodeAudioResource(String resourcePath) {
        try (InputStream stream = openAudioStream(resourcePath)) {
            if (stream == null) {
                System.err.println("[VSRCraft] No se encontró el recurso de audio: " + resourcePath);
                return null;
            }
            byte[] rawBytes = stream.readAllBytes();
            if (rawBytes.length == 0) {
                return null;
            }

            String lower = resourcePath.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".mp3")) {
                DecodedAudio mp3 = decodeMp3Bytes(rawBytes, resourcePath);
                if (mp3 != null) return mp3;
                return decodeOggBytes(rawBytes, resourcePath);
            } else if (lower.endsWith(".wav")) {
                DecodedAudio wav = decodeWavBytes(rawBytes, resourcePath);
                if (wav != null) return wav;
                return decodeMp3Bytes(rawBytes, resourcePath);
            } else {
                DecodedAudio ogg = decodeOggBytes(rawBytes, resourcePath);
                if (ogg != null) return ogg;
                // Fallback a MP3 por si el mapper renombró un .mp3 a .ogg
                return decodeMp3Bytes(rawBytes, resourcePath);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static DecodedAudio decodeOggBytes(byte[] rawBytes, String resourcePath) {
        ByteBuffer vorbisBytes = null;
        try {
            vorbisBytes = MemoryUtil.memAlloc(rawBytes.length);
            vorbisBytes.put(rawBytes).flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer channelsBuf = stack.mallocInt(1);
                IntBuffer sampleRateBuf = stack.mallocInt(1);
                ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(vorbisBytes, channelsBuf, sampleRateBuf);
                if (pcm == null) {
                    return null;
                }
                int channels = channelsBuf.get(0);
                int sampleRate = sampleRateBuf.get(0);
                int totalSamples = pcm.limit() / Math.max(1, channels);
                return new DecodedAudio(pcm, channels, sampleRate, totalSamples, true);
            }
        } catch (Exception e) {
            System.err.println("[VSRCraft] Error decodificando OGG con STBVorbis (" + resourcePath + "): " + e.getMessage());
            return null;
        } finally {
            if (vorbisBytes != null) {
                MemoryUtil.memFree(vorbisBytes);
            }
        }
    }

    private static DecodedAudio decodeMp3Bytes(byte[] rawBytes, String resourcePath) {
        Bitstream bitstream = null;
        try {
            // 1. Inspeccionar cabecera ID3v2 y primer frame MPEG para detectar frames de metadatos (Xing/Info/VBRI)
            //    y el Encoder Delay de LAME/Lavc. JLayer 1.0.1 no descarta el frame 0 ("Info"/"Xing") ni el delay
            //    del filtro de síntesis MPEG Layer 3 (528 muestras) + LAME encoder delay (576 muestras), lo que
            //    introduce ~65-77ms de silencio artificial al inicio respecto al motor BASS de osu!.
            int id3End = 0;
            if (rawBytes.length >= 10 && rawBytes[0] == 'I' && rawBytes[1] == 'D' && rawBytes[2] == '3') {
                int id3Size = ((rawBytes[6] & 0x7F) << 21)
                        | ((rawBytes[7] & 0x7F) << 14)
                        | ((rawBytes[8] & 0x7F) << 7)
                        | (rawBytes[9] & 0x7F);
                id3End = Math.min(rawBytes.length, 10 + id3Size);
            }

            int syncPos = id3End;
            while (syncPos + 4 < rawBytes.length
                    && !((rawBytes[syncPos] & 0xFF) == 0xFF && (rawBytes[syncPos + 1] & 0xE0) == 0xE0)) {
                syncPos++;
            }

            boolean hasVbrHeaderFrame = false;
            int encoderDelaySamples = 576;
            for (int i = syncPos; i < Math.min(rawBytes.length - 4, syncPos + 64); i++) {
                if ((rawBytes[i] == 'X' && rawBytes[i + 1] == 'i' && rawBytes[i + 2] == 'n' && rawBytes[i + 3] == 'g')
                        || (rawBytes[i] == 'I' && rawBytes[i + 1] == 'n' && rawBytes[i + 2] == 'f' && rawBytes[i + 3] == 'o')
                        || (rawBytes[i] == 'V' && rawBytes[i + 1] == 'B' && rawBytes[i + 2] == 'R' && rawBytes[i + 3] == 'I')) {
                    hasVbrHeaderFrame = true;
                    if (i + 143 < rawBytes.length) {
                        int b0 = rawBytes[i + 141] & 0xFF;
                        int b1 = rawBytes[i + 142] & 0xFF;
                        int parsedDelay = (b0 << 4) | (b1 >> 4);
                        if (parsedDelay > 0 && parsedDelay <= 2880) {
                            encoderDelaySamples = parsedDelay;
                        }
                    }
                    break;
                }
            }

            // Detectar si existe un segundo frame LAME silencioso por doble recodificación (ej. LAME -> Lavf/FFmpeg)
            boolean hasSecondLamePaddingFrame = false;
            if (hasVbrHeaderFrame && syncPos + 2000 <= rawBytes.length) {
                String headWindow = new String(rawBytes, syncPos, 2000, java.nio.charset.StandardCharsets.ISO_8859_1);
                if (headWindow.indexOf("LAME", 250) != -1) {
                    hasSecondLamePaddingFrame = true;
                }
            }

            bitstream = new Bitstream(new ByteArrayInputStream(rawBytes));
            Decoder decoder = new Decoder();

            int channels = 0;
            int sampleRate = 0;
            short[] accum = new short[44100 * 2 * 60];
            int totalShorts = 0;
            int frameIndex = 0;

            while (true) {
                Header header;
                try {
                    header = bitstream.readFrame();
                } catch (Exception frameEx) {
                    try {
                        bitstream.closeFrame();
                        continue;
                    } catch (Exception ignored) {
                        break;
                    }
                }
                if (header == null) {
                    break;
                }

                try {
                    SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    if (output != null) {
                        if (channels == 0) {
                            channels = output.getChannelCount();
                            sampleRate = output.getSampleFrequency();
                        }
                        int len = output.getBufferLength();
                        boolean skipFrame = false;
                        if (frameIndex == 0 && hasVbrHeaderFrame) {
                            // Descartar frame 0 de metadatos Xing / Info / VBRI (1152 muestras de silencio)
                            skipFrame = true;
                        } else if (frameIndex == 1 && hasSecondLamePaddingFrame) {
                            // Verificar que el segundo frame LAME sea efectivamente silencio puro antes de descartarlo
                            short[] buf = output.getBuffer();
                            boolean allSilent = true;
                            for (int s = 0; s < len; s++) {
                                if (Math.abs((int) buf[s]) > 4) {
                                    allSilent = false;
                                    break;
                                }
                            }
                            if (allSilent) {
                                skipFrame = true;
                            }
                        }

                        if (!skipFrame && len > 0) {
                            if (totalShorts + len > accum.length) {
                                int newCap = Math.max(accum.length * 2, totalShorts + len + 44100 * 2 * 30);
                                accum = Arrays.copyOf(accum, newCap);
                            }
                            System.arraycopy(output.getBuffer(), 0, accum, totalShorts, len);
                            totalShorts += len;
                        }
                    }
                } catch (Exception decodeEx) {
                    // Ignorar frame corrupto individual (ej. metadatos o carátula al final del archivo)
                } finally {
                    bitstream.closeFrame();
                }
                frameIndex++;
            }

            if (totalShorts == 0 || channels <= 0 || sampleRate <= 0) {
                return null;
            }

            int validChannels = Math.min(2, Math.max(1, channels));
            // Recortar el retardo del filtro de síntesis MPEG Layer 3 (528 muestras) + encoder delay de LAME
            // (+ 576 muestras del offset universal de pipeline MP3 de osu! cuando no fue absorbido por un 2do frame LAME)
            int trimSamples = encoderDelaySamples + 528 + (hasSecondLamePaddingFrame ? 0 : 576);
            int trimShorts = Math.min(totalShorts / 4, trimSamples * validChannels);
            // Asegurar alineación por par de canales (stereo frame alignment)
            trimShorts -= (trimShorts % validChannels);

            int remainingShorts = totalShorts - trimShorts;
            ShortBuffer pcm = MemoryUtil.memAllocShort(remainingShorts);
            pcm.put(accum, trimShorts, remainingShorts).flip();
            int totalSamples = remainingShorts / validChannels;
            return new DecodedAudio(pcm, validChannels, sampleRate, totalSamples, false);
        } catch (Exception e) {
            System.err.println("[VSRCraft] Error decodificando MP3 (" + resourcePath + "): " + e.getMessage());
            return null;
        } finally {
            if (bitstream != null) {
                try {
                    bitstream.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static DecodedAudio decodeWavBytes(byte[] rawBytes, String resourcePath) {
        try (AudioInputStream rawStream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(rawBytes))) {
            AudioFormat baseFormat = rawStream.getFormat();
            int channels = Math.min(2, Math.max(1, baseFormat.getChannels()));
            float sampleRate = baseFormat.getSampleRate() > 0 ? baseFormat.getSampleRate() : 44100f;

            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    16,
                    channels,
                    channels * 2,
                    sampleRate,
                    false
            );

            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(targetFormat, rawStream)) {
                byte[] pcmBytes = pcmStream.readAllBytes();
                int totalShorts = pcmBytes.length / 2;
                if (totalShorts == 0) return null;

                ShortBuffer pcm = MemoryUtil.memAllocShort(totalShorts);
                ShortBuffer view = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                pcm.put(view).flip();
                int totalSamples = totalShorts / channels;
                return new DecodedAudio(pcm, channels, Math.round(sampleRate), totalSamples, false);
            }
        } catch (Exception e) {
            System.err.println("[VSRCraft] Error decodificando WAV (" + resourcePath + "): " + e.getMessage());
            return null;
        }
    }

    private static String resolveResourcePath(SoundEvent sound) {
        if (sound == null) return "/assets/vsrcraft/sounds/music/whatsup.ogg";
        String path = sound.location().getPath();
        if (path.startsWith("music.")) {
            String trackName = path.substring("music.".length());
            String oggPath = "/assets/vsrcraft/sounds/music/" + trackName + ".ogg";
            if (AudioManager.class.getResource(oggPath) != null) {
                return oggPath;
            }
            String mp3Path = "/assets/vsrcraft/sounds/music/" + trackName + ".mp3";
            if (AudioManager.class.getResource(mp3Path) != null) {
                return mp3Path;
            }
            return oggPath;
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
