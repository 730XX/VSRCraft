package com.vsrcraft.client.gui;

import com.vsrcraft.client.audio.AudioManager;
import com.vsrcraft.client.config.VSRCraftSettings;
import com.vsrcraft.client.domain.Beatmap;
import com.vsrcraft.client.domain.BeatmapRepository;
import com.vsrcraft.client.domain.BeatmapSet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

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

        // 5. Botón principal [▶ JUGAR (ENTER)]
        int playBtnW = this.leftPanelW - 16;
        int playBtnX = this.leftPanelX + 8;
        int playBtnY = this.leftPanelY + this.leftPanelH - 28;
        this.playButton = Button.builder(
                Component.literal("§b§l▶ JUGAR BEATMAP"),
                btn -> startSelectedBeatmap()
        ).bounds(playBtnX, playBtnY, playBtnW, 20).build();
        this.addRenderableWidget(this.playButton);

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
        this.transition.startExit(240L, () -> this.minecraft.setScreen(new RhythmGameScreen(targetMap)));
    }

    private void triggerPreviewForSelected() {
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
        renderLeftDetailsColumn(g);

        // 4. Renderizar Columna Derecha (Acordeón de canciones y dificultades)
        renderRightSongListColumn(g, mouseX, mouseY);

        // 5. Barra Inferior (Atajos)
        g.fill(0, this.height - 18, this.width, this.height, 0xCC0D111A);
        g.fill(0, this.height - 18, this.width, this.height - 17, 0x44FFFFFF);
        String footer = String.format(Locale.ROOT,
                "Clic: Desplegar/Elegir  |  ENTER / Doble Clic: Jugar  |  Scroll: %.2fx (F3/F4)  |  Offset: %+dms",
                VSRCraftSettings.get().scrollSpeed, VSRCraftSettings.get().globalAudioOffsetMs);
        g.drawCenteredString(this.font, footer, this.width / 2, this.height - 13, 0xFF99AABB);

        // 6. Widgets
        super.render(g, mouseX, mouseY, partialTick);

        // 7. Transición suave (Fade-In / Fade-Out)
        this.transition.renderOverlay(g, this.width, this.height);
    }

    private void renderLeftDetailsColumn(GuiGraphics g) {
        g.fill(leftPanelX, leftPanelY, leftPanelX + leftPanelW, leftPanelY + leftPanelH, 0xD8121722);
        drawBorder(g, leftPanelX, leftPanelY, leftPanelW, leftPanelH, 0xFF26334D);

        Beatmap map = this.selectedBeatmap;
        if (map == null) {
            g.drawCenteredString(this.font, "Sin canciones seleccionadas", leftPanelX + leftPanelW / 2, leftPanelY + leftPanelH / 2 - 5, 0xFF888888);
            return;
        }

        float stars = map.getEstimatedStarRating();
        int diffColor = getDifficultyColor(stars);

        // Banner superior
        int bannerH = 58;
        g.fillGradient(leftPanelX + 1, leftPanelY + 1, leftPanelX + leftPanelW - 1, leftPanelY + bannerH, 0xFF1A273B, 0xFF121722);
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

        // Récord Personal
        int playBtnTop = leftPanelY + leftPanelH - 32;
        int recordBoxH = playBtnTop - yCursor - 2;
        if (recordBoxH >= 32) {
            g.fill(innerX, yCursor, innerX + maxTextW, yCursor + recordBoxH, 0x990B0E17);
            drawBorder(g, innerX, yCursor, maxTextW, recordBoxH, 0xFF1F2B40);

            VSRCraftSettings.BeatmapRecord record = VSRCraftSettings.get().getPersonalBest(map.getId());
            g.drawString(this.font, "§7RÉCORD PERSONAL", innerX + 6, yCursor + 5, 0xFF8899AA, false);

            if (record != null && record.bestScore > 0) {
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
            } else {
                g.drawString(this.font, "Sin récord registrado aún", innerX + 6, yCursor + 18, 0xFF666677, false);
            }
        }
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

                g.fill(cardX, cardY, cardX + cardW, cardY + item.height, bgColor);
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

        boolean searchFocused = this.searchBox != null && this.searchBox.isFocused();
        if (!searchFocused) {
            if (key == GLFW.GLFW_KEY_O) {
                openSettings();
                return true;
            }
            if (key == GLFW.GLFW_KEY_F3) {
                VSRCraftSettings.get().scrollSpeed = Math.max(0.2f, VSRCraftSettings.get().scrollSpeed - 0.05f);
                VSRCraftSettings.save();
                return true;
            }
            if (key == GLFW.GLFW_KEY_F4) {
                VSRCraftSettings.get().scrollSpeed = Math.min(1.5f, VSRCraftSettings.get().scrollSpeed + 0.05f);
                VSRCraftSettings.save();
                return true;
            }
        }

        return super.keyPressed(event);
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
        if (stars < 2.0f) return 0xFF66BB6A; // Easy (Verde)
        if (stars < 2.8f) return 0xFF29B6F6; // Normal (Cian)
        if (stars < 4.0f) return 0xFFFFCA28; // Hard (Ámbar)
        if (stars < 5.3f) return 0xFFFF5252; // Insane (Rojo/Coral)
        return 0xFFAB47BC;                   // Expert+ (Púrpura)
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
