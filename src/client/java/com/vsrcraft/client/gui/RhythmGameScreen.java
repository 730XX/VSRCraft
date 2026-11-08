package com.vsrcraft.client.gui;

import com.vsrcraft.client.audio.AudioManager;
import com.vsrcraft.client.config.KeybindConfig;
import com.vsrcraft.client.config.RhythmConfig;
import com.vsrcraft.client.config.VSRCraftSettings;
import com.vsrcraft.client.domain.Beatmap;
import com.vsrcraft.client.domain.BeatmapRepository;
import com.vsrcraft.client.domain.Judgement;
import com.vsrcraft.client.domain.ScoreTracker;
import com.vsrcraft.client.engine.RhythmEngine;
import com.vsrcraft.client.gui.renderer.JudgementHudRenderer;
import com.vsrcraft.client.gui.renderer.LaneRenderer;
import com.vsrcraft.client.gui.renderer.NoteRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class RhythmGameScreen extends Screen {
    private final int keyCount;
    private int[] laneKeys;
    private final boolean[] lanePressed;
    private final RhythmEngine engine;
    private final long leadInMs;

    // Sincronización con Reloj Maestro de Audio (OpenAL)
    private static final long MIN_LEAD_IN_MS = 2000L;
    private boolean sessionInitialized = false;
    private long startNanoTime = -1;
    private boolean musicStarted = false;
    private boolean isPaused = false;
    private long pausedNanoTime = 0L;

    public RhythmGameScreen() {
        this(getDefaultBeatmap());
    }

    public RhythmGameScreen(Beatmap beatmap) {
        super(Component.literal("VSRCraft - " + beatmap.getTitle()));
        this.keyCount = beatmap.getKeyCount();
        this.laneKeys = KeybindConfig.getKeysForMode(keyCount);
        this.lanePressed = new boolean[keyCount];
        this.engine = new RhythmEngine(beatmap);
        this.leadInMs = Math.max(MIN_LEAD_IN_MS, beatmap.getAudioLeadInMs());
    }

    private static Beatmap getDefaultBeatmap() {
        List<Beatmap> maps = BeatmapRepository.getBeatmaps();
        if (!maps.isEmpty()) {
            return maps.get(0);
        }
        return new Beatmap("What's up, people?!", "MAXIMUM THE HORMONE", 4);
    }

    @Override
    protected void init() {
        super.init();

        // Actualizar teclas por si fueron reasignadas en la pantalla de opciones
        this.laneKeys = KeybindConfig.getKeysForMode(keyCount);

        if (!this.sessionInitialized) {
            this.sessionInitialized = true;
            this.startNanoTime = System.nanoTime();
            this.musicStarted = false;
            this.engine.start(0);
            AudioManager.preloadMusicFromResource(this.engine.getBeatmap().getAudioResourcePath());
        } else if (this.isPaused) {
            // Reanudando desde la pantalla de ajustes
            this.isPaused = false;
            long pausedDuration = System.nanoTime() - this.pausedNanoTime;
            this.startNanoTime += pausedDuration;
            if (this.musicStarted) {
                AudioManager.resumeMusic();
            }
        }

        // Botón de la tuerquita (⚙) abajo a la derecha
        int gearButtonSize = 24;
        int gearX = this.width - gearButtonSize - 12;
        int gearY = this.height - gearButtonSize - 12;

        Button settingsButton = Button.builder(
                Component.literal("⚙"),
                btn -> openSettingsScreen()
        )
        .bounds(gearX, gearY, gearButtonSize, gearButtonSize)
        .tooltip(Tooltip.create(Component.literal("Ajustes y Personalización de VSRCraft")))
        .build();

        this.addRenderableWidget(settingsButton);
    }

    private void openSettingsScreen() {
        this.isPaused = true;
        this.pausedNanoTime = System.nanoTime();
        if (this.musicStarted) {
            AudioManager.pauseMusic();
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(new VSRSettingsScreen(this));
        }
    }

    private double getCurrentSongTimeMs() {
        if (!musicStarted) {
            if (startNanoTime < 0) return -leadInMs;
            long now = isPaused ? pausedNanoTime : System.nanoTime();
            double elapsedMs = (now - startNanoTime) / 1_000_000.0;
            double leadInCurrent = elapsedMs - leadInMs;
            if (leadInCurrent >= 0.0 && !AudioManager.ensureMusicReady()) {
                return -1.0;
            }
            return leadInCurrent;
        }
        return AudioManager.getMusicTimeMs() + VSRCraftSettings.get().globalAudioOffsetMs;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Fondo oscurecido completo (Background Dim)
        float dim = VSRCraftSettings.get().backgroundDim;
        if (dim > 0.0f) {
            int alpha = Math.max(0, Math.min(255, Math.round(dim * 255)));
            guiGraphics.fill(0, 0, this.width, this.height, (alpha << 24) | 0x000000);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (!musicStarted && !isPaused) {
            AudioManager.ensureMusicReady();
        }

        double currentSongTimeMs = getCurrentSongTimeMs();

        // Arrancar la música con latencia cero al cruzar el ms 0
        if (!musicStarted && !isPaused && currentSongTimeMs >= 0.0) {
            AudioManager.startPreloadedMusic();
            musicStarted = true;
            currentSongTimeMs = AudioManager.getMusicTimeMs() + VSRCraftSettings.get().globalAudioOffsetMs;
        }

        long currentSongTimeLong = Math.round(currentSongTimeMs);
        if (!isPaused) {
            engine.update(currentSongTimeLong);
        }

        int boardWidth = keyCount * RhythmConfig.LANE_WIDTH;
        int boardStartX = (this.width - boardWidth) / 2;
        int hitLineY = this.height - RhythmConfig.getHitLineOffsetBottom();

        // 1. Render de pista y carriles (aplica opacidad y opción de hit glow)
        LaneRenderer.renderLanes(guiGraphics, this.font, boardStartX, boardWidth, hitLineY, this.height, keyCount, lanePressed);

        // 2. Render de notas cayendo (aplica velocidad, grosor y hit position configurados)
        NoteRenderer.renderNotes(guiGraphics, engine.getBeatmap().getNotes(), currentSongTimeMs, boardStartX, hitLineY, this.height, keyCount);

        // 3. Render de información (Score a la izquierda, Combo y Juicio al medio)
        JudgementHudRenderer.renderHud(guiGraphics, this.font, engine.getScoreTracker(), engine.getLastJudgement(), boardStartX, hitLineY, this.width, this.height);

        // Metadata de la canción y estado arriba a la izquierda
        String status = currentSongTimeLong < 0
                ? "PREPARATE (" + (-currentSongTimeLong / 1000 + 1) + "s)"
                : (currentSongTimeLong / 1000) + "s / " + (engine.getBeatmap().getDurationMs() / 1000) + "s";
        String songInfo = engine.getBeatmap().getTitle() + " [" + engine.getBeatmap().getVersion() + "] - " + status;
        guiGraphics.drawString(this.font, songInfo, 12, 10, 0xFFE0E0E0, true);

        // Mensaje de ayuda abajo a la izquierda
        String offsetStr = (VSRCraftSettings.get().globalAudioOffsetMs >= 0 ? "+" : "") + VSRCraftSettings.get().globalAudioOffsetMs + "ms";
        String help = String.format("ESC: Volver a Canciones | Scroll: %.2fx | Offset: %s | HitPos: %dpx",
                VSRCraftSettings.get().scrollSpeed, offsetStr, RhythmConfig.getHitLineOffsetBottom());
        guiGraphics.drawString(this.font, help, 12, this.height - 18, 0xFF777777, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_O) {
            openSettingsScreen();
            return true;
        }

        if (event.key() == GLFW.GLFW_KEY_F3) {
            VSRCraftSettings.get().scrollSpeed = Math.max(0.2f, VSRCraftSettings.get().scrollSpeed - 0.05f);
            VSRCraftSettings.save();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_F4) {
            VSRCraftSettings.get().scrollSpeed = Math.min(1.5f, VSRCraftSettings.get().scrollSpeed + 0.05f);
            VSRCraftSettings.save();
            return true;
        }

        if (event.key() == GLFW.GLFW_KEY_LEFT || event.key() == GLFW.GLFW_KEY_MINUS) {
            VSRCraftSettings.get().globalAudioOffsetMs -= 5L;
            VSRCraftSettings.save();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_RIGHT || event.key() == GLFW.GLFW_KEY_EQUAL) {
            VSRCraftSettings.get().globalAudioOffsetMs += 5L;
            VSRCraftSettings.save();
            return true;
        }

        long currentSongTime = Math.round(getCurrentSongTimeMs());

        for (int lane = 0; lane < keyCount; lane++) {
            if (event.key() == laneKeys[lane]) {
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

    private void saveSessionRecordIfValid() {
        ScoreTracker tracker = this.engine.getScoreTracker();
        if (tracker.getTotalJudged() > 0 && tracker.getScore() > 0) {
            VSRCraftSettings.get().updatePersonalBest(
                    this.engine.getBeatmap().getId(),
                    tracker.getScore(),
                    tracker.getAccuracy(),
                    tracker.getMaxCombo(),
                    tracker.getRank()
            );
        }
    }

    @Override
    public void onClose() {
        saveSessionRecordIfValid();
        AudioManager.stopMusic();
        if (this.minecraft != null) {
            this.minecraft.setScreen(new SongSelectScreen(this.engine.getBeatmap()));
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (!this.isPaused) {
            saveSessionRecordIfValid();
            AudioManager.stopMusic();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
