package com.vsrcraft.client.gui;

import com.vsrcraft.client.audio.AudioManager;
import com.vsrcraft.client.config.VSRCraftSettings;
import com.vsrcraft.client.domain.Beatmap;
import com.vsrcraft.client.domain.BeatmapRepository;
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
 * Pantalla de Selección de Canciones (Song Select) estilo osu!mania con distribución de 2 columnas:
 * - Columna Izquierda: Ficha técnica detallada del beatmap, estadísticas, récord personal y botón JUGAR.
 * - Columna Derecha: Barra de búsqueda en tiempo real y lista desplazable de rectángulos ultra-compactos.
 */
public class SongSelectScreen extends Screen {

    // Alturas compactas para que entren la mayor cantidad posible de canciones en pantalla
    private static final int CARD_HEIGHT = 26;
    private static final int CARD_GAP = 3;
    private static final int CARD_STEP = CARD_HEIGHT + CARD_GAP;

    private List<Beatmap> allBeatmaps = new ArrayList<>();
    private List<Beatmap> filteredBeatmaps = new ArrayList<>();
    private int selectedIndex = 0;
    private String currentSearchQuery = "";

    // Scroll suave para la columna derecha
    private double scrollOffset = 0.0;
    private double targetScrollOffset = 0.0;

    // Detección de doble clic
    private long lastClickTimeMs = 0L;
    private int lastClickedIndex = -1;

    // Coordenadas de layout calculadas dinámicamente
    private int leftPanelX, leftPanelY, leftPanelW, leftPanelH;
    private int rightPanelX, rightPanelY, rightPanelW, rightPanelH;
    private int listStartX, listStartY, listWidth, listHeight;

    private EditBox searchBox;
    private Button playButton;
    private boolean transitioningToSubScreen = false;

    public SongSelectScreen() {
        this(null);
    }

    public SongSelectScreen(Beatmap preselectedBeatmap) {
        super(Component.literal("VSRCraft - Selector de Canciones"));
        this.allBeatmaps = BeatmapRepository.getBeatmaps();
        this.filteredBeatmaps = new ArrayList<>(this.allBeatmaps);

        if (preselectedBeatmap != null) {
            for (int i = 0; i < filteredBeatmaps.size(); i++) {
                if (filteredBeatmaps.get(i).getId().equals(preselectedBeatmap.getId())) {
                    this.selectedIndex = i;
                    break;
                }
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        this.transitioningToSubScreen = false;

        int margin = 10;
        int topBarH = 28;
        int bottomBarH = 18;
        int contentY = topBarH + 6;
        int contentH = this.height - contentY - bottomBarH - 4;

        // Distribución de 2 columnas: ~42% izquierda (ficha detallada), ~58% derecha (lista compacta)
        this.leftPanelX = margin;
        this.leftPanelY = contentY;
        this.leftPanelW = Math.max(175, Math.min(250, (int) ((this.width - margin * 3) * 0.42f)));
        this.leftPanelH = contentH;

        this.rightPanelX = this.leftPanelX + this.leftPanelW + margin;
        this.rightPanelY = contentY;
        this.rightPanelW = this.width - this.rightPanelX - margin;
        this.rightPanelH = contentH;

        // 1. Botón [< Salir] arriba a la izquierda
        this.addRenderableWidget(Button.builder(
                Component.literal("< Salir"),
                btn -> this.onClose()
        ).bounds(margin, 4, 54, 20).build());

        // 2. Botón [⚙] Ajustes arriba a la derecha
        int gearX = this.width - margin - 24;
        this.addRenderableWidget(Button.builder(
                Component.literal("⚙"),
                btn -> openSettings()
        )
        .bounds(gearX, 4, 24, 20)
        .tooltip(Tooltip.create(Component.literal("Ajustes y Personalización (Tecla O)")))
        .build());

        // 3. Botón [↻] Recargar beatmaps (al lado de Ajustes)
        int reloadX = gearX - 28;
        this.addRenderableWidget(Button.builder(
                Component.literal("↻"),
                btn -> reloadCatalog()
        )
        .bounds(reloadX, 4, 24, 20)
        .tooltip(Tooltip.create(Component.literal("Recargar canciones (.minecraft/vsrg_beatmaps)")))
        .build());

        // 4. Barra de búsqueda en la parte superior de la columna derecha
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
        this.searchBox.setHint(Component.literal("§7🔍 Buscar por título, artista, mapper o dificultad..."));
        this.searchBox.setValue(this.currentSearchQuery);
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);

        // Área de la lista de tarjetas compactas debajo del buscador
        this.listStartX = this.rightPanelX;
        this.listStartY = this.rightPanelY + searchH + 5;
        this.listWidth = this.rightPanelW;
        this.listHeight = Math.max(40, this.rightPanelH - searchH - 5);

        // 5. Botón principal [▶ JUGAR (ENTER)] abajo en la columna izquierda
        int playBtnW = this.leftPanelW - 16;
        int playBtnX = this.leftPanelX + 8;
        int playBtnY = this.leftPanelY + this.leftPanelH - 28;
        this.playButton = Button.builder(
                Component.literal("§b§l▶ JUGAR BEATMAP"),
                btn -> startSelectedBeatmap()
        ).bounds(playBtnX, playBtnY, playBtnW, 20).build();
        this.playButton.active = !this.filteredBeatmaps.isEmpty();
        this.addRenderableWidget(this.playButton);

        ensureSelectedVisible();
        triggerPreviewForSelected();
    }

    private void onSearchChanged(String query) {
        this.currentSearchQuery = query;
        Beatmap previouslySelected = getSelectedBeatmap();

        this.filteredBeatmaps.clear();
        for (Beatmap map : this.allBeatmaps) {
            if (map.matchesQuery(query)) {
                this.filteredBeatmaps.add(map);
            }
        }

        if (this.filteredBeatmaps.isEmpty()) {
            this.selectedIndex = -1;
        } else {
            int newIdx = 0;
            if (previouslySelected != null) {
                for (int i = 0; i < this.filteredBeatmaps.size(); i++) {
                    if (this.filteredBeatmaps.get(i).getId().equals(previouslySelected.getId())) {
                        newIdx = i;
                        break;
                    }
                }
            }
            this.selectedIndex = newIdx;
        }

        if (this.playButton != null) {
            this.playButton.active = !this.filteredBeatmaps.isEmpty();
        }
        clampScroll();
        ensureSelectedVisible();
        triggerPreviewForSelected();
    }

    private void reloadCatalog() {
        BeatmapRepository.reload();
        this.allBeatmaps = BeatmapRepository.getBeatmaps();
        onSearchChanged(this.currentSearchQuery);
    }

    private void openSettings() {
        this.transitioningToSubScreen = true;
        if (this.minecraft != null) {
            this.minecraft.setScreen(new VSRSettingsScreen(this));
        }
    }

    private void startSelectedBeatmap() {
        Beatmap selected = getSelectedBeatmap();
        if (selected == null || this.minecraft == null) return;
        this.transitioningToSubScreen = true;
        AudioManager.stopMusic();
        this.minecraft.setScreen(new RhythmGameScreen(selected));
    }

    private Beatmap getSelectedBeatmap() {
        if (this.selectedIndex >= 0 && this.selectedIndex < this.filteredBeatmaps.size()) {
            return this.filteredBeatmaps.get(this.selectedIndex);
        }
        return null;
    }

    private void selectBeatmapAtIndex(int index) {
        if (this.filteredBeatmaps.isEmpty()) return;
        int clamped = Math.max(0, Math.min(this.filteredBeatmaps.size() - 1, index));
        if (clamped != this.selectedIndex) {
            this.selectedIndex = clamped;
            ensureSelectedVisible();
            triggerPreviewForSelected();
        }
    }

    private void triggerPreviewForSelected() {
        Beatmap selected = getSelectedBeatmap();
        if (selected != null && selected.getAudioResourcePath() != null && !selected.getAudioResourcePath().isEmpty()) {
            AudioManager.requestPreview(selected.getAudioResourcePath(), selected.getPreviewTimeMs());
        } else {
            AudioManager.stopMusic();
        }
    }

    private void ensureSelectedVisible() {
        if (this.selectedIndex < 0 || this.listHeight <= 0) return;
        int itemTop = this.selectedIndex * CARD_STEP;
        int itemBottom = itemTop + CARD_HEIGHT;

        if (itemTop < this.targetScrollOffset) {
            this.targetScrollOffset = itemTop;
        } else if (itemBottom > this.targetScrollOffset + this.listHeight) {
            this.targetScrollOffset = itemBottom - this.listHeight;
        }
        clampScroll();
    }

    private void clampScroll() {
        int totalContentHeight = Math.max(0, this.filteredBeatmaps.size() * CARD_STEP - CARD_GAP);
        int maxScroll = Math.max(0, totalContentHeight - this.listHeight);
        this.targetScrollOffset = Math.max(0.0, Math.min(maxScroll, this.targetScrollOffset));
        this.scrollOffset = Math.max(0.0, Math.min(maxScroll, this.scrollOffset));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Actualizar preview de audio asíncrono si ya terminó de decodificarse
        AudioManager.updatePreview();

        // Interpolación suave del scroll
        this.scrollOffset += (this.targetScrollOffset - this.scrollOffset) * 0.32;
        if (Math.abs(this.targetScrollOffset - this.scrollOffset) < 0.2) {
            this.scrollOffset = this.targetScrollOffset;
        }

        // 1. Fondo general estilizado
        g.fillGradient(0, 0, this.width, this.height, 0xE60B0E14, 0xF206080C);

        // 2. Barra Superior (Header)
        g.fill(0, 0, this.width, 28, 0xCC111622);
        g.fill(0, 27, this.width, 28, 0xFF00D4FF); // Línea de acento cian
        g.drawString(this.font, "§b§lVSRCRAFT §8// §fSELECTOR DE CANCIONES", 72, 10, 0xFFFFFFFF, true);

        String countBadge = this.filteredBeatmaps.size() + (this.filteredBeatmaps.size() == 1 ? " mapa" : " mapas");
        int countW = this.font.width(countBadge);
        g.drawString(this.font, countBadge, this.width - 74 - countW, 10, 0xFF88AABB, false);

        // 3. Renderizar Columna Izquierda (Detalles del Beatmap Seleccionado)
        renderLeftDetailsColumn(g);

        // 4. Renderizar Columna Derecha (Lista de Tarjetas Compactas)
        renderRightSongListColumn(g, mouseX, mouseY);

        // 5. Barra Inferior (Atajos)
        g.fill(0, this.height - 18, this.width, this.height, 0xCC0D111A);
        g.fill(0, this.height - 18, this.width, this.height - 17, 0x44FFFFFF);
        String footer = String.format(Locale.ROOT,
                "↑/↓: Navegar  |  ENTER / Doble Clic: Jugar  |  Scroll: %.2fx (F3/F4)  |  Offset: %+dms",
                VSRCraftSettings.get().scrollSpeed, VSRCraftSettings.get().globalAudioOffsetMs);
        g.drawCenteredString(this.font, footer, this.width / 2, this.height - 13, 0xFF99AABB);

        // 6. Widgets (Botones y EditBox)
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderLeftDetailsColumn(GuiGraphics g) {
        // Contenedor principal izquierdo
        g.fill(leftPanelX, leftPanelY, leftPanelX + leftPanelW, leftPanelY + leftPanelH, 0xD8121722);
        drawBorder(g, leftPanelX, leftPanelY, leftPanelW, leftPanelH, 0xFF26334D);

        Beatmap map = getSelectedBeatmap();
        if (map == null) {
            g.drawCenteredString(this.font, "Sin canciones seleccionadas", leftPanelX + leftPanelW / 2, leftPanelY + leftPanelH / 2 - 5, 0xFF888888);
            return;
        }

        float stars = map.getEstimatedStarRating();
        int diffColor = getDifficultyColor(stars);

        // Banner superior de la tarjeta izquierda
        int bannerH = 58;
        g.fillGradient(leftPanelX + 1, leftPanelY + 1, leftPanelX + leftPanelW - 1, leftPanelY + bannerH, 0xFF1A273B, 0xFF121722);
        // Franja superior del color de dificultad
        g.fill(leftPanelX + 1, leftPanelY + 1, leftPanelX + leftPanelW - 1, leftPanelY + 3, diffColor);

        int innerX = leftPanelX + 8;
        int maxTextW = leftPanelW - 16;

        // Fila 1 del Banner: Modo [4K] + Estrellas ★
        String modeTag = "[" + map.getKeyCount() + "K MANIA]";
        g.drawString(this.font, modeTag, innerX, leftPanelY + 7, 0xFF00E5FF, false);

        String starTag = String.format(Locale.ROOT, "★ %.2f", stars);
        int starW = this.font.width(starTag);
        g.drawString(this.font, starTag, leftPanelX + leftPanelW - 8 - starW, leftPanelY + 7, diffColor, true);

        // Fila 2 del Banner: Título de la canción
        String title = ellipsize(map.getTitle(), maxTextW);
        g.drawString(this.font, "§l" + title, innerX, leftPanelY + 20, 0xFFFFFFFF, true);

        // Fila 3 del Banner: Artista
        String artist = ellipsize(map.getArtist(), maxTextW);
        g.drawString(this.font, artist, innerX, leftPanelY + 32, 0xFF99CCFF, false);

        // Fila 4 del Banner: Dificultad (Version) y Mapper (Creator)
        String versionAndMapper = ellipsize("[" + map.getVersion() + "] • por " + map.getCreator(), maxTextW);
        g.drawString(this.font, versionAndMapper, innerX, leftPanelY + 44, 0xFFAAAAAA, false);

        // Línea divisoria
        int yCursor = leftPanelY + bannerH + 2;
        g.fill(innerX, yCursor, leftPanelX + leftPanelW - 8, yCursor + 1, 0x44FFFFFF);
        yCursor += 6;

        // Cuadrícula compacta de Estadísticas (2 columnas x 3 filas)
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

        // Caja de Récord Personal (si hay espacio suficiente antes del botón JUGAR)
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

                String scoreLine = String.format(Locale.ROOT, "Score: %07d", record.bestScore);
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
        // Fondo del área de lista
        g.fill(listStartX, listStartY, listStartX + listWidth, listStartY + listHeight, 0x80090C12);
        drawBorder(g, listStartX, listStartY, listWidth, listHeight, 0xFF1E293B);

        if (this.filteredBeatmaps.isEmpty()) {
            g.drawCenteredString(this.font, "No se encontraron canciones para \"" + this.currentSearchQuery + "\"",
                    listStartX + listWidth / 2, listStartY + listHeight / 2 - 4, 0xFF888899);
            return;
        }

        g.enableScissor(listStartX + 1, listStartY + 1, listStartX + listWidth - 1, listStartY + listHeight - 1);

        int scrollPx = (int) Math.round(this.scrollOffset);
        int scrollbarWidth = 5;
        int cardAreaWidth = listWidth - scrollbarWidth - 6;

        for (int i = 0; i < this.filteredBeatmaps.size(); i++) {
            int cardY = listStartY + 3 + i * CARD_STEP - scrollPx;
            if (cardY + CARD_HEIGHT < listStartY || cardY > listStartY + listHeight) {
                continue; // Fuera del área visible
            }

            Beatmap map = this.filteredBeatmaps.get(i);
            boolean isSelected = (i == this.selectedIndex);
            boolean isHovered = mouseX >= listStartX && mouseX <= listStartX + cardAreaWidth
                    && mouseY >= Math.max(listStartY, cardY) && mouseY <= Math.min(listStartY + listHeight, cardY + CARD_HEIGHT);

            // Efecto sutil de desplazamiento lateral al estar seleccionada (estilo osu!)
            int indent = isSelected ? 2 : (isHovered ? 4 : 6);
            int cardX = listStartX + indent;
            int cardW = cardAreaWidth - indent + 2;

            int bgColor = isSelected ? 0xE6132F44 : (isHovered ? 0xCC1C2636 : 0xB8131822);
            int borderColor = isSelected ? 0xFF00E5FF : (isHovered ? 0xFF3B5270 : 0xFF20293A);
            float stars = map.getEstimatedStarRating();
            int diffColor = getDifficultyColor(stars);

            // Fondo y borde del rectángulo compacto
            g.fill(cardX, cardY, cardX + cardW, cardY + CARD_HEIGHT, bgColor);
            drawBorder(g, cardX, cardY, cardW, CARD_HEIGHT, borderColor);

            // Barra vertical izquierda indicadora de dificultad (3px)
            g.fill(cardX, cardY, cardX + 3, cardY + CARD_HEIGHT, diffColor);

            // Badge compacto de Estrellas + Modo a la izquierda
            String badge = String.format(Locale.ROOT, "★%.1f", stars);
            g.drawString(this.font, badge, cardX + 7, cardY + 5, diffColor, true);
            g.drawString(this.font, map.getKeyCount() + "K", cardX + 7, cardY + 15, 0xFF7799BB, false);

            int textStartX = cardX + 36;

            // Lado derecho de la tarjeta: Récord Rank (si existe) + Duración + [Dificultad]
            int rightCursor = cardX + cardW - 6;

            VSRCraftSettings.BeatmapRecord rec = VSRCraftSettings.get().getPersonalBest(map.getId());
            if (rec != null && rec.bestScore > 0) {
                String rankStr = "§l" + rec.bestRank;
                int rw = this.font.width(rankStr);
                rightCursor -= rw;
                g.drawString(this.font, rankStr, rightCursor, cardY + 9, getRankColor(rec.bestRank), true);
                rightCursor -= 6;
            }

            String durText = map.getFormattedDuration();
            int durW = this.font.width(durText);
            rightCursor -= durW;
            g.drawString(this.font, durText, rightCursor, cardY + 5, 0xFF99AABB, false);

            String notesText = map.getNotes().size() + "n";
            int notesW = this.font.width(notesText);
            g.drawString(this.font, notesText, rightCursor + durW - notesW, cardY + 15, 0xFF667788, false);
            rightCursor -= 8;

            // Título y Artista en la línea superior de la tarjeta, Dificultad [Version] en la inferior
            int availableMainW = Math.max(40, rightCursor - textStartX);
            String titleLine = ellipsize(map.getTitle(), availableMainW);
            int titleColor = isSelected ? 0xFFFFFFFF : 0xFFE2E8F0;
            g.drawString(this.font, isSelected ? "§l" + titleLine : titleLine, textStartX, cardY + 4, titleColor, isSelected);

            String subLine = ellipsize(map.getArtist() + "  [" + map.getVersion() + "]", availableMainW);
            int subColor = isSelected ? 0xFF80DEEA : 0xFF8899AA;
            g.drawString(this.font, subLine, textStartX, cardY + 15, subColor, false);
        }

        g.disableScissor();

        // Barra de desplazamiento (Scrollbar) a la derecha
        int totalContentHeight = Math.max(0, this.filteredBeatmaps.size() * CARD_STEP - CARD_GAP);
        if (totalContentHeight > listHeight) {
            int trackX = listStartX + listWidth - 5;
            int trackY = listStartY + 2;
            int trackH = listHeight - 4;
            g.fill(trackX, trackY, trackX + 3, trackY + trackH, 0x66000000);

            int thumbH = Math.max(18, (int) ((listHeight / (double) totalContentHeight) * trackH));
            int maxScroll = totalContentHeight - listHeight;
            int thumbY = trackY + (int) ((this.scrollOffset / maxScroll) * (trackH - thumbH));
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFF00D4FF);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = event.x();
        double mouseY = event.y();

        // Verificar si se hizo clic dentro de la lista de canciones
        if (mouseX >= listStartX && mouseX <= listStartX + listWidth
                && mouseY >= listStartY && mouseY <= listStartY + listHeight) {

            int scrollPx = (int) Math.round(this.scrollOffset);
            int relativeY = (int) mouseY - (listStartY + 3) + scrollPx;
            if (relativeY >= 0) {
                int clickedIndex = relativeY / CARD_STEP;
                int offsetWithinCard = relativeY % CARD_STEP;

                if (offsetWithinCard <= CARD_HEIGHT && clickedIndex >= 0 && clickedIndex < this.filteredBeatmaps.size()) {
                    long now = System.currentTimeMillis();
                    boolean isDoubleClick = (clickedIndex == this.lastClickedIndex && (now - this.lastClickTimeMs) < 400L);
                    this.lastClickedIndex = clickedIndex;
                    this.lastClickTimeMs = now;

                    if (this.searchBox != null) {
                        this.searchBox.setFocused(false);
                    }

                    if (clickedIndex == this.selectedIndex && isDoubleClick) {
                        startSelectedBeatmap();
                    } else {
                        selectBeatmapAtIndex(clickedIndex);
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.filteredBeatmaps.isEmpty()) {
            this.targetScrollOffset -= scrollY * (CARD_STEP * 1.5);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        // Navegación con flechas Arriba / Abajo siempre disponible
        if (key == GLFW.GLFW_KEY_UP) {
            selectBeatmapAtIndex(this.selectedIndex - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            selectBeatmapAtIndex(this.selectedIndex + 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP) {
            selectBeatmapAtIndex(this.selectedIndex - 5);
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN) {
            selectBeatmapAtIndex(this.selectedIndex + 5);
            return true;
        }

        // ENTER para jugar la canción seleccionada
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (!this.filteredBeatmaps.isEmpty()) {
                startSelectedBeatmap();
                return true;
            }
        }

        // Atajos rápidos de velocidad y offset cuando el cuadro de búsqueda no tiene el foco
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
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int getDifficultyColor(float stars) {
        if (stars < 2.0f) return 0xFF66BB6A; // Easy (Verde)
        if (stars < 2.8f) return 0xFF29B6F6; // Normal (Cian)
        if (stars < 4.0f) return 0xFFFFCA28; // Hard (Ámbar/Oro)
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
