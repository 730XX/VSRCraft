package com.vsrcraft.client.gui;

import com.vsrcraft.client.audio.AudioManager;
import com.vsrcraft.client.domain.Beatmap;
import com.vsrcraft.client.domain.Judgement;
import com.vsrcraft.client.domain.ScoreTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pantalla de Resultados (STAGE CLEARED!) al completar un beatmap con éxito.
 * Muestra el rango obtenido, puntuación, precisión, combo máximo, insignia de nuevo récord
 * y desglose completo de juicios con barras animadas.
 */
public class ResultsScreen extends Screen {
    private final Beatmap beatmap;
    private final int score;
    private final float accuracy;
    private final int maxCombo;
    private final String rank;
    private final Map<Judgement, Integer> judgementCounts;
    private final int totalJudged;
    private final boolean isNewRecord;
    private final int previousBestScore;

    private final ScreenTransition transition = new ScreenTransition(320L);

    private int leftPanelX, leftPanelY, leftPanelW, leftPanelH;
    private int rightPanelX, rightPanelY, rightPanelW, rightPanelH;

    public ResultsScreen(Beatmap beatmap, ScoreTracker tracker, boolean isNewRecord, int previousBestScore) {
        super(Component.literal("VSRCraft - Resultados: " + beatmap.getTitle()));
        this.beatmap = beatmap;
        this.score = tracker.getScore();
        this.accuracy = tracker.getAccuracy();
        this.maxCombo = tracker.getMaxCombo();
        this.rank = tracker.getRank();
        this.judgementCounts = new EnumMap<>(Judgement.class);
        this.judgementCounts.putAll(tracker.getJudgementCounts());
        this.totalJudged = Math.max(1, tracker.getTotalJudged());
        this.isNewRecord = isNewRecord;
        this.previousBestScore = previousBestScore;
    }

    @Override
    protected void init() {
        super.init();

        int margin = 16;
        int headerH = 52;
        int footerH = 40;
        int contentY = headerH + 8;
        int contentH = Math.max(140, this.height - contentY - footerH);

        this.leftPanelW = Math.max(170, Math.min(240, (int) ((this.width - margin * 3) * 0.42f)));
        this.leftPanelX = margin;
        this.leftPanelY = contentY;
        this.leftPanelH = contentH;

        this.rightPanelX = this.leftPanelX + this.leftPanelW + margin;
        this.rightPanelY = contentY;
        this.rightPanelW = Math.max(180, this.width - this.rightPanelX - margin);
        this.rightPanelH = contentH;

        int btnW = 150;
        int btnH = 22;
        int btnY = this.height - 30;
        int gap = 12;

        this.addRenderableWidget(Button.builder(
                Component.literal("§a↻ Reintentar (R)"),
                btn -> retryMap()
        ).bounds(this.width / 2 - btnW - gap / 2, btnY, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("§b< Volver al Selector (ENTER)"),
                btn -> backToSelect()
        ).bounds(this.width / 2 + gap / 2, btnY, btnW, btnH).build());
    }

    private void retryMap() {
        if (this.transition.isExiting() || this.minecraft == null) return;
        AudioManager.fadeOutMusic(220L);
        this.transition.startExit(240L, () -> {
            AudioManager.stopMusic();
            this.minecraft.setScreen(new RhythmGameScreen(this.beatmap));
        });
    }

    private void backToSelect() {
        if (this.transition.isExiting() || this.minecraft == null) return;
        AudioManager.fadeOutMusic(220L);
        this.transition.startExit(240L, () -> {
            AudioManager.stopMusic();
            this.minecraft.setScreen(new SongSelectScreen(this.beatmap));
        });
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float enterProgress = this.transition.getEnterProgress();

        // Fondo oscuro estilizado
        g.fillGradient(0, 0, this.width, this.height, 0xF0090D16, 0xF805070C);

        // Encabezado
        int rankColor = getRankColor(this.rank);
        g.fill(0, 0, this.width, 52, 0xCC111724);
        g.fill(0, 51, this.width, 52, rankColor);

        g.drawCenteredString(this.font, "§a§l★ STAGE CLEARED ★", this.width / 2, 8, 0xFF66FFAA);
        String songTitle = this.beatmap.getArtist() + " - " + this.beatmap.getTitle() + " [" + this.beatmap.getVersion() + "]";
        g.drawCenteredString(this.font, songTitle, this.width / 2, 22, 0xFFFFFFFF);

        if (this.isNewRecord) {
            String recordText = this.previousBestScore > 0
                    ? String.format(Locale.ROOT, "§6§l★ ¡NUEVO RÉCORD PERSONAL! (+%d pts) ★", this.score - this.previousBestScore)
                    : "§6§l★ ¡PRIMER RÉCORD REGISTRADO! ★";
            g.drawCenteredString(this.font, recordText, this.width / 2, 35, 0xFFFFD54F);
        } else {
            String mapperLine = "Mapeado por " + this.beatmap.getCreator() + " • " + this.beatmap.getKeyCount() + "K Mania";
            g.drawCenteredString(this.font, mapperLine, this.width / 2, 35, 0xFF8899AA);
        }

        // Deslizamiento sutil de entrada
        int slideOffset = Math.round((1.0f - enterProgress) * 18.0f);

        // Panel Izquierdo: Rango + Score + Accuracy + Max Combo
        renderLeftSummaryPanel(g, this.leftPanelX - slideOffset, this.leftPanelY, this.leftPanelW, this.leftPanelH, rankColor, enterProgress);

        // Panel Derecho: Desglose de Juicios
        renderRightJudgementPanel(g, this.rightPanelX + slideOffset, this.rightPanelY, this.rightPanelW, this.rightPanelH, enterProgress);

        super.render(g, mouseX, mouseY, partialTick);

        // Overlay de transición (Fade-In / Fade-Out)
        this.transition.renderOverlay(g, this.width, this.height);
    }

    private void renderLeftSummaryPanel(GuiGraphics g, int x, int y, int w, int h, int rankColor, float progress) {
        g.fill(x, y, x + w, y + h, 0xD8111722);
        drawBorder(g, x, y, w, h, 0xFF26334D);

        int centerX = x + w / 2;
        g.drawCenteredString(this.font, "§7RANGO FINAL", centerX, y + 10, 0xFF8899AA);

        // Letra de rango gigante escalada con pop sutil
        float scale = 4.0f * (0.85f + 0.15f * progress);
        int rankCenterY = y + 42;
        g.pose().pushMatrix();
        g.pose().translate(centerX, rankCenterY);
        g.pose().scale(scale, scale);
        int rw = this.font.width(this.rank);
        g.drawString(this.font, this.rank, -rw / 2, -4, rankColor, true);
        g.pose().popMatrix();

        // Tarjetas de Score, Accuracy y Max Combo
        int cardX = x + 12;
        int cardW = w - 24;
        int cursorY = y + Math.max(76, h - 108);
        int cardH = 26;
        int gap = 6;

        int animatedScore = Math.round(this.score * progress);
        drawMetricRow(g, cardX, cursorY, cardW, cardH, "SCORE TOTAL", String.format(Locale.ROOT, "%07d", animatedScore), 0xFFFFFFFF);
        cursorY += cardH + gap;

        drawMetricRow(g, cardX, cursorY, cardW, cardH, "PRECISIÓN (ACC)", String.format(Locale.ROOT, "%.2f%%", this.accuracy), 0xFF00E5FF);
        cursorY += cardH + gap;

        drawMetricRow(g, cardX, cursorY, cardW, cardH, "COMBO MÁXIMO", this.maxCombo + "x", 0xFFFFCA28);
    }

    private void renderRightJudgementPanel(GuiGraphics g, int x, int y, int w, int h, float progress) {
        g.fill(x, y, x + w, y + h, 0xD8111722);
        drawBorder(g, x, y, w, h, 0xFF26334D);

        g.drawString(this.font, "§7DESGLOSE DE JUICIOS", x + 12, y + 10, 0xFF8899AA, false);
        g.fill(x + 12, y + 22, x + w - 12, y + 23, 0x33FFFFFF);

        Judgement[] order = {
                Judgement.MARVELOUS,
                Judgement.PERFECT,
                Judgement.GREAT,
                Judgement.BAD,
                Judgement.MISS
        };

        int startY = y + 32;
        int availableH = Math.max(100, h - 44);
        int rowSpacing = Math.min(34, availableH / order.length);
        int barX = x + 12;
        int barW = w - 24;

        for (int i = 0; i < order.length; i++) {
            Judgement j = order[i];
            int rowY = startY + i * rowSpacing;
            int count = this.judgementCounts.getOrDefault(j, 0);
            float ratio = (float) count / (float) this.totalJudged;

            String label = j.getLabel();
            String countText = String.format(Locale.ROOT, "%d (%.1f%%)", count, ratio * 100.0f);
            int color = j.getColor();

            g.drawString(this.font, label, barX, rowY, color, true);
            int countW = this.font.width(countText);
            g.drawString(this.font, countText, barX + barW - countW, rowY, 0xFFE0E0E0, false);

            int barTop = rowY + 11;
            int barH = 6;
            g.fill(barX, barTop, barX + barW, barTop + barH, 0xFF0A0E16);
            int fillW = Math.round(barW * ratio * progress);
            if (fillW > 0) {
                g.fill(barX, barTop, barX + fillW, barTop + barH, color);
            }
        }
    }

    private void drawMetricRow(GuiGraphics g, int x, int y, int w, int h, String label, String value, int valueColor) {
        g.fill(x, y, x + w, y + h, 0x990A0E16);
        g.fill(x, y, x + 3, y + h, valueColor);
        g.drawString(this.font, label, x + 8, y + 3, 0xFF8899AA, false);
        g.drawString(this.font, value, x + 8, y + 14, valueColor, true);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.transition.isExiting()) return true;
        int key = event.key();
        if (key == GLFW.GLFW_KEY_R) {
            retryMap();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
            backToSelect();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        backToSelect();
    }

    @Override
    public void removed() {
        super.removed();
        if (!this.transition.isExiting()) {
            AudioManager.stopMusic();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int getRankColor(String rank) {
        if (rank == null) return 0xFF888888;
        return switch (rank) {
            case "SS" -> 0xFFFFEA00;
            case "S" -> 0xFF00E5FF;
            case "A" -> 0xFF66BB6A;
            case "B" -> 0xFF42A5F5;
            case "C" -> 0xFFAB47BC;
            default -> 0xFFEF5350;
        };
    }
}
