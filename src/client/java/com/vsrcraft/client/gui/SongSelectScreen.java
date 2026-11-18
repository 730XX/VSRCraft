package com.vsrcraft.client.gui;

import com.vsrcraft.client.audio.AudioManager;
import com.vsrcraft.client.config.OsuDifficultyColor;
import com.vsrcraft.client.config.VSRCraftSettings;
import com.vsrcraft.client.domain.Beatmap;
import com.vsrcraft.client.domain.BeatmapRepository;
import com.vsrcraft.client.domain.BeatmapSet;
import com.vsrcraft.client.domain.ReplayData;
import com.vsrcraft.client.domain.ReplayRepository;
import com.vsrcraft.client.domain.ScoreTracker;
import com.vsrcraft.client.engine.RhythmEngine;
import com.vsrcraft.client.parser.OsrReplayParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pantalla de Selección de Canciones (Song Select) estilo osu!mania con agrupación por Canción (BeatmapSet):
 * - Columna Izquierda: Ficha técnica detallada de la dificultad seleccionada, BPM, notas, récord y botón JUGAR.
 * - Columna Derecha: Buscador y lista agrupada por canciones (cards principales) que se despliegan en acordeón.
 */
public class SongSelectScreen extends Screen {

    private static class ListItem {
        final boolean isSetHeader;
        final BeatmapSet set;
        final Beatmap difficulty;
        final int height;
        int yTop;

        ListItem(BeatmapSet set) {
            this.isSetHeader = true;
            this.set = set;
            this.difficulty = null;
            this.height = 28;
        }

        ListItem(BeatmapSet set, Beatmap difficulty) {
            this.isSetHeader = false;
            this.set = set;
            this.difficulty = difficulty;
            this.height = 23;
        }
    }

    private static final int ITEM_GAP = 3;

    private List<BeatmapSet> allSets = new ArrayList<>();
    private List<BeatmapSet> filteredSets = new ArrayList<>();
    private final List<ListItem> visibleItems = new ArrayList<>();

    private static String rememberedSetId = null;
    private static String rememberedBeatmapId = null;
    private static String rememberedSearchQuery = "";
    private static double rememberedScrollOffset = -1.0;
    private static float rememberedPlaybackRate = 1.0f;

    private String expandedSetId = null;
    private Beatmap selectedBeatmap = null;
    private int selectedVisibleIndex = 0;
    private String currentSearchQuery = "";

    // Scroll suave para la columna derecha
    private double scrollOffset = 0.0;
    private double targetScrollOffset = 0.0;
    private int totalContentHeight = 0;

    // Detección de doble clic
    private long lastClickTimeMs = 0L;
    private int lastClickedItemIndex = -1;

    // Layout dinámico
    private int leftPanelX, leftPanelY, leftPanelW, leftPanelH;
    private int rightPanelX, rightPanelY, rightPanelW, rightPanelH;
    private int listStartX, listStartY, listWidth, listHeight;

    private EditBox searchBox;
    private Button playButton;
    private Button replayButton;
    private ReplayData selectedReplayMeta = null;
    private int recordBoxX, recordBoxY, recordBoxW, recordBoxH;
    private boolean recordBoxClickable = false;
    private boolean transitioningToSubScreen = false;
    private boolean hasTriggeredInitialPreview = false;
    private final ScreenTransition transition = new ScreenTransition(300L);

    public SongSelectScreen() {
        this(null);
    }

    public SongSelectScreen(Beatmap preselectedBeatmap) {
        super(Component.literal("VSRCraft - Selector de Canciones"));
        this.allSets = BeatmapRepository.getBeatmapSets();
        if (preselectedBeatmap != null) {
            this.selectedBeatmap = preselectedBeatmap;
            this.expandedSetId = preselectedBeatmap.getBeatmapSetId();
            this.currentSearchQuery = rememberedSearchQuery != null ? rememberedSearchQuery : "";
        } else if (rememberedBeatmapId != null) {
            this.currentSearchQuery = rememberedSearchQuery != null ? rememberedSearchQuery : "";
            this.expandedSetId = rememberedSetId;
            for (BeatmapSet s : this.allSets) {
                for (Beatmap d : s.getDifficulties()) {
                    if (d.getId().equals(rememberedBeatmapId)) {
                        this.selectedBeatmap = d;
                        this.expandedSetId = s.getId();
                        break;
                    }
                }
                if (this.selectedBeatmap != null) break;
            }
        }

        if (this.selectedBeatmap == null && !this.allSets.isEmpty()) {
            BeatmapSet firstSet = this.allSets.get(0);
            this.expandedSetId = firstSet.getId();
            this.selectedBeatmap = firstSet.getDefaultBeatmap();
        }

        this.filteredSets = new ArrayList<>();
        for (BeatmapSet set : this.allSets) {
            if (set.matchesQuery(this.currentSearchQuery)) {
                this.filteredSets.add(set);
            }
        }
        if (this.filteredSets.isEmpty() && !this.allSets.isEmpty()) {
            this.currentSearchQuery = "";
            this.filteredSets.addAll(this.allSets);
        }
    }

    @Override
    protected void init() {
        super.init();
        this.transitioningToSubScreen = false;
        this.transition.startEnter(260L);

        int margin = 10;
        int topBarH = 28;
        int bottomBarH = 18;
        int contentY = topBarH + 6;
        int contentH = this.height - contentY - bottomBarH - 4;

        // Distribución de 2 columnas: ~40% izquierda, ~60% derecha
        this.leftPanelX = margin;
        this.leftPanelY = contentY;
        this.leftPanelW = Math.max(180, Math.min(260, (int) ((this.width - margin * 3) * 0.40f)));
        this.leftPanelH = contentH;

        this.rightPanelX = this.leftPanelX + this.leftPanelW + margin;
        this.rightPanelY = contentY;
        this.rightPanelW = this.width - this.rightPanelX - margin;
        this.rightPanelH = contentH;

        // 1. Botón [< Salir]
        this.addRenderableWidget(Button.builder(
                Component.literal("< Salir"),
                btn -> this.onClose()
        ).bounds(margin, 4, 54, 20).build());

        // 2. Botón [⚙] Ajustes
        int gearX = this.width - margin - 24;
        this.addRenderableWidget(Button.builder(
                Component.literal("⚙"),
                btn -> openSettings()
        )
        .bounds(gearX, 4, 24, 20)
        .tooltip(Tooltip.create(Component.literal("Ajustes y Personalización")))
        .build());

        // 3. Botón [↻] Recargar beatmaps
        int reloadX = gearX - 28;
        this.addRenderableWidget(Button.builder(
                Component.literal("↻"),
                btn -> reloadCatalog()
        )
        .bounds(reloadX, 4, 24, 20)
        .tooltip(Tooltip.create(Component.literal("Recargar canciones (.minecraft/vsrg_beatmaps)")))
        .build());

        // 4. Barra de búsqueda en la parte superior derecha
        int searchH = 20;
        this.searchBox = new EditBox(
                this.font,
                this.rightPanelX,
                this.rightPanelY,
                this.rightPanelW,
                searchH,
                Component.literal("Buscar canción...")
        );
        this.searchBox.setMaxLength(80);
        this.searchBox.setHint(Component.literal("§7🔍 Buscar por título, artista o dificultad..."));
        this.searchBox.setValue(this.currentSearchQuery);
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);

        // Área de lista de canciones y dificultades
        this.listStartX = this.rightPanelX;
        this.listStartY = this.rightPanelY + searchH + 5;
        this.listWidth = this.rightPanelW;
        this.listHeight = Math.max(40, this.rightPanelH - searchH - 5);

        // 5. Botones principales [▶ JUGAR BEATMAP] y [▶ Replay]
        int totalBottomBtnW = this.leftPanelW - 16;
        int replayBtnW = 68;
        int playBtnGap = 4;
        int playBtnW = totalBottomBtnW - replayBtnW - playBtnGap;
        int playBtnX = this.leftPanelX + 8;
        int playBtnY = this.leftPanelY + this.leftPanelH - 28;
        this.playButton = Button.builder(
                Component.literal("§b§l▶ JUGAR"),
                btn -> startSelectedBeatmap()
        ).bounds(playBtnX, playBtnY, playBtnW, 20).build();
        this.addRenderableWidget(this.playButton);

        this.replayButton = Button.builder(
                Component.literal("§e▶ Replay"),
                btn -> watchSelectedReplay()
        ).bounds(playBtnX + playBtnW + playBtnGap, playBtnY, replayBtnW, 20)
         .tooltip(Tooltip.create(Component.literal("Ver el Replay guardado de tu mejor puntuación (F1)")))
         .build();
        this.addRenderableWidget(this.replayButton);

        rebuildVisibleList();
        if (rememberedScrollOffset >= 0.0) {
            this.targetScrollOffset = rememberedScrollOffset;
            this.scrollOffset = rememberedScrollOffset;
            clampScroll();
        }
        ensureIndexVisible(this.selectedVisibleIndex);
        this.scrollOffset = this.targetScrollOffset;
        triggerPreviewForSelected();
    }

    private void rebuildVisibleList() {
        this.visibleItems.clear();
        int yAccum = 0;

        for (BeatmapSet set : this.filteredSets) {
            ListItem setItem = new ListItem(set);
            setItem.yTop = yAccum;
            this.visibleItems.add(setItem);
            yAccum += setItem.height + ITEM_GAP;

            // Si este set está expandido, desplegamos sus dificultades hijas debajo
            if (set.getId().equals(this.expandedSetId)) {
                for (Beatmap diff : set.getDifficulties()) {
                    ListItem diffItem = new ListItem(set, diff);
                    diffItem.yTop = yAccum;
                    this.visibleItems.add(diffItem);
                    yAccum += diffItem.height + ITEM_GAP;
                }
            }
        }

        this.totalContentHeight = Math.max(0, yAccum - ITEM_GAP);
        clampScroll();
        syncSelectedVisibleIndex();

        if (this.playButton != null) {
            this.playButton.active = (this.selectedBeatmap != null);
        }
        updateReplayButtonState();
    }

    private void updateReplayButtonState() {
        this.selectedReplayMeta = (this.selectedBeatmap != null) ? ReplayRepository.loadReplay(this.selectedBeatmap) : null;
        if (this.replayButton != null) {
            this.replayButton.active = (this.selectedReplayMeta != null);
        }
    }

    private void syncSelectedVisibleIndex() {
        if (this.selectedBeatmap == null) return;
        for (int i = 0; i < this.visibleItems.size(); i++) {
            ListItem item = this.visibleItems.get(i);
            if (!item.isSetHeader && item.difficulty != null && item.difficulty.getId().equals(this.selectedBeatmap.getId())) {
                this.selectedVisibleIndex = i;
                return;
            }
        }
        // Si la dificultad no es visible pero su set sí
        for (int i = 0; i < this.visibleItems.size(); i++) {
            ListItem item = this.visibleItems.get(i);
            if (item.isSetHeader && item.set.getId().equals(this.expandedSetId)) {
                this.selectedVisibleIndex = i;
                return;
            }
        }
    }

    private void onSearchChanged(String query) {
        this.currentSearchQuery = query;
        this.filteredSets.clear();

        for (BeatmapSet set : this.allSets) {
            if (set.matchesQuery(query)) {
                this.filteredSets.add(set);
            }
        }

        // Si el set expandido actual ya no está en los resultados, expandir el primero
        boolean expandedStillPresent = false;
        for (BeatmapSet s : this.filteredSets) {
            if (s.getId().equals(this.expandedSetId)) {
                expandedStillPresent = true;
                break;
            }
        }

        if (!expandedStillPresent) {
            if (!this.filteredSets.isEmpty()) {
                BeatmapSet first = this.filteredSets.get(0);
                this.expandedSetId = first.getId();
                this.selectedBeatmap = first.getDefaultBeatmap();
            } else {
                this.expandedSetId = null;
                this.selectedBeatmap = null;
            }
        }

        rebuildVisibleList();
        if (rememberedScrollOffset >= 0.0) {
            this.targetScrollOffset = rememberedScrollOffset;
            this.scrollOffset = rememberedScrollOffset;
            clampScroll();
        }
        ensureIndexVisible(this.selectedVisibleIndex);
        this.scrollOffset = this.targetScrollOffset;
        triggerPreviewForSelected();
    }

    private void reloadCatalog() {
        BeatmapRepository.reload();
        this.allSets = BeatmapRepository.getBeatmapSets();
        onSearchChanged(this.currentSearchQuery);
    }

    private void saveSelectionState() {
        rememberedSetId = this.expandedSetId;
        rememberedBeatmapId = (this.selectedBeatmap != null) ? this.selectedBeatmap.getId() : null;
        rememberedSearchQuery = this.currentSearchQuery;
        rememberedScrollOffset = this.targetScrollOffset;
    }

    private void openSettings() {
        if (this.transition.isExiting() || this.minecraft == null) return;
        saveSelectionState();
        this.transitioningToSubScreen = true;
        this.transition.startExit(180L, () -> this.minecraft.setScreen(new VSRSettingsScreen(this)));
    }

    private void startSelectedBeatmap() {
        if (this.selectedBeatmap == null || this.minecraft == null || this.transition.isExiting()) return;
        saveSelectionState();
        this.transitioningToSubScreen = true;
        AudioManager.fadeOutMusic(220L);
        final Beatmap targetMap = this.selectedBeatmap;
        final float targetRate = rememberedPlaybackRate;
        this.transition.startExit(240L, () -> this.minecraft.setScreen(new RhythmGameScreen(targetMap, targetRate)));
    }

    private String statusMessage = "";
    private int statusMessageColor = 0xFF55FFAA;
    private long statusMessageUntilMs = 0L;

    private void showStatusMessage(String message, int color) {
        this.statusMessage = message;
        this.statusMessageColor = color;
        this.statusMessageUntilMs = System.currentTimeMillis() + 4000L;
    }

    private void openSelectedResults() {
        if (this.selectedBeatmap == null || this.minecraft == null || this.transition.isExiting()) return;
        final Beatmap targetMap = this.selectedBeatmap;
        final ReplayData replay = this.selectedReplayMeta;
        final VSRCraftSettings.BeatmapRecord record = VSRCraftSettings.get().getPersonalBest(targetMap.getId());

        saveSelectionState();
        this.transitioningToSubScreen = true;
        AudioManager.fadeOutMusic(220L);

        if (replay != null && replay.events != null && !replay.events.isEmpty()) {
            ScoreTracker simTracker = simulateReplayToTracker(targetMap, replay);
            int prevBest = (record != null) ? record.bestScore : simTracker.getScore();
            this.transition.startExit(240L, () -> this.minecraft.setScreen(
                    new ResultsScreen(targetMap, simTracker, false, prevBest, replay)));
        } else if (record != null && record.bestScore > 0) {
            this.transition.startExit(240L, () -> this.minecraft.setScreen(
                    new ResultsScreen(targetMap, record.bestScore, record.bestAccuracy, record.bestCombo, record.bestRank)));
        }
    }

    private static ScoreTracker simulateReplayToTracker(Beatmap map, ReplayData replay) {
        RhythmEngine sim = new RhythmEngine(map);
        sim.start(0);
        int keyCount = map.getKeyCount();
        for (ReplayData.ReplayEvent ev : replay.events) {
            if (ev.lane < 0 || ev.lane >= keyCount) continue;
            sim.update(ev.timeMs);
            if (ev.pressed) {
                sim.onLanePressed(ev.lane, ev.timeMs);
            } else {
                sim.onLaneReleased(ev.lane, ev.timeMs);
            }
        }
        sim.update(map.getDurationMs() + 1000L);
        return sim.getScoreTracker();
    }

    private void watchSelectedReplay() {
        if (this.selectedBeatmap == null || this.minecraft == null || this.transition.isExiting()) return;
        ReplayData replay = ReplayRepository.loadReplay(this.selectedBeatmap);
        if (replay == null) return;
        launchReplay(this.selectedBeatmap, replay);
    }

    private void launchReplay(Beatmap targetMap, ReplayData replay) {
        if (targetMap == null || replay == null || this.minecraft == null || this.transition.isExiting()) return;
        this.selectedBeatmap = targetMap;
        if (targetMap.getBeatmapSetId() != null) {
            this.expandedSetId = targetMap.getBeatmapSetId();
        }
        saveSelectionState();
        this.transitioningToSubScreen = true;
        AudioManager.fadeOutMusic(220L);
        this.transition.startExit(240L, () -> this.minecraft.setScreen(new RhythmGameScreen(targetMap, replay)));
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        if (files == null || files.isEmpty()) return;

        BeatmapSet lastImportedSet = null;
        int importedOszCount = 0;

        for (Path path : files) {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".osz")) {
                try {
                    BeatmapSet imported = BeatmapRepository.importOsz(path);
                    if (imported != null) {
                        lastImportedSet = imported;
                        importedOszCount++;
                    } else if (importedOszCount == 0) {
                        showStatusMessage("El .osz no contiene mapas de osu!mania.", 0xFFFF6666);
                    }
                } catch (Exception e) {
                    showStatusMessage("Error al importar .osz: " + e.getMessage(), 0xFFFF6666);
                    return;
                }
            }
        }

        if (importedOszCount > 0 && lastImportedSet != null) {
            this.allSets = BeatmapRepository.getBeatmapSets();
            this.currentSearchQuery = "";
            if (this.searchBox != null) {
                this.searchBox.setValue("");
            }
            this.filteredSets = new ArrayList<>(this.allSets);
            this.expandedSetId = lastImportedSet.getId();
            this.selectedBeatmap = lastImportedSet.getDefaultBeatmap();
            rebuildVisibleList();
            ensureIndexVisible(this.selectedVisibleIndex);
            this.scrollOffset = this.targetScrollOffset;
            triggerPreviewForSelected();
            saveSelectionState();

            String msg = (importedOszCount == 1)
                    ? "✔ Mapa importado: " + lastImportedSet.getTitle() + " (" + lastImportedSet.getDifficulties().size() + " dif.)"
                    : "✔ " + importedOszCount + " mapas .osz importados";
            showStatusMessage(msg, 0xFF55FF99);
            return;
        }

        for (Path path : files) {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".osr")) continue;

            try {
                OsrReplayParser.OsrHeader header = OsrReplayParser.readHeader(path);
                if (header.gameMode() != 3) {
                    showStatusMessage("El archivo .osr no es de osu!mania.", 0xFFFF6666);
                    return;
                }
                Beatmap matched = BeatmapRepository.findByOsuMd5(header.beatmapMd5());
                Beatmap target = (matched != null) ? matched : this.selectedBeatmap;
                if (target == null) {
                    showStatusMessage("No se encontró el mapa del .osr ni hay uno seleccionado.", 0xFFFF6666);
                    return;
                }
                ReplayData replay = ReplayRepository.loadOsrFile(path, target);
                if (replay.events.isEmpty()) {
                    showStatusMessage("El .osr no contiene eventos de teclas.", 0xFFFF6666);
                    return;
                }
                ReplayRepository.saveImportedOsr(path, replay);
                updateReplayButtonState();
                launchReplay(target, replay);
                return;
            } catch (Exception e) {
                showStatusMessage("Error al leer .osr: " + e.getMessage(), 0xFFFF6666);
                return;
            }
        }
    }

    private void triggerPreviewForSelected() {
        updateReplayButtonState();
        if (this.selectedBeatmap != null && this.selectedBeatmap.getAudioResourcePath() != null && !this.selectedBeatmap.getAudioResourcePath().isEmpty()) {
            boolean isInitial = !this.hasTriggeredInitialPreview;
            this.hasTriggeredInitialPreview = true;
            AudioManager.requestPreview(this.selectedBeatmap.getAudioResourcePath(), this.selectedBeatmap.getPreviewTimeMs(), isInitial);
        } else {
            AudioManager.stopMusic();
        }
    }

    private void ensureIndexVisible(int index) {
        if (index < 0 || index >= this.visibleItems.size() || this.listHeight <= 0) return;
        ListItem item = this.visibleItems.get(index);
        int itemTop = item.yTop;
        int itemBottom = itemTop + item.height;

        if (itemTop < this.targetScrollOffset) {
            this.targetScrollOffset = itemTop;
        } else if (itemBottom > this.targetScrollOffset + this.listHeight) {
            this.targetScrollOffset = itemBottom - this.listHeight;
        }
        clampScroll();
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, this.totalContentHeight - this.listHeight);
        this.targetScrollOffset = Math.max(0.0, Math.min(maxScroll, this.targetScrollOffset));
        this.scrollOffset = Math.max(0.0, Math.min(maxScroll, this.scrollOffset));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Actualizar preview de audio asíncrono
        AudioManager.updatePreview();

        // Interpolación suave del scroll
        this.scrollOffset += (this.targetScrollOffset - this.scrollOffset) * 0.32;
        if (Math.abs(this.targetScrollOffset - this.scrollOffset) < 0.2) {
            this.scrollOffset = this.targetScrollOffset;
        }

        // 1. Fondo estilizado
        g.fillGradient(0, 0, this.width, this.height, 0xE60B0E14, 0xF206080C);

        // 2. Barra Superior (Header)
        g.fill(0, 0, this.width, 28, 0xCC111622);
        g.fill(0, 27, this.width, 28, 0xFF00D4FF);
        g.drawString(this.font, "§b§lVSRCRAFT §8// §fSELECTOR DE CANCIONES", 72, 10, 0xFFFFFFFF, true);

        // Contador de MAPAS (Canciones únicas, sin contar cada dificultad suelta)
        int songCount = this.filteredSets.size();
        String countBadge = songCount + (songCount == 1 ? " canción" : " canciones");
        int countW = this.font.width(countBadge);
        g.drawString(this.font, countBadge, this.width - 74 - countW, 10, 0xFF00E5FF, true);

        // 3. Renderizar Columna Izquierda (Detalle técnico de la dificultad seleccionada)
        renderLeftDetailsColumn(g, mouseX, mouseY);

        // 4. Renderizar Columna Derecha (Acordeón de canciones y dificultades)
        renderRightSongListColumn(g, mouseX, mouseY);

        // 5. Barra Inferior (Atajos)
        g.fill(0, this.height - 18, this.width, this.height, 0xCC0D111A);
        g.fill(0, this.height - 18, this.width, this.height - 17, 0x44FFFFFF);
        String footer = String.format(Locale.ROOT,
                "Clic: Desplegar/Elegir  |  ENTER / Doble Clic: Jugar  |  Scroll: %.2fx (F3/F4)  |  Rate: %.2fx (F5/F6)  |  Offset: %+dms",
                VSRCraftSettings.get().scrollSpeed, rememberedPlaybackRate, VSRCraftSettings.get().globalAudioOffsetMs);
        g.drawCenteredString(this.font, footer, this.width / 2, this.height - 13, 0xFF99AABB);

        // 6. Widgets
        super.render(g, mouseX, mouseY, partialTick);

        if (!this.statusMessage.isEmpty() && System.currentTimeMillis() < this.statusMessageUntilMs) {
            int msgW = this.font.width(this.statusMessage) + 18;
            int msgX = (this.width - msgW) / 2;
            int msgY = this.height - 42;
            g.fill(msgX, msgY, msgX + msgW, msgY + 18, 0xE6101622);
            drawBorder(g, msgX, msgY, msgW, 18, this.statusMessageColor);
            g.drawCenteredString(this.font, this.statusMessage, this.width / 2, msgY + 5, this.statusMessageColor);
        }

        // 7. Transición suave (Fade-In / Fade-Out)
        this.transition.renderOverlay(g, this.width, this.height);
    }

    private void renderLeftDetailsColumn(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(leftPanelX, leftPanelY, leftPanelX + leftPanelW, leftPanelY + leftPanelH, 0xD8121722);
        drawBorder(g, leftPanelX, leftPanelY, leftPanelW, leftPanelH, 0xFF26334D);

        Beatmap map = this.selectedBeatmap;
        if (map == null) {
            g.drawCenteredString(this.font, "Sin canciones seleccionadas", leftPanelX + leftPanelW / 2, leftPanelY + leftPanelH / 2 - 5, 0xFF888888);
            return;
        }

        float stars = map.getEstimatedStarRating();
        int diffColor = getDifficultyColor(stars);

        // Banner superior (con imagen de fondo del .osu si existe)
        int bannerH = 58;
        String bgPath = map.getBackgroundFilePath();
        if (bgPath != null && !bgPath.isEmpty()) {
            BeatmapBackgroundCache.drawBannerBackground(g, bgPath, leftPanelX + 1, leftPanelY + 1, leftPanelW - 2, bannerH - 1);
            g.fillGradient(leftPanelX + 1, leftPanelY + 1, leftPanelX + leftPanelW - 1, leftPanelY + bannerH, 0x990C111B, 0xE6121722);
        } else {
            g.fillGradient(leftPanelX + 1, leftPanelY + 1, leftPanelX + leftPanelW - 1, leftPanelY + bannerH, 0xFF1A273B, 0xFF121722);
        }
        g.fill(leftPanelX + 1, leftPanelY + 1, leftPanelX + leftPanelW - 1, leftPanelY + 3, diffColor);

        int innerX = leftPanelX + 8;
        int maxTextW = leftPanelW - 16;

        String modeTag = "[" + map.getKeyCount() + "K MANIA]";
        g.drawString(this.font, modeTag, innerX, leftPanelY + 7, 0xFF00E5FF, false);

        String starTag = String.format(Locale.ROOT, "★ %.2f", stars);
        int starW = this.font.width(starTag);
        g.drawString(this.font, starTag, leftPanelX + leftPanelW - 8 - starW, leftPanelY + 7, diffColor, true);

        String title = ellipsize(map.getTitle(), maxTextW);
        g.drawString(this.font, "§l" + title, innerX, leftPanelY + 20, 0xFFFFFFFF, true);

        String artist = ellipsize(map.getArtist(), maxTextW);
        g.drawString(this.font, artist, innerX, leftPanelY + 32, 0xFF99CCFF, false);

        String versionAndMapper = ellipsize("[" + map.getVersion() + "] • por " + map.getCreator(), maxTextW);
        g.drawString(this.font, versionAndMapper, innerX, leftPanelY + 44, 0xFFAAAAAA, false);

        int yCursor = leftPanelY + bannerH + 2;
        g.fill(innerX, yCursor, leftPanelX + leftPanelW - 8, yCursor + 1, 0x44FFFFFF);
        yCursor += 6;

        // Cuadrícula de Estadísticas
        int colGap = 6;
        int cellW = (maxTextW - colGap) / 2;
        int cellH = 22;
        int rowGap = 4;

        drawStatCell(g, innerX, yCursor, cellW, cellH, "DURACIÓN", map.getFormattedDuration(), 0xFF00E5FF);
        drawStatCell(g, innerX + cellW + colGap, yCursor, cellW, cellH, "BPM", map.getBpm() + " BPM", 0xFFFFD54F);
        yCursor += cellH + rowGap;

        drawStatCell(g, innerX, yCursor, cellW, cellH, "NOTAS TOTALES", String.valueOf(map.getNotes().size()), 0xFFFFFFFF);
        drawStatCell(g, innerX + cellW + colGap, yCursor, cellW, cellH, "TAPS / HOLDS", map.getTapCount() + " / " + map.getHoldCount(), 0xFF81C784);
        yCursor += cellH + rowGap;

        String odHpText = String.format(Locale.ROOT, "OD %.1f | HP %.1f", map.getOverallDifficulty(), map.getHpDrainRate());
        String scrollText = String.format(Locale.ROOT, "%.2fx", VSRCraftSettings.get().scrollSpeed);
        drawStatCell(g, innerX, yCursor, cellW, cellH, "PRECISIÓN", odHpText, 0xFFFF8A65);
        drawStatCell(g, innerX + cellW + colGap, yCursor, cellW, cellH, "VEL. SCROLL", scrollText, 0xFFBA68C8);
        yCursor += cellH + 8;

        // Récord Personal / Replay guardado (clic para abrir ResultsScreen)
        int playBtnTop = leftPanelY + leftPanelH - 32;
        int recordBoxH = playBtnTop - yCursor - 2;
        this.recordBoxClickable = false;
        if (recordBoxH >= 32) {
            VSRCraftSettings.BeatmapRecord record = VSRCraftSettings.get().getPersonalBest(map.getId());
            boolean hasPersonal = record != null && record.bestScore > 0;
            boolean hasReplay = this.selectedReplayMeta != null;
            this.recordBoxClickable = hasPersonal || hasReplay;
            this.recordBoxX = innerX;
            this.recordBoxY = yCursor;
            this.recordBoxW = maxTextW;
            this.recordBoxH = recordBoxH;

            boolean hoverRecord = this.recordBoxClickable
                    && mouseX >= innerX && mouseX <= innerX + maxTextW
                    && mouseY >= yCursor && mouseY <= yCursor + recordBoxH;

            g.fill(innerX, yCursor, innerX + maxTextW, yCursor + recordBoxH, hoverRecord ? 0xBB121A2B : 0x990B0E17);
            drawBorder(g, innerX, yCursor, maxTextW, recordBoxH, hoverRecord ? 0xFF00E5FF : 0xFF1F2B40);

            if (hasPersonal) {
                g.drawString(this.font, "§7RÉCORD PERSONAL", innerX + 6, yCursor + 5, 0xFF8899AA, false);
                int rankColor = getRankColor(record.bestRank);
                String rankLabel = "§l" + record.bestRank;
                int rankW = this.font.width(rankLabel);
                g.drawString(this.font, rankLabel, innerX + maxTextW - 8 - rankW, yCursor + 5, rankColor, true);

                String scoreLine = String.format(Locale.ROOT, "Score: %,d", record.bestScore);
                g.drawString(this.font, scoreLine, innerX + 6, yCursor + 17, 0xFFFFFFFF, false);

                if (recordBoxH >= 42) {
                    String accComboLine = String.format(Locale.ROOT, "Acc: %.2f%%  •  Combo: %dx", record.bestAccuracy, record.bestCombo);
                    g.drawString(this.font, accComboLine, innerX + 6, yCursor + 28, 0xFF80DEEA, false);
                }
            } else if (hasReplay) {
                String headerLabel = (this.selectedReplayMeta.playerName != null && !this.selectedReplayMeta.playerName.isEmpty())
                        ? "§eREPLAY // " + ellipsize(this.selectedReplayMeta.playerName, maxTextW - 70)
                        : "§eREPLAY GUARDADO";
                g.drawString(this.font, headerLabel, innerX + 6, yCursor + 5, 0xFFFFD54F, false);

                int rankColor = getRankColor(this.selectedReplayMeta.rank);
                String rankLabel = "§l" + this.selectedReplayMeta.rank;
                int rankW = this.font.width(rankLabel);
                g.drawString(this.font, rankLabel, innerX + maxTextW - 8 - rankW, yCursor + 5, rankColor, true);

                String scoreLine = String.format(Locale.ROOT, "Score: %,d", this.selectedReplayMeta.score);
                g.drawString(this.font, scoreLine, innerX + 6, yCursor + 17, 0xFFFFFFFF, false);

                if (recordBoxH >= 42) {
                    String accComboLine = String.format(Locale.ROOT, "Acc: %.2f%%  •  Combo: %dx",
                            this.selectedReplayMeta.accuracy, this.selectedReplayMeta.maxCombo);
                    g.drawString(this.font, accComboLine, innerX + 6, yCursor + 28, 0xFF80DEEA, false);
                }
            } else {
                g.drawString(this.font, "§7RÉCORD PERSONAL", innerX + 6, yCursor + 5, 0xFF8899AA, false);
                g.drawString(this.font, "Sin récord registrado aún", innerX + 6, yCursor + 17, 0xFF666677, false);
            }

            // Gráfica de densidad NPS en el espacio inferior del recuadro (para mapas con o sin récord)
            int graphTop = (hasPersonal || hasReplay) ? (yCursor + 40) : (yCursor + 29);
            int graphBottom = yCursor + recordBoxH - ((hoverRecord && recordBoxH >= 42) ? 13 : 5);
            int graphH = graphBottom - graphTop;
            if (graphH >= 16) {
                drawMapDensityGraph(g, map, innerX + 6, graphTop, maxTextW - 12, graphH);
            }

            if (hoverRecord && recordBoxH >= 42) {
                String hint = "Clic: Ver Resultados";
                int hintW = this.font.width(hint);
                g.drawString(this.font, hint, innerX + maxTextW - hintW - 6, yCursor + recordBoxH - 11, 0xFF00E5FF, false);
            }
        }
    }

    private void drawMapDensityGraph(GuiGraphics g, Beatmap map, int x, int y, int w, int h) {
        float[] buckets = map.getDensityBuckets();
        if (buckets == null || buckets.length < 2 || w <= 8 || h <= 8) return;

        g.fill(x, y, x + w, y + h, 0x88070A12);
        drawBorder(g, x, y, w, h, 0xFF1A2538);

        int plotX = x + 2;
        int plotW = Math.max(2, w - 4);
        int plotBottom = y + h - 2;
        int plotMaxH = Math.max(4, h - 13);

        // Escala absoluta de NPS (24 NPS techo base) para que mapas fáciles queden bajos y no escalados al 100%
        float ceilNps = Math.max(24.0f, map.getPeakNps());

        // Línea guía horizontal sutil (12 NPS)
        int midRefY = plotBottom - Math.round((12.0f / ceilNps) * plotMaxH);
        if (midRefY > y + 10 && midRefY < plotBottom) {
            g.fill(plotX, midRefY, plotX + plotW, midRefY + 1, 0x1AFFFFFF);
        }

        int n = buckets.length;
        int prevY = -1;

        for (int px = 0; px < plotW; px++) {
            double pos = (px / (double) Math.max(1, plotW - 1)) * (n - 1);
            int idx0 = Math.min(n - 1, (int) Math.floor(pos));
            int idx1 = Math.min(n - 1, idx0 + 1);
            double frac = pos - idx0;
            // Interpolación coseno para una curva suave que sube y baja orgánicamente
            double mu = (1.0 - Math.cos(frac * Math.PI)) * 0.5;
            float nps = (float) (buckets[idx0] * (1.0 - mu) + buckets[idx1] * mu);

            float normH = Math.max(0.0f, Math.min(1.0f, nps / ceilNps));
            int currY = plotBottom - Math.round(normH * plotMaxH);

            int spectrumColor = OsuDifficultyColor.getColorForNps(nps);
            int areaColor = (spectrumColor & 0x00FFFFFF) | 0x42000000;
            int lineColor = (spectrumColor & 0x00FFFFFF) | 0xFF000000;

            int colX = plotX + px;
            // Relleno translúcido debajo de la curva
            if (currY < plotBottom) {
                g.fill(colX, currY, colX + 1, plotBottom, areaColor);
            }

            // Línea curva continua de 2px conectando sin huecos con la columna anterior
            int segTop = (prevY < 0) ? currY : Math.min(prevY, currY);
            int segBottom = (prevY < 0) ? currY : Math.max(prevY, currY);
            int drawTop = Math.max(y + 1, segTop - 1);
            int drawBottom = Math.min(plotBottom, segBottom + 1);
            g.fill(colX, drawTop, colX + 1, drawBottom, lineColor);

            prevY = currY;
        }

        g.drawString(this.font, "DENSIDAD", x + 4, y + 2, 0xFF667788, false);
        String peakStr = String.format(Locale.ROOT, "Pico: %.1f NPS", map.getPeakNps());
        int peakW = this.font.width(peakStr);
        int peakColor = OsuDifficultyColor.getColorForNps(map.getPeakNps());
        g.drawString(this.font, peakStr, x + w - peakW - 4, y + 2, peakColor, false);
    }

    private void drawStatCell(GuiGraphics g, int x, int y, int w, int h, String label, String value, int valueColor) {
        g.fill(x, y, x + w, y + h, 0x880B0E16);
        g.fill(x, y, x + 2, y + h, valueColor);
        g.drawString(this.font, label, x + 5, y + 2, 0xFF778899, false);
        g.drawString(this.font, value, x + 5, y + 11, valueColor, false);
    }

    private void renderRightSongListColumn(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(listStartX, listStartY, listStartX + listWidth, listStartY + listHeight, 0x80090C12);
        drawBorder(g, listStartX, listStartY, listWidth, listHeight, 0xFF1E293B);

        if (this.visibleItems.isEmpty()) {
            g.drawCenteredString(this.font, "No se encontraron canciones para \"" + this.currentSearchQuery + "\"",
                    listStartX + listWidth / 2, listStartY + listHeight / 2 - 4, 0xFF888899);
            return;
        }

        g.enableScissor(listStartX + 1, listStartY + 1, listStartX + listWidth - 1, listStartY + listHeight - 1);

        int scrollPx = (int) Math.round(this.scrollOffset);
        int scrollbarWidth = 5;
        int listInnerWidth = listWidth - scrollbarWidth - 6;

        for (int i = 0; i < this.visibleItems.size(); i++) {
            ListItem item = this.visibleItems.get(i);
            int cardY = listStartY + 3 + item.yTop - scrollPx;

            if (cardY + item.height < listStartY || cardY > listStartY + listHeight) {
                continue; // Fuera del área de visualización
            }

            boolean isHovered = mouseX >= listStartX && mouseX <= listStartX + listInnerWidth
                    && mouseY >= Math.max(listStartY, cardY) && mouseY <= Math.min(listStartY + listHeight, cardY + item.height);

            if (item.isSetHeader) {
                // ==================== CARD PRINCIPAL DE CANCIÓN (SET) ====================
                boolean isExpanded = item.set.getId().equals(this.expandedSetId);
                int cardX = listStartX + (isExpanded ? 2 : 4);
                int cardW = listInnerWidth - (isExpanded ? 2 : 4);

                int bgColor = isExpanded ? 0xE6142638 : (isHovered ? 0xCC182332 : 0xB8121620);
                int borderColor = isExpanded ? 0xFF00E5FF : (isHovered ? 0xFF354860 : 0xFF222B3B);

                Beatmap setDefault = item.set.getDefaultBeatmap();
                String setBgPath = (setDefault != null) ? setDefault.getBackgroundFilePath() : "";
                if (setBgPath != null && !setBgPath.isEmpty()) {
                    BeatmapBackgroundCache.drawBannerBackground(g, setBgPath, cardX + 1, cardY + 1, cardW - 2, item.height - 2);
                    g.fill(cardX + 1, cardY + 1, cardX + cardW - 1, cardY + item.height - 1, isExpanded ? 0xB80E1E2E : (isHovered ? 0xCC101824 : 0xD80D111A));
                } else {
                    g.fill(cardX, cardY, cardX + cardW, cardY + item.height, bgColor);
                }
                drawBorder(g, cardX, cardY, cardW, item.height, borderColor);

                // Icono acordeón: ▼ si está desplegado, ▶ si está cerrado
                String expandIcon = isExpanded ? "▼" : "▶";
                int iconColor = isExpanded ? 0xFF00E5FF : 0xFF7799BB;
                g.drawString(this.font, expandIcon, cardX + 7, cardY + 10, iconColor, false);

                // Lado derecho de la tarjeta del Set: [X diffs] + Modos de teclas + Rango Estrellas
                int rightCursor = cardX + cardW - 6;

                int diffCount = item.set.getDifficulties().size();
                String diffsBadge = "[" + diffCount + (diffCount == 1 ? " diff" : " diffs") + "]";
                int diffsW = this.font.width(diffsBadge);
                rightCursor -= diffsW;
                g.drawString(this.font, diffsBadge, rightCursor, cardY + 5, 0xFF00E5FF, false);

                String keyModes = item.set.getKeyModesSummary();
                int kmW = this.font.width(keyModes);
                g.drawString(this.font, keyModes, rightCursor + diffsW - kmW, cardY + 16, 0xFF88AABB, false);
                rightCursor -= 8;

                String starRange = item.set.getFormattedStarRange();
                int srW = this.font.width(starRange);
                rightCursor -= srW;
                g.drawString(this.font, starRange, rightCursor, cardY + 10, 0xFFFFCA28, true);
                rightCursor -= 8;

                // Título y Artista
                int titleStartX = cardX + 22;
                int maxMainW = Math.max(30, rightCursor - titleStartX);
                String titleLine = ellipsize(item.set.getTitle(), maxMainW);
                g.drawString(this.font, "§l" + titleLine, titleStartX, cardY + 5, 0xFFFFFFFF, isExpanded);

                String artistLine = ellipsize(item.set.getArtist(), maxMainW);
                g.drawString(this.font, artistLine, titleStartX, cardY + 16, 0xFF99AABB, false);

            } else {
                // ==================== SUB-CARD DE DIFICULTAD (HIJO DESPLEGADO) ====================
                Beatmap diff = item.difficulty;
                boolean isSelected = (this.selectedBeatmap != null && diff.getId().equals(this.selectedBeatmap.getId()));

                // Sangría de 18px para denotar jerarquía dentro de la canción
                int indent = 18;
                int cardX = listStartX + indent;
                int cardW = listInnerWidth - indent;

                int bgColor = isSelected ? 0xF0183B55 : (isHovered ? 0xD0182230 : 0xA00F141C);
                int borderColor = isSelected ? 0xFF00E5FF : (isHovered ? 0xFF2F435A : 0xFF1B2330);

                float stars = diff.getEstimatedStarRating();
                int diffColor = getDifficultyColor(stars);

                g.fill(cardX, cardY, cardX + cardW, cardY + item.height, bgColor);
                drawBorder(g, cardX, cardY, cardW, item.height, borderColor);

                // Barra vertical de dificultad
                g.fill(cardX, cardY, cardX + 3, cardY + item.height, diffColor);

                // Estrellas y Modo
                String starStr = String.format(Locale.ROOT, "★%.1f", stars);
                g.drawString(this.font, starStr, cardX + 7, cardY + 4, diffColor, true);
                g.drawString(this.font, diff.getKeyCount() + "K", cardX + 7, cardY + 13, 0xFF6688AA, false);

                int rightCursor = cardX + cardW - 6;

                // Récord si existe
                VSRCraftSettings.BeatmapRecord rec = VSRCraftSettings.get().getPersonalBest(diff.getId());
                if (rec != null && rec.bestScore > 0) {
                    String rankStr = "§l" + rec.bestRank;
                    int rw = this.font.width(rankStr);
                    rightCursor -= rw;
                    g.drawString(this.font, rankStr, rightCursor, cardY + 7, getRankColor(rec.bestRank), true);
                    rightCursor -= 6;
                }

                String durText = diff.getFormattedDuration();
                int durW = this.font.width(durText);
                rightCursor -= durW;
                g.drawString(this.font, durText, rightCursor, cardY + 4, 0xFF8899AA, false);

                String notesText = diff.getNotes().size() + "n";
                int notesW = this.font.width(notesText);
                g.drawString(this.font, notesText, rightCursor + durW - notesW, cardY + 13, 0xFF556677, false);
                rightCursor -= 8;

                // Nombre de la versión / dificultad
                int nameStartX = cardX + 38;
                int maxNameW = Math.max(30, rightCursor - nameStartX);
                String versionText = ellipsize("[" + diff.getVersion() + "]", maxNameW);
                int vColor = isSelected ? 0xFF00E5FF : 0xFFDDEEFF;
                g.drawString(this.font, isSelected ? "§l" + versionText : versionText, nameStartX, cardY + 7, vColor, isSelected);
            }
        }

        g.disableScissor();

        // Barra de desplazamiento (Scrollbar)
        if (this.totalContentHeight > listHeight) {
            int trackX = listStartX + listWidth - 5;
            int trackY = listStartY + 2;
            int trackH = listHeight - 4;
            g.fill(trackX, trackY, trackX + 3, trackY + trackH, 0x66000000);

            int thumbH = Math.max(18, (int) ((listHeight / (double) this.totalContentHeight) * trackH));
            int maxScroll = this.totalContentHeight - listHeight;
            int thumbY = trackY + (int) ((this.scrollOffset / maxScroll) * (trackH - thumbH));
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFF00D4FF);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (event.button() == 0 && this.recordBoxClickable
                && mouseX >= this.recordBoxX && mouseX <= this.recordBoxX + this.recordBoxW
                && mouseY >= this.recordBoxY && mouseY <= this.recordBoxY + this.recordBoxH) {
            openSelectedResults();
            return true;
        }

        if (mouseX >= listStartX && mouseX <= listStartX + listWidth
                && mouseY >= listStartY && mouseY <= listStartY + listHeight) {

            int scrollPx = (int) Math.round(this.scrollOffset);
            int clickedY = (int) mouseY - (listStartY + 3) + scrollPx;

            for (int i = 0; i < this.visibleItems.size(); i++) {
                ListItem item = this.visibleItems.get(i);
                if (clickedY >= item.yTop && clickedY <= item.yTop + item.height) {
                    long now = System.currentTimeMillis();
                    boolean isDoubleClick = (i == this.lastClickedItemIndex && (now - this.lastClickTimeMs) < 400L);
                    this.lastClickedItemIndex = i;
                    this.lastClickTimeMs = now;

                    if (this.searchBox != null) {
                        this.searchBox.setFocused(false);
                    }

                    if (item.isSetHeader) {
                        // Clic en Card de Canción (Set): Alternar despliegue
                        if (item.set.getId().equals(this.expandedSetId)) {
                            // Si ya estaba expandido y es doble clic, jugar
                            if (isDoubleClick && this.selectedBeatmap != null) {
                                startSelectedBeatmap();
                                return true;
                            }
                        } else {
                            this.expandedSetId = item.set.getId();
                            this.selectedBeatmap = item.set.getDefaultBeatmap();
                            rebuildVisibleList();
                            triggerPreviewForSelected();
                            ensureIndexVisible(i);
                        }
                    } else {
                        // Clic en Sub-Card de Dificultad
                        this.selectedBeatmap = item.difficulty;
                        this.selectedVisibleIndex = i;
                        triggerPreviewForSelected();

                        if (isDoubleClick) {
                            startSelectedBeatmap();
                        }
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.visibleItems.isEmpty()) {
            this.targetScrollOffset -= scrollY * 36.0;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.transition.isExiting()) return true;
        int key = event.key();

        if (key == GLFW.GLFW_KEY_UP) {
            navigateVisibleItems(-1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            navigateVisibleItems(1);
            return true;
        }

        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.selectedBeatmap != null) {
                startSelectedBeatmap();
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_F1) {
            watchSelectedReplay();
            return true;
        }

        boolean searchFocused = this.searchBox != null && this.searchBox.isFocused();
        if (!searchFocused) {
            if (key == GLFW.GLFW_KEY_LEFT) {
                navigateBeatmapSets(-1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT) {
                navigateBeatmapSets(1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_O) {
                openSettings();
                return true;
            }
            if (key == GLFW.GLFW_KEY_F3) {
                VSRCraftSettings.get().scrollSpeed = Math.max(0.2f, VSRCraftSettings.get().scrollSpeed - 0.05f);
                VSRCraftSettings.save();
                showStatusMessage(String.format(Locale.ROOT, "Scroll Speed: %.2fx", VSRCraftSettings.get().scrollSpeed), 0xFFBA68C8);
                return true;
            }
            if (key == GLFW.GLFW_KEY_F4) {
                VSRCraftSettings.get().scrollSpeed = Math.min(1.5f, VSRCraftSettings.get().scrollSpeed + 0.05f);
                VSRCraftSettings.save();
                showStatusMessage(String.format(Locale.ROOT, "Scroll Speed: %.2fx", VSRCraftSettings.get().scrollSpeed), 0xFFBA68C8);
                return true;
            }
            if (key == GLFW.GLFW_KEY_F5) {
                rememberedPlaybackRate = Math.max(0.50f, Math.round((rememberedPlaybackRate - 0.05f) * 20.0f) / 20.0f);
                showStatusMessage(String.format(Locale.ROOT, "Playback Rate: %.2fx", rememberedPlaybackRate), 0xFF55E5FF);
                return true;
            }
            if (key == GLFW.GLFW_KEY_F6) {
                rememberedPlaybackRate = Math.min(2.00f, Math.round((rememberedPlaybackRate + 0.05f) * 20.0f) / 20.0f);
                showStatusMessage(String.format(Locale.ROOT, "Playback Rate: %.2fx", rememberedPlaybackRate), 0xFF55E5FF);
                return true;
            }
        }

        return super.keyPressed(event);
    }

    private void navigateBeatmapSets(int delta) {
        if (this.filteredSets.isEmpty()) return;
        int currentSetIdx = 0;
        for (int i = 0; i < this.filteredSets.size(); i++) {
            if (this.filteredSets.get(i).getId().equals(this.expandedSetId)) {
                currentSetIdx = i;
                break;
            }
        }
        int nextSetIdx = (currentSetIdx + delta + this.filteredSets.size()) % this.filteredSets.size();
        BeatmapSet nextSet = this.filteredSets.get(nextSetIdx);
        this.expandedSetId = nextSet.getId();
        this.selectedBeatmap = nextSet.getDefaultBeatmap();
        rebuildVisibleList();
        ensureIndexVisible(this.selectedVisibleIndex);
        triggerPreviewForSelected();
        saveSelectionState();
    }

    private void navigateVisibleItems(int delta) {
        if (this.visibleItems.isEmpty()) return;
        int newIdx = Math.max(0, Math.min(this.visibleItems.size() - 1, this.selectedVisibleIndex + delta));
        if (newIdx == this.selectedVisibleIndex) return;

        this.selectedVisibleIndex = newIdx;
        ListItem item = this.visibleItems.get(newIdx);

        if (item.isSetHeader) {
            this.expandedSetId = item.set.getId();
            this.selectedBeatmap = item.set.getDefaultBeatmap();
            rebuildVisibleList();
            triggerPreviewForSelected();
        } else {
            this.selectedBeatmap = item.difficulty;
            triggerPreviewForSelected();
        }

        ensureIndexVisible(this.selectedVisibleIndex);
    }

    private String ellipsize(String text, int maxWidth) {
        if (text == null) return "";
        if (this.font.width(text) <= maxWidth) return text;
        String dots = "...";
        int dotsW = this.font.width(dots);
        String trimmed = this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - dotsW));
        return trimmed + dots;
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int getDifficultyColor(float stars) {
        return OsuDifficultyColor.getSpectrumColor(stars);
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

    @Override
    public void onClose() {
        saveSelectionState();
        AudioManager.stopMusic();
        super.onClose();
    }

    @Override
    public void removed() {
        super.removed();
        if (!this.transitioningToSubScreen) {
            AudioManager.stopMusic();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
