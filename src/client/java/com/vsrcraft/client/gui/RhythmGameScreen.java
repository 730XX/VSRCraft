package com.vsrcraft.client.gui;

import com.vsrcraft.client.audio.AudioManager;
import com.vsrcraft.client.config.KeybindConfig;
import com.vsrcraft.client.config.RhythmConfig;
import com.vsrcraft.client.domain.Beatmap;
import com.vsrcraft.client.domain.Judgement;
import com.vsrcraft.client.engine.RhythmEngine;
import com.vsrcraft.client.gui.renderer.JudgementHudRenderer;
import com.vsrcraft.client.gui.renderer.LaneRenderer;
import com.vsrcraft.client.gui.renderer.NoteRenderer;
import com.vsrcraft.client.parser.OsuBeatmapParser;
import com.vsrcraft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;

public class RhythmGameScreen extends Screen {
    private final int keyCount;
    private final int[] laneKeys;
    private final boolean[] lanePressed;
    private final RhythmEngine engine;
    private final long leadInMs;

    // Sincronización con Reloj Maestro de Audio (OpenAL)
    private static final long MIN_LEAD_IN_MS = 2000L;
    private boolean sessionInitialized = false;
    private long startNanoTime = -1;
    private boolean musicStarted = false;

    public RhythmGameScreen() {
        this(loadWhatsUpBeatmap());
    }

    public RhythmGameScreen(Beatmap beatmap) {
        super(Component.literal("VSRCraft - " + beatmap.getTitle()));
        this.keyCount = beatmap.getKeyCount();
        this.laneKeys = KeybindConfig.getKeysForMode(keyCount);
        this.lanePressed = new boolean[keyCount];
        this.engine = new RhythmEngine(beatmap);
        this.leadInMs = Math.max(MIN_LEAD_IN_MS, beatmap.getAudioLeadInMs());
    }

    private static Beatmap loadWhatsUpBeatmap() {
        try {
            InputStream stream = RhythmGameScreen.class.getResourceAsStream("/assets/vsrcraft/beatmaps/whatsup.osu");
            if (stream != null) {
                return OsuBeatmapParser.parse(stream);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Beatmap("What's up, people?!", "MAXIMUM THE HORMONE", 4);
    }

    @Override
    protected void init() {
        super.init();
        // Evitar que redimensionar la ventana de Minecraft reinicie el reloj o desincronice el audio
        if (!this.sessionInitialized) {
            this.sessionInitialized = true;
            this.startNanoTime = System.nanoTime();
            this.musicStarted = false;
            this.engine.start(0);
            // Precargar el archivo .ogg en memoria de OpenAL durante la cuenta regresiva (Lead-In)
            AudioManager.preloadMusic(ModSounds.MUSIC_WHATSUP);
        }
    }

    private double getCurrentSongTimeMs() {
        if (!musicStarted) {
            if (startNanoTime < 0) return -leadInMs;
            double elapsedMs = (System.nanoTime() - startNanoTime) / 1_000_000.0;
            double leadInCurrent = elapsedMs - leadInMs;
            // Si llegó a 0 pero aún está terminando de subir el buffer a OpenAL, esperar en 0.0ms
            if (leadInCurrent >= 0.0 && !AudioManager.ensureMusicReady()) {
                return -1.0;
            }
            return leadInCurrent;
        }
        // Una vez iniciada la música, el Reloj Maestro de OpenAL manda sobre las notas
        return AudioManager.getMusicTimeMs() + RhythmConfig.globalAudioOffsetMs;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Verificar si la precarga asíncrona terminó para crear el buffer OpenAL antes del segundo 0
        if (!musicStarted) {
            AudioManager.ensureMusicReady();
        }

        double currentSongTimeMs = getCurrentSongTimeMs();

        // Arrancar la música con latencia cero al cruzar el milisegundo 0
        if (!musicStarted && currentSongTimeMs >= 0.0) {
            AudioManager.startPreloadedMusic();
            musicStarted = true;
            currentSongTimeMs = AudioManager.getMusicTimeMs() + RhythmConfig.globalAudioOffsetMs;
        }

        long currentSongTimeLong = Math.round(currentSongTimeMs);
        engine.update(currentSongTimeLong);

        int boardWidth = keyCount * RhythmConfig.LANE_WIDTH;
        int boardStartX = (this.width - boardWidth) / 2;
        int hitLineY = this.height - RhythmConfig.HIT_LINE_OFFSET_BOTTOM;

        // 1. Render de pista y carriles
        LaneRenderer.renderLanes(guiGraphics, this.font, boardStartX, boardWidth, hitLineY, this.height, keyCount, lanePressed);

        // 2. Render de notas cayendo con precisión sub-milisegundo
        NoteRenderer.renderNotes(guiGraphics, engine.getBeatmap().getNotes(), currentSongTimeMs, boardStartX, hitLineY, this.height, keyCount);

        // 3. Render de información (Score, Combo, Juicio)
        JudgementHudRenderer.renderHud(guiGraphics, this.font, engine.getScoreTracker(), engine.getLastJudgement(), boardStartX, hitLineY, this.width, this.height);

        // Metadata de la canción y estado
        String status = currentSongTimeLong < 0
                ? "PREPARATE (" + (-currentSongTimeLong / 1000 + 1) + "s)"
                : (currentSongTimeLong / 1000) + "s / " + currentSongTimeLong + "ms";
        String songInfo = engine.getBeatmap().getTitle() + " [" + engine.getBeatmap().getNotes().size() + " Notes] - " + status;
        guiGraphics.drawString(this.font, songInfo, (this.width - this.font.width(songInfo)) / 2, 8, 0xFFE0E0E0, true);

        // Mensaje de ayuda y calibración de offset global
        String offsetStr = (RhythmConfig.globalAudioOffsetMs >= 0 ? "+" : "") + RhythmConfig.globalAudioOffsetMs + "ms";
        String help = "Teclas: W, D, L, P | Offset Audio: " + offsetStr + " (Flechas Izq/Der) | ESC para salir";
        guiGraphics.drawString(this.font, help, (this.width - this.font.width(help)) / 2, this.height - 20, 0xFF777777, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Ajuste de offset global en vivo estilo osu! (Flecha Izquierda / Derecha o - / +)
        if (event.key() == GLFW.GLFW_KEY_LEFT || event.key() == GLFW.GLFW_KEY_MINUS) {
            RhythmConfig.globalAudioOffsetMs -= 5L;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_RIGHT || event.key() == GLFW.GLFW_KEY_EQUAL) {
            RhythmConfig.globalAudioOffsetMs += 5L;
            return true;
        }

        long currentSongTime = Math.round(getCurrentSongTimeMs());

        for (int lane = 0; lane < keyCount; lane++) {
            if (event.key() == laneKeys[lane]) {
                // Evitar key-repeat del sistema operativo si la tecla ya estaba mantenida
                if (lanePressed[lane]) {
                    return true;
                }
                lanePressed[lane] = true;
                Judgement judgement = engine.onLanePressed(lane, currentSongTime);
                if (judgement != null && judgement != Judgement.MISS) {
                    AudioManager.playHitSound();
                }
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        for (int lane = 0; lane < keyCount; lane++) {
            if (event.key() == laneKeys[lane]) {
                lanePressed[lane] = false;
                return true;
            }
        }
        return super.keyReleased(event);
    }

    @Override
    public void removed() {
        super.removed();
        AudioManager.stopMusic();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
