package com.vsrcraft.client.gui;

import com.vsrcraft.client.audio.AudioManager;
import com.vsrcraft.client.config.KeybindConfig;
import com.vsrcraft.client.config.RhythmConfig;
import com.vsrcraft.client.config.VSRCraftSettings;
import com.vsrcraft.client.domain.Beatmap;
import com.vsrcraft.client.domain.BeatmapRepository;
import com.vsrcraft.client.domain.Judgement;
import com.vsrcraft.client.domain.ReplayData;
import com.vsrcraft.client.domain.ReplayRepository;
import com.vsrcraft.client.domain.ScoreTracker;
import com.vsrcraft.client.engine.RhythmEngine;
import com.vsrcraft.client.gui.renderer.HitEffectRenderer;
import com.vsrcraft.client.gui.renderer.JudgementHudRenderer;
import com.vsrcraft.client.gui.renderer.KeyOverlayRenderer;
import com.vsrcraft.client.gui.renderer.LaneRenderer;
import com.vsrcraft.client.gui.renderer.NoteRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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

    // Barra de Vida / HP y Resultados
    private float smoothHp = 100.0f;
    private boolean failTriggered = false;
    private boolean clearTriggered = false;
    private boolean transitioningToResults = false;
    private final ScreenTransition transition = new ScreenTransition(280L);
    private final HitEffectRenderer hitEffects = new HitEffectRenderer();
    private final KeyOverlayRenderer keyOverlay;

    // Grabación y Reproducción de Replays
    private final ReplayData activeReplay;
    private final float playbackRate;
    private int replayCursor = 0;
    private final List<ReplayData.ReplayEvent> recordedEvents = new ArrayList<>();

    // Botones interactivos en pantalla de fallo
    private Button retryButton;
    private Button exitButton;

    public RhythmGameScreen() {
        this(getDefaultBeatmap(), null, 1.0f);
    }

    public RhythmGameScreen(Beatmap beatmap) {
        this(beatmap, null, 1.0f);
    }

    public RhythmGameScreen(Beatmap beatmap, float playbackRate) {
        this(beatmap, null, playbackRate);
    }

    public RhythmGameScreen(Beatmap beatmap, ReplayData replayData) {
        this(beatmap, replayData, replayData != null ? replayData.getPlaybackRate() : 1.0f);
    }

    public RhythmGameScreen(Beatmap beatmap, ReplayData replayData, float playbackRate) {
        super(Component.literal("VSRCraft - " + beatmap.getTitle()));
        this.keyCount = beatmap.getKeyCount();
        this.laneKeys = KeybindConfig.getKeysForMode(keyCount);
        this.lanePressed = new boolean[keyCount];
        this.engine = new RhythmEngine(beatmap);
        this.keyOverlay = new KeyOverlayRenderer(keyCount);
        this.leadInMs = Math.max(MIN_LEAD_IN_MS, beatmap.getAudioLeadInMs());
        this.activeReplay = replayData;
        this.playbackRate = Math.max(0.5f, Math.min(2.0f, playbackRate));
    }

    private boolean isReplayMode() {
        return this.activeReplay != null;
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

        // Actualizar teclas por si fueron reasignadas
        this.laneKeys = KeybindConfig.getKeysForMode(keyCount);

        if (!this.sessionInitialized) {
            startSession();
        } else if (this.isPaused) {
            // Reanudando desde la pantalla de ajustes
            this.isPaused = false;
            long pausedDuration = System.nanoTime() - this.pausedNanoTime;
            this.startNanoTime += pausedDuration;
            if (this.musicStarted && !engine.isFailed()) {
                AudioManager.resumeMusic();
            }
        }

        // Botón de la tuerquita (⚙) arriba a la derecha
        int gearButtonSize = 26;
        int gearX = this.width - gearButtonSize - 12;
        int gearY = 10;

        Button settingsButton = Button.builder(
                Component.literal("⚙"),
                btn -> openSettingsScreen()
        )
        .bounds(gearX, gearY, gearButtonSize, gearButtonSize)
        .tooltip(Tooltip.create(Component.literal("Ajustes y Personalización de VSRCraft")))
        .build();

        this.addRenderableWidget(settingsButton);

        // Botones para la pantalla de Fallo (inician ocultos/inactivos)
        int btnW = 120;
        int btnH = 22;
        int centerY = this.height / 2 + 38;

        this.retryButton = Button.builder(Component.literal("§a↻ Reintentar (R)"), btn -> restartSession())
                .bounds(this.width / 2 - btnW - 6, centerY, btnW, btnH).build();
        this.exitButton = Button.builder(Component.literal("§c< Salir (ESC)"), btn -> this.onClose())
                .bounds(this.width / 2 + 6, centerY, btnW, btnH).build();

        this.retryButton.visible = false;
        this.exitButton.visible = false;
        this.addRenderableWidget(this.retryButton);
        this.addRenderableWidget(this.exitButton);
    }

    private void startSession() {
        this.sessionInitialized = true;
        this.startNanoTime = System.nanoTime();
        this.musicStarted = false;
        this.smoothHp = 100.0f;
        this.failTriggered = false;
        this.clearTriggered = false;
        this.transitioningToResults = false;
        this.hitEffects.clear();
        this.keyOverlay.reset();
        this.replayCursor = 0;
        this.recordedEvents.clear();
        Arrays.fill(this.lanePressed, false);
        this.engine.start(0);
        AudioManager.setMusicPlaybackRate(this.playbackRate);
        AudioManager.preloadMusicFromResource(this.engine.getBeatmap().getAudioResourcePath());
    }

    private void restartSession() {
        AudioManager.stopMusic();
        if (this.retryButton != null) this.retryButton.visible = false;
        if (this.exitButton != null) this.exitButton.visible = false;
        startSession();
    }

    private void openSettingsScreen() {
        if (engine.isFailed()) return;
        this.isPaused = true;
        this.pausedNanoTime = System.nanoTime();
        if (this.musicStarted) {
            AudioManager.pauseMusic();
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(new VSRSettingsScreen(this));
        }
    }

    private boolean isLeadInComplete() {
        if (musicStarted) return true;
        if (startNanoTime < 0) return false;
        long now = isPaused ? pausedNanoTime : System.nanoTime();
        double elapsedMs = ((now - startNanoTime) / 1_000_000.0) * this.playbackRate;
        return (elapsedMs - leadInMs) >= 0.0 && AudioManager.ensureMusicReady();
    }

    private double getCurrentSongTimeMs() {
        if (!musicStarted) {
            if (startNanoTime < 0) return -leadInMs + VSRCraftSettings.get().globalAudioOffsetMs;
            long now = isPaused ? pausedNanoTime : System.nanoTime();
            double elapsedMs = ((now - startNanoTime) / 1_000_000.0) * this.playbackRate;
            double leadInCurrent = elapsedMs - leadInMs;
            if (leadInCurrent >= 0.0 && !AudioManager.ensureMusicReady()) {
                return -1.0 + VSRCraftSettings.get().globalAudioOffsetMs;
            }
            return leadInCurrent + VSRCraftSettings.get().globalAudioOffsetMs;
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

        if (!musicStarted && !isPaused && !engine.isFailed()) {
            AudioManager.ensureMusicReady();
        }

        // Arrancar la música con latencia cero al cruzar el ms 0 del lead-in
        if (!musicStarted && !isPaused && !engine.isFailed() && isLeadInComplete()) {
            AudioManager.startPreloadedMusic();
            musicStarted = true;
        }

        double currentSongTimeMs = getCurrentSongTimeMs();

        long currentSongTimeLong = Math.round(currentSongTimeMs);
        if (!isPaused && !engine.isFailed()) {
            if (isReplayMode() && !this.clearTriggered) {
                dispatchReplayEvents(currentSongTimeLong);
            }
            engine.update(currentSongTimeLong);
        }

        int boardWidth = keyCount * RhythmConfig.getLaneWidth(keyCount);
        int boardStartX = (this.width - boardWidth) / 2;
        int hitLineY = this.height - RhythmConfig.getHitLineOffsetBottom();

        // 1. Render de pista y carriles (aplica opacidad y opción de hit glow)
        LaneRenderer.renderLanes(guiGraphics, this.font, boardStartX, boardWidth, hitLineY, this.height, keyCount, lanePressed);

        // 2. Render de notas cayendo (aplica velocidad, grosor y hit position configurados)
        NoteRenderer.renderNotes(guiGraphics, engine.getBeatmap().getNotes(), currentSongTimeMs, boardStartX, hitLineY, this.height, keyCount);

        // 2b. Efectos de golpe "Voxel Sparks" (chispas cúbicas debajo de la línea de impacto)
        if (!isPaused && !engine.isFailed()) {
            for (RhythmEngine.HitEvent ev : engine.drainVisualEvents()) {
                this.hitEffects.spawnHitEffect(ev.lane(), keyCount, ev.judgement(), boardStartX, hitLineY);
            }
            for (int lane = 0; lane < keyCount; lane++) {
                if (engine.isLaneHolding(lane)) {
                    this.hitEffects.spawnHoldTick(lane, keyCount, boardStartX, hitLineY);
                }
            }
        }
        this.hitEffects.render(guiGraphics, boardStartX, hitLineY, this.height, keyCount);

        // 3. Render de información (Score a la izquierda, Combo y Juicio al medio)
        JudgementHudRenderer.renderHud(guiGraphics, this.font, engine.getScoreTracker(), engine.getLastJudgement(), boardStartX, hitLineY, this.width, this.height);

        // 4. Render de la Barra de Vida Lateral (HP Bar)
        renderHpBar(guiGraphics, boardStartX + boardWidth + 8, hitLineY);

        // 4b. Visualizador de teclas y KPS (Key Overlay / JKPS) abajo a la derecha
        this.keyOverlay.render(guiGraphics, this.font, this.laneKeys, this.lanePressed, this.width, this.height);

        // Metadata de la canción y estado arriba a la izquierda
        String status = currentSongTimeLong < 0
                ? "PREPARATE (" + (-currentSongTimeLong / 1000 + 1) + "s)"
                : (currentSongTimeLong / 1000) + "s / " + (engine.getBeatmap().getDurationMs() / 1000) + "s";
        String songInfo = engine.getBeatmap().getTitle() + " [" + engine.getBeatmap().getVersion() + "] - " + status;
        guiGraphics.drawString(this.font, songInfo, 12, 10, 0xFFE0E0E0, true);
        if (isReplayMode()) {
            String replayLabel = "§e§l▶ MODO REPLAY";
            if (this.activeReplay.playerName != null && !this.activeReplay.playerName.isEmpty()) {
                replayLabel += " §f// " + this.activeReplay.playerName;
            }
            if (this.activeReplay.hasSpeedMod()) {
                replayLabel += String.format(" §b[%s %.2fx]", this.activeReplay.getSpeedModLabel(), this.playbackRate);
            } else if (Math.abs(this.playbackRate - 1.0f) > 0.001f) {
                replayLabel += String.format(" §b[%.2fx]", this.playbackRate);
            }
            guiGraphics.drawCenteredString(this.font, replayLabel, this.width / 2, 10, 0xFFFFD54F);
        } else if (Math.abs(this.playbackRate - 1.0f) > 0.001f) {
            guiGraphics.drawCenteredString(this.font, String.format("§b§lRATE: %.2fx", this.playbackRate), this.width / 2, 10, 0xFF55E5FF);
        }

        // Mensaje de ayuda abajo a la izquierda
        String offsetStr = (VSRCraftSettings.get().globalAudioOffsetMs >= 0 ? "+" : "") + VSRCraftSettings.get().globalAudioOffsetMs + "ms";
        String help = String.format("ESC: Volver a Canciones | Scroll: %.2fx | Offset: %s | HitPos: %dpx",
                VSRCraftSettings.get().scrollSpeed, offsetStr, RhythmConfig.getHitLineOffsetBottom());
        guiGraphics.drawString(this.font, help, 12, this.height - 18, 0xFF777777, false);

        // 5. Manejo y Overlay de FALLO (STAGE FAILED)
        if (engine.isFailed()) {
            if (!this.failTriggered) {
                this.failTriggered = true;
                AudioManager.stopMusic();
                AudioManager.playHitSound(); // Alerta sonora de rotura
                if (this.retryButton != null) this.retryButton.visible = true;
                if (this.exitButton != null) this.exitButton.visible = true;
            }
            renderFailedOverlay(guiGraphics);
        } else if (!this.isPaused && !this.clearTriggered && engine.isCleared(currentSongTimeLong)) {
            // 6. Canción completada con éxito -> Transición a Pantalla de Resultados (STAGE CLEARED!)
            this.clearTriggered = true;
            this.transitioningToResults = true;

            ScoreTracker tracker = this.engine.getScoreTracker();
            VSRCraftSettings.BeatmapRecord prevRecord = VSRCraftSettings.get().getPersonalBest(this.engine.getBeatmap().getId());
            int prevBestScore = (prevRecord != null) ? prevRecord.bestScore : 0;
            boolean isNewRecord = !isReplayMode() && tracker.getScore() > prevBestScore && tracker.getScore() > 0;

            ReplayData sessionReplay = isReplayMode()
                    ? this.activeReplay
                    : new ReplayData(
                            this.engine.getBeatmap().getId(),
                            tracker.getScore(),
                            tracker.getAccuracy(),
                            tracker.getMaxCombo(),
                            tracker.getRank(),
                            this.recordedEvents
                    );
            if (!isReplayMode()) {
                sessionReplay.playbackRate = this.playbackRate;
            }

            if (isNewRecord) {
                ReplayRepository.saveBestReplay(sessionReplay);
            }
            saveSessionRecordIfValid();
            AudioManager.fadeOutMusic(320L);
            this.transition.startExit(340L, () -> {
                AudioManager.stopMusic();
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new ResultsScreen(this.engine.getBeatmap(), tracker, isNewRecord, prevBestScore, sessionReplay));
                }
            });
        }

        if (this.transition.isExiting()) {
            AudioManager.updatePreview();
        }
        this.transition.renderOverlay(guiGraphics, this.width, this.height);
    }

    private void renderHpBar(GuiGraphics g, int barX, int hitLineY) {
        int barTop = 32;
        int barH = Math.max(40, hitLineY - barTop);
        int barW = 8;

        // Interpolación suave del valor de HP
        this.smoothHp += (this.engine.getHp() - this.smoothHp) * 0.18f;
        if (Math.abs(this.engine.getHp() - this.smoothHp) < 0.1f) {
            this.smoothHp = this.engine.getHp();
        }

        // Fondo y borde de la barra
        g.fill(barX, barTop, barX + barW, barTop + barH, 0xD00E121A);
        g.fill(barX, barTop, barX + barW, barTop + 1, 0xFF2A374A);
        g.fill(barX, barTop + barH - 1, barX + barW, barTop + barH, 0xFF2A374A);
        g.fill(barX, barTop, barX + 1, barTop + barH, 0xFF2A374A);
        g.fill(barX + barW - 1, barTop, barX + barW, barTop + barH, 0xFF2A374A);

        // Relleno de vida (de abajo hacia arriba)
        int fillH = Math.max(0, Math.min(barH - 2, Math.round(((this.smoothHp / 100.0f) * (barH - 2)))));
        int fillY = barTop + barH - 1 - fillH;

        int hpColor;
        if (this.smoothHp > 50.0f) {
            hpColor = 0xFF00E5FF; // Cian brillante
        } else if (this.smoothHp > 20.0f) {
            hpColor = 0xFFFFCA28; // Amarillo/Oro
        } else {
            // Parpadeo de peligro si la vida es muy baja
            boolean blink = (System.currentTimeMillis() / 150) % 2 == 0;
            hpColor = blink ? 0xFFFF1744 : 0xFFB71C1C;
        }

        if (fillH > 0) {
            g.fill(barX + 1, fillY, barX + barW - 1, barTop + barH - 1, hpColor);
            // Destello superior en el tope de la barra de vida
            g.fill(barX + 1, fillY, barX + barW - 1, fillY + 1, 0xFFFFFFFF);
        }

        // Etiqueta "HP" arriba de la barra
        g.drawString(this.font, "HP", barX, barTop - 10, hpColor, false);
    }

    private void renderFailedOverlay(GuiGraphics g) {
        // Tinte rojo sangre semi-transparente sobre la pantalla
        g.fill(0, 0, this.width, this.height, 0xD9200508);

        int centerX = this.width / 2;
        int centerY = this.height / 2 - 20;

        // Letras gigantes de fallo
        g.drawCenteredString(this.font, "§c§l✖ STAGE FAILED ✖", centerX, centerY - 32, 0xFFFF3333);
        g.drawCenteredString(this.font, "§7Te has quedado sin vida (HP 0%)", centerX, centerY - 16, 0xFFAAAAAA);

        ScoreTracker tracker = engine.getScoreTracker();
        String statsLine = String.format(Locale.ROOT, "Score: %,d   |   Acc: %.2f%%   |   Max Combo: %dx",
                tracker.getScore(), tracker.getAccuracy(), tracker.getMaxCombo());
        g.drawCenteredString(this.font, statsLine, centerX, centerY + 6, 0xFFE0E0E0);
    }

    private void dispatchReplayEvents(long currentSongTimeLong) {
        if (this.activeReplay == null || this.activeReplay.events == null) return;
        List<ReplayData.ReplayEvent> events = this.activeReplay.events;
        while (this.replayCursor < events.size()) {
            ReplayData.ReplayEvent ev = events.get(this.replayCursor);
            if (ev.timeMs > currentSongTimeLong) {
                break;
            }
            this.replayCursor++;
            if (ev.lane < 0 || ev.lane >= this.keyCount) continue;

            this.lanePressed[ev.lane] = ev.pressed;
            if (ev.pressed) {
                this.keyOverlay.onLanePressed(ev.lane);
                Judgement judgement = this.engine.onLanePressed(ev.lane, ev.timeMs);
                if (judgement != null && judgement != Judgement.MISS) {
                    AudioManager.playHitSound();
                }
            } else {
                Judgement releaseJudgement = this.engine.onLaneReleased(ev.lane, ev.timeMs);
                if (releaseJudgement != null && releaseJudgement != Judgement.MISS) {
                    AudioManager.playHitSound();
                }
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (engine.isFailed()) {
            if (event.key() == GLFW.GLFW_KEY_R) {
                restartSession();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                this.onClose();
                return true;
            }
            return true;
        }

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

        if (isReplayMode()) {
            return super.keyPressed(event);
        }

        long currentSongTime = Math.round(getCurrentSongTimeMs());

        for (int lane = 0; lane < keyCount; lane++) {
            if (event.key() == laneKeys[lane]) {
                if (lanePressed[lane]) {
                    return true;
                }
                lanePressed[lane] = true;
                this.keyOverlay.onLanePressed(lane);
                this.recordedEvents.add(new ReplayData.ReplayEvent(currentSongTime, lane, true));
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
        if (engine.isFailed() || isReplayMode()) return true;

        long currentSongTime = Math.round(getCurrentSongTimeMs());
        for (int lane = 0; lane < keyCount; lane++) {
            if (event.key() == laneKeys[lane]) {
                lanePressed[lane] = false;
                this.recordedEvents.add(new ReplayData.ReplayEvent(currentSongTime, lane, false));
                Judgement releaseJudgement = engine.onLaneReleased(lane, currentSongTime);
                if (releaseJudgement != null && releaseJudgement != Judgement.MISS) {
                    AudioManager.playHitSound();
                }
                return true;
            }
        }
        return super.keyReleased(event);
    }

    private void saveSessionRecordIfValid() {
        if (engine.isFailed() || isReplayMode()) return; // No registrar récord si el mapa fue fallado o es un Replay
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
        if (this.transition.isExiting()) return;
        saveSessionRecordIfValid();
        AudioManager.fadeOutMusic(200L);
        this.transition.startExit(220L, () -> {
            AudioManager.stopMusic();
            if (this.minecraft != null) {
                this.minecraft.setScreen(new SongSelectScreen(this.engine.getBeatmap()));
            }
        });
    }

    @Override
    public void removed() {
        super.removed();
        if (!this.isPaused && !this.transitioningToResults && !this.transition.isExiting()) {
            saveSessionRecordIfValid();
            AudioManager.stopMusic();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
