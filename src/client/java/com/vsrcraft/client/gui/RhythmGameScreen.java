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

import java.io.InputStream;

public class RhythmGameScreen extends Screen {
    private final int keyCount;
    private final int[] laneKeys;
    private final boolean[] lanePressed;
    private final RhythmEngine engine;

    // Sincronización
    private static final long LEAD_IN_MS = 2000L;
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
        this.startNanoTime = System.nanoTime();
        this.musicStarted = false;
        engine.start(0);
    }

    private long getCurrentSongTimeMs() {
        if (startNanoTime < 0) return -LEAD_IN_MS;
        long elapsedMs = (System.nanoTime() - startNanoTime) / 1_000_000L;
        return elapsedMs - LEAD_IN_MS;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        long currentSongTime = getCurrentSongTimeMs();

        // Arrancar la música de What's up, people?! al llegar al segundo cero
        if (!musicStarted && currentSongTime >= 0) {
            AudioManager.startMusic(ModSounds.MUSIC_WHATSUP);
            musicStarted = true;
        }

        engine.update(currentSongTime);

        int boardWidth = keyCount * RhythmConfig.LANE_WIDTH;
        int boardStartX = (this.width - boardWidth) / 2;
        int hitLineY = this.height - RhythmConfig.HIT_LINE_OFFSET_BOTTOM;

        // 1. Render de pista y carriles
        LaneRenderer.renderLanes(guiGraphics, this.font, boardStartX, boardWidth, hitLineY, this.height, keyCount, lanePressed);

        // 2. Render de notas cayendo
        NoteRenderer.renderNotes(guiGraphics, engine.getBeatmap().getNotes(), currentSongTime, boardStartX, hitLineY, this.height, keyCount);

        // 3. Render de información (Score, Combo, Juicio)
        JudgementHudRenderer.renderHud(guiGraphics, this.font, engine.getScoreTracker(), engine.getLastJudgement(), boardStartX, hitLineY, this.width, this.height);

        // Metadata de la canción y estado
        String status = currentSongTime < 0 ? "PREPARATE (" + (-currentSongTime / 1000 + 1) + "s)" : (currentSongTime / 1000) + "s / " + currentSongTime + "ms";
        String songInfo = engine.getBeatmap().getTitle() + " [" + engine.getBeatmap().getNotes().size() + " Notes] - " + status;
        guiGraphics.drawString(this.font, songInfo, (this.width - this.font.width(songInfo)) / 2, 8, 0xFFE0E0E0, true);

        // Mensaje de ayuda
        String help = "Teclas: D, F, J, K | Presiona ESC para salir";
        guiGraphics.drawString(this.font, help, (this.width - this.font.width(help)) / 2, this.height - 20, 0xFF777777, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        long currentSongTime = getCurrentSongTimeMs();

        for (int lane = 0; lane < keyCount; lane++) {
            if (event.key() == laneKeys[lane]) {
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
