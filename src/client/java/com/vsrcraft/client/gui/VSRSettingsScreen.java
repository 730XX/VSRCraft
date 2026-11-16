package com.vsrcraft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.vsrcraft.client.audio.AudioManager;
import com.vsrcraft.client.config.KeybindConfig;
import com.vsrcraft.client.config.LaneTheme;
import com.vsrcraft.client.config.RhythmConfig;
import com.vsrcraft.client.config.VSRCraftSettings;
import com.vsrcraft.client.domain.Note;
import com.vsrcraft.client.gui.renderer.NoteShapeRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pantalla de Configuración y Personalización de VSRCraft:
 * - Organización limpia en 4 Pestañas: 🎧 Audio, 🎮 Gameplay, 🎨 Visual, ⌨ Teclas.
 * - Live Preview en tiempo real en la columna derecha que reacciona a los sliders y pulsaciones de teclas.
 */
public class VSRSettingsScreen extends Screen {

    public enum Tab {
        AUDIO("🎧 Audio"),
        GAMEPLAY("🎮 Gameplay"),
        VISUAL("🎨 Visual"),
        KEYBINDS("⌨ Teclas");

        final String label;
        Tab(String label) { this.label = label; }
    }

    private final Screen parentScreen;
    private Tab currentTab = Tab.AUDIO;
    private final ScreenTransition transition = new ScreenTransition(220L);

    // Estado de reasignación de teclas
    private int listeningLane = -1;
    private int editingKeyMode = 4; // 4K o 7K

    // Estado del Previsualizador en Vivo (Live Preview)
    private final boolean[] previewPressed = new boolean[7];
    private final List<Note> previewNotes4K = new ArrayList<>();
    private final List<Note> previewNotes7K = new ArrayList<>();
    private long previewLoopStart = 0L;
    private static final long LOOP_DURATION_MS = 2800L;

    // Layout
    private int leftPanelX, leftPanelY, leftPanelW, leftPanelH;
    private int rightPanelX, rightPanelY, rightPanelW, rightPanelH;

    public VSRSettingsScreen(Screen parentScreen) {
        super(Component.literal("Configuración de VSRCraft"));
        this.parentScreen = parentScreen;
        initPreviewNotes();
    }

    private void initPreviewNotes() {
        this.previewLoopStart = System.currentTimeMillis();

        // Notas de demostración para 4K en bucle continuo
        this.previewNotes4K.clear();
        this.previewNotes4K.add(new Note(0, 300));
        this.previewNotes4K.add(new Note(1, 600));
        this.previewNotes4K.add(new Note(2, 900));
        this.previewNotes4K.add(new Note(3, 1200));
        this.previewNotes4K.add(new Note(1, 1500, 700, Note.Type.HOLD));
        this.previewNotes4K.add(new Note(2, 1700));
        this.previewNotes4K.add(new Note(0, 2400));
        this.previewNotes4K.add(new Note(3, 2400));

        // Notas de demostración para 7K
        this.previewNotes7K.clear();
        this.previewNotes7K.add(new Note(0, 300));
        this.previewNotes7K.add(new Note(2, 600));
        this.previewNotes7K.add(new Note(3, 900, 600, Note.Type.HOLD)); // Centro amarillo
        this.previewNotes7K.add(new Note(4, 1200));
        this.previewNotes7K.add(new Note(6, 1500));
        this.previewNotes7K.add(new Note(1, 1800));
        this.previewNotes7K.add(new Note(5, 2100));
        this.previewNotes7K.add(new Note(3, 2500));
    }

    @Override
    protected void init() {
        super.init();
        VSRCraftSettings settings = VSRCraftSettings.get();

        int margin = 10;
        int topBarH = 28;
        int bottomBarH = 22;
        int contentY = topBarH + 6;
        int contentH = this.height - contentY - bottomBarH - 4;

        // Distribución 2 Columnas: Izquierda (Ajustes con Pestañas) ~56%, Derecha (Live Preview) ~44%
        this.leftPanelX = margin;
        this.leftPanelY = contentY;
        this.leftPanelW = Math.max(220, (int) ((this.width - margin * 3) * 0.56f));
        this.leftPanelH = contentH;

        this.rightPanelX = this.leftPanelX + this.leftPanelW + margin;
        this.rightPanelY = contentY;
        this.rightPanelW = this.width - this.rightPanelX - margin;
        this.rightPanelH = contentH;

        // 1. Botón [< Guardar y Salir]
        this.addRenderableWidget(Button.builder(
                Component.literal("< Guardar y Salir"),
                btn -> this.onClose()
        ).bounds(margin, 4, 110, 20).build());

        // 2. Botón [↺ Restablecer Valores] arriba a la derecha
        this.addRenderableWidget(Button.builder(
                Component.literal("↺ Restablecer"),
                btn -> resetToDefaults()
        )
        .bounds(this.width - margin - 86, 4, 86, 20)
        .tooltip(Tooltip.create(Component.literal("Restablece todas las opciones a los valores por defecto")))
        .build());

        // 3. Barra de Pestañas (Tabs) sobre la columna izquierda
        Tab[] tabs = Tab.values();
        int tabGap = 3;
        int tabW = (this.leftPanelW - (tabs.length - 1) * tabGap) / tabs.length;
        int tabH = 20;

        for (int i = 0; i < tabs.length; i++) {
            final Tab tab = tabs[i];
            int tabX = this.leftPanelX + i * (tabW + tabGap);
            boolean isSelected = (this.currentTab == tab);

            Button tabBtn = Button.builder(
                    Component.literal(isSelected ? "§b§l" + tab.label : "§7" + tab.label),
                    btn -> {
                        this.currentTab = tab;
                        this.listeningLane = -1;
                        this.clearWidgets();
                        this.init();
                    }
            ).bounds(tabX, this.leftPanelY, tabW, tabH).build();
            this.addRenderableWidget(tabBtn);
        }

        // 4. Controles según la pestaña activa
        int controlsStartY = this.leftPanelY + tabH + 8;
        int controlInnerW = this.leftPanelW - 16;
        int controlInnerX = this.leftPanelX + 8;
        int spacing = 26;
        int row = 0;

        switch (this.currentTab) {
            case AUDIO -> initAudioTabControls(controlInnerX, controlsStartY, controlInnerW, spacing, settings);
            case GAMEPLAY -> initGameplayTabControls(controlInnerX, controlsStartY, controlInnerW, spacing, settings);
            case VISUAL -> initVisualTabControls(controlInnerX, controlsStartY, controlInnerW, spacing, settings);
            case KEYBINDS -> initKeybindsTabControls(controlInnerX, controlsStartY, controlInnerW, spacing, settings);
        }
    }

    private void initAudioTabControls(int x, int startY, int w, int spacing, VSRCraftSettings settings) {
        int row = 0;

        // Slider: Volumen Música
        this.addRenderableWidget(new AbstractSliderButton(
                x, startY + (row++ * spacing), w, 20,
                Component.literal("Volumen Música: " + Math.round(settings.musicVolume * 100) + "%"),
                settings.musicVolume
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal("Volumen Música: " + Math.round(this.value * 100) + "%"));
            }
            @Override
            protected void applyValue() {
                settings.musicVolume = (float) this.value;
                AudioManager.updateMusicVolume();
            }
        });

        // Slider: Volumen HitSounds
        this.addRenderableWidget(new AbstractSliderButton(
                x, startY + (row++ * spacing), w, 20,
                Component.literal("Volumen HitSounds: " + Math.round(settings.hitSoundVolume * 100) + "%"),
                settings.hitSoundVolume
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal("Volumen HitSounds: " + Math.round(this.value * 100) + "%"));
            }
            @Override
            protected void applyValue() {
                settings.hitSoundVolume = (float) this.value;
            }
        });

        // Fila doble: Toggle HitSounds + Botón Probar Sonido
        int halfW = (w - 6) / 2;
        int toggleY = startY + (row++ * spacing);

        Button toggleBtn = Button.builder(
                getHitSoundToggleText(settings.enableHitSounds),
                btn -> {
                    settings.enableHitSounds = !settings.enableHitSounds;
                    btn.setMessage(getHitSoundToggleText(settings.enableHitSounds));
                }
        ).bounds(x, toggleY, halfW, 20).build();
        this.addRenderableWidget(toggleBtn);

        Button testSoundBtn = Button.builder(
                Component.literal("🎵 Probar Sonido"),
                btn -> AudioManager.playHitSound()
        ).bounds(x + halfW + 6, toggleY, halfW, 20).build();
        this.addRenderableWidget(testSoundBtn);

        // Control Fino de Offset Global
        int offsetRowY = startY + (row++ * spacing) + 4;
        int btnStepW = 42;
        int centerOffsetW = w - (btnStepW * 2) - 8;

        Button decOffset = Button.builder(Component.literal("-5ms"), btn -> {
            settings.globalAudioOffsetMs -= 5L;
            this.clearWidgets();
            this.init();
        }).bounds(x, offsetRowY, btnStepW, 20).build();
        this.addRenderableWidget(decOffset);

        String offsetText = String.format(Locale.ROOT, "Offset Global: %+d ms", settings.globalAudioOffsetMs);
        Button resetOffset = Button.builder(Component.literal(offsetText), btn -> {
            settings.globalAudioOffsetMs = 0L;
            this.clearWidgets();
            this.init();
        }).bounds(x + btnStepW + 4, offsetRowY, centerOffsetW, 20)
          .tooltip(Tooltip.create(Component.literal("Clic para resetear a 0ms")))
          .build();
        this.addRenderableWidget(resetOffset);

        Button incOffset = Button.builder(Component.literal("+5ms"), btn -> {
            settings.globalAudioOffsetMs += 5L;
            this.clearWidgets();
            this.init();
        }).bounds(x + w - btnStepW, offsetRowY, btnStepW, 20).build();
        this.addRenderableWidget(incOffset);
    }

    private void initGameplayTabControls(int x, int startY, int w, int spacing, VSRCraftSettings settings) {
        int row = 0;

        // Slider: Velocidad de Scroll (0.2x a 1.2x)
        double scrollNorm = (settings.scrollSpeed - 0.2) / (1.2 - 0.2);
        this.addRenderableWidget(new AbstractSliderButton(
                x, startY + (row++ * spacing), w, 20,
                Component.literal(String.format(Locale.ROOT, "Velocidad Scroll: %.2fx", settings.scrollSpeed)),
                Math.max(0.0, Math.min(1.0, scrollNorm))
        ) {
            @Override
            protected void updateMessage() {
                float val = 0.2f + (float) this.value * (1.2f - 0.2f);
                this.setMessage(Component.literal(String.format(Locale.ROOT, "Velocidad Scroll: %.2fx", val)));
            }
            @Override
            protected void applyValue() {
                settings.scrollSpeed = 0.2f + (float) this.value * (1.2f - 0.2f);
            }
        });

        // Slider: Posición de Línea de Impacto (Hit Position: 30px a 160px)
        double hitPosNorm = (settings.hitPositionOffsetBottom - 30.0) / (160.0 - 30.0);
        this.addRenderableWidget(new AbstractSliderButton(
                x, startY + (row++ * spacing), w, 20,
                Component.literal("Posición HitLine: " + settings.hitPositionOffsetBottom + " px"),
                Math.max(0.0, Math.min(1.0, hitPosNorm))
        ) {
            @Override
            protected void updateMessage() {
                int px = 30 + (int) Math.round(this.value * 130.0);
                this.setMessage(Component.literal("Posición HitLine: " + px + " px"));
            }
            @Override
            protected void applyValue() {
                settings.hitPositionOffsetBottom = 30 + (int) Math.round(this.value * 130.0);
            }
        });

        // Slider: Grosor de Notas (6px a 24px)
        double noteHNorm = (settings.noteHeight - 6.0) / (24.0 - 6.0);
        this.addRenderableWidget(new AbstractSliderButton(
                x, startY + (row++ * spacing), w, 20,
                Component.literal("Grosor de Notas: " + settings.noteHeight + " px"),
                Math.max(0.0, Math.min(1.0, noteHNorm))
        ) {
            @Override
            protected void updateMessage() {
                int px = 6 + (int) Math.round(this.value * 18.0);
                this.setMessage(Component.literal("Grosor de Notas: " + px + " px"));
            }
            @Override
            protected void applyValue() {
                settings.noteHeight = 6 + (int) Math.round(this.value * 18.0);
            }
        });

        // Sliders independientes: Ancho de Carril 4K (28..70px) y 7K (22..60px)
        int halfW = (w - 6) / 2;
        int widthRowY = startY + (row++ * spacing);

        double w4Norm = (settings.laneWidth4K - 28.0) / (70.0 - 28.0);
        this.addRenderableWidget(new AbstractSliderButton(
                x, widthRowY, halfW, 20,
                Component.literal("Ancho 4K: " + settings.laneWidth4K + " px"),
                Math.max(0.0, Math.min(1.0, w4Norm))
        ) {
            @Override
            protected void updateMessage() {
                int px = 28 + (int) Math.round(this.value * 42.0);
                this.setMessage(Component.literal("Ancho 4K: " + px + " px"));
            }
            @Override
            protected void applyValue() {
                settings.laneWidth4K = 28 + (int) Math.round(this.value * 42.0);
                VSRSettingsScreen.this.editingKeyMode = 4;
            }
        });

        double w7Norm = (settings.laneWidth7K - 22.0) / (60.0 - 22.0);
        this.addRenderableWidget(new AbstractSliderButton(
                x + halfW + 6, widthRowY, halfW, 20,
                Component.literal("Ancho 7K: " + settings.laneWidth7K + " px"),
                Math.max(0.0, Math.min(1.0, w7Norm))
        ) {
            @Override
            protected void updateMessage() {
                int px = 22 + (int) Math.round(this.value * 38.0);
                this.setMessage(Component.literal("Ancho 7K: " + px + " px"));
            }
            @Override
            protected void applyValue() {
                settings.laneWidth7K = 22 + (int) Math.round(this.value * 38.0);
                VSRSettingsScreen.this.editingKeyMode = 7;
            }
        });
    }

    private void initVisualTabControls(int x, int startY, int w, int spacing, VSRCraftSettings settings) {
        int row = 0;

        // Slider: Fondo Oscuro (Background Dim)
        this.addRenderableWidget(new AbstractSliderButton(
                x, startY + (row++ * spacing), w, 20,
                Component.literal("Fondo Oscuro (Dim): " + Math.round(settings.backgroundDim * 100) + "%"),
                settings.backgroundDim
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal("Fondo Oscuro (Dim): " + Math.round(this.value * 100) + "%"));
            }
            @Override
            protected void applyValue() {
                settings.backgroundDim = (float) this.value;
            }
        });

        // Slider: Opacidad de Pista (Lane Alpha)
        this.addRenderableWidget(new AbstractSliderButton(
                x, startY + (row++ * spacing), w, 20,
                Component.literal("Opacidad de Pista: " + Math.round(settings.laneAlpha * 100) + "%"),
                settings.laneAlpha
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal("Opacidad de Pista: " + Math.round(this.value * 100) + "%"));
            }
            @Override
            protected void applyValue() {
                settings.laneAlpha = (float) Math.max(0.05, this.value);
            }
        });

        // Toggle: Hit Glow de Carriles
        Button laneGlowToggle = Button.builder(
                getLaneGlowToggleText(settings.enableLaneGlow),
                btn -> {
                    settings.enableLaneGlow = !settings.enableLaneGlow;
                    btn.setMessage(getLaneGlowToggleText(settings.enableLaneGlow));
                }
        ).bounds(x, startY + (row++ * spacing), w, 20).build();
        this.addRenderableWidget(laneGlowToggle);

        // Toggle: Efectos de Golpe (Voxel Sparks)
        Button hitEffectsToggle = Button.builder(
                getHitEffectsToggleText(settings.enableHitEffects),
                btn -> {
                    settings.enableHitEffects = !settings.enableHitEffects;
                    btn.setMessage(getHitEffectsToggleText(settings.enableHitEffects));
                }
        ).bounds(x, startY + (row++ * spacing), w, 20).build();
        this.addRenderableWidget(hitEffectsToggle);

        // Selector cíclico: Forma de Notas (Barras / Círculos / Flechas)
        Button noteShapeBtn = Button.builder(
                Component.literal("Forma de Notas: §b" + settings.getNoteShape().getDisplayName()),
                btn -> {
                    settings.noteShape = settings.getNoteShape().next();
                    btn.setMessage(Component.literal("Forma de Notas: §b" + settings.getNoteShape().getDisplayName()));
                }
        ).bounds(x, startY + (row++ * spacing), w, 20).build();
        this.addRenderableWidget(noteShapeBtn);

        // Selector cíclico: Tema de Color
        Button colorPresetBtn = Button.builder(
                Component.literal("Tema de Color: §b" + settings.getColorPreset().getDisplayName()),
                btn -> {
                    settings.colorPreset = settings.getColorPreset().next();
                    btn.setMessage(Component.literal("Tema de Color: §b" + settings.getColorPreset().getDisplayName()));
                }
        ).bounds(x, startY + (row++ * spacing), w, 20).build();
        this.addRenderableWidget(colorPresetBtn);
    }

    private void initKeybindsTabControls(int x, int startY, int w, int spacing, VSRCraftSettings settings) {
        int row = 0;

        // Toggle para alternar entre 4K y 7K
        Button modeBtn = Button.builder(
                Component.literal("Modo de Teclas: " + (this.editingKeyMode == 4 ? "§b[ 4K ]" : "§e[ 7K ]") + " §7(Clic para alternar)"),
                btn -> {
                    this.editingKeyMode = (this.editingKeyMode == 4) ? 7 : 4;
                    this.listeningLane = -1;
                    this.clearWidgets();
                    this.init();
                }
        ).bounds(x, startY + (row++ * spacing), w, 20).build();
        this.addRenderableWidget(modeBtn);

        // Fila de botones de asignación interactiva
        int keyY = startY + (row++ * spacing) + 4;
        int count = this.editingKeyMode;
        int keyBtnW = (count == 4) ? Math.min(58, (w - 18) / 4) : Math.min(38, (w - 24) / 7);
        int keyGap = 4;
        int totalKeyW = count * keyBtnW + (count - 1) * keyGap;
        int keyStartX = x + (w - totalKeyW) / 2;

        int[] currentKeys = (count == 4) ? settings.keybinds4K : settings.keybinds7K;

        for (int i = 0; i < count; i++) {
            final int laneIdx = i;
            Button keyBtn = Button.builder(
                    getKeyButtonText(laneIdx, currentKeys[laneIdx]),
                    btn -> {
                        this.listeningLane = laneIdx;
                        this.clearWidgets();
                        this.init();
                    }
            ).bounds(keyStartX + i * (keyBtnW + keyGap), keyY, keyBtnW, 22).build();
            this.addRenderableWidget(keyBtn);
        }
    }

    private void resetToDefaults() {
        VSRCraftSettings s = VSRCraftSettings.get();
        s.musicVolume = 0.5f;
        s.hitSoundVolume = 0.7f;
        s.enableHitSounds = true;
        s.globalAudioOffsetMs = 0L;
        s.scrollSpeed = 0.5f;
        s.hitPositionOffsetBottom = 60;
        s.noteHeight = 12;
        s.laneWidth4K = 45;
        s.laneWidth7K = 36;
        s.laneAlpha = 0.8f;
        s.enableLaneGlow = false;
        s.enableHitEffects = true;
        s.backgroundDim = 0.6f;
        s.noteShape = VSRCraftSettings.NoteShape.BARS;
        s.colorPreset = VSRCraftSettings.ColorPreset.CLASSIC;
        s.keybinds4K = new int[]{GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K};
        s.keybinds7K = new int[]{GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_L};
        VSRCraftSettings.save();
        AudioManager.updateMusicVolume();
        this.clearWidgets();
        this.init();
    }

    private static Component getKeyButtonText(int lane, int keyCode) {
        String keyName = getKeyDisplayName(keyCode);
        return Component.literal("C" + (lane + 1) + ":" + keyName);
    }

    private static String getKeyDisplayName(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) return "SPC";
        try {
            InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(keyCode);
            String name = key.getDisplayName().getString().toUpperCase();
            if (name.length() > 4) name = name.substring(0, 4);
            return name;
        } catch (Exception e) {
            return "K" + keyCode;
        }
    }

    private static Component getHitSoundToggleText(boolean enabled) {
        return Component.literal("HitSounds: " + (enabled ? "§aACTIVADOS" : "§cDESACTIVADOS"));
    }

    private static Component getLaneGlowToggleText(boolean enabled) {
        return Component.literal("Hit Glow de Carriles: " + (enabled ? "§aACTIVADO" : "§cDESACTIVADO"));
    }

    private static Component getHitEffectsToggleText(boolean enabled) {
        return Component.literal("Chispas de Golpe (Sparks): " + (enabled ? "§aACTIVADAS" : "§cDESACTIVADAS"));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.listeningLane >= 0) {
            int newKey = event.key();
            if (newKey != GLFW.GLFW_KEY_ESCAPE) {
                if (this.editingKeyMode == 4) {
                    VSRCraftSettings.get().keybinds4K[this.listeningLane] = newKey;
                } else {
                    VSRCraftSettings.get().keybinds7K[this.listeningLane] = newKey;
                }
                VSRCraftSettings.save();
            }
            this.listeningLane = -1;
            this.clearWidgets();
            this.init();
            return true;
        }

        // Probar teclas en vivo en el previsualizador
        int count = this.editingKeyMode;
        int[] keys = (count == 4) ? VSRCraftSettings.get().keybinds4K : VSRCraftSettings.get().keybinds7K;
        for (int i = 0; i < count; i++) {
            if (event.key() == keys[i]) {
                this.previewPressed[i] = true;
                AudioManager.playHitSound();
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        int count = this.editingKeyMode;
        int[] keys = (count == 4) ? VSRCraftSettings.get().keybinds4K : VSRCraftSettings.get().keybinds7K;
        for (int i = 0; i < count; i++) {
            if (event.key() == keys[i]) {
                this.previewPressed[i] = false;
                return true;
            }
        }
        return super.keyReleased(event);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (this.parentScreen instanceof SongSelectScreen) {
            AudioManager.updatePreview();
        }
        // Fondo general estilizado
        g.fillGradient(0, 0, this.width, this.height, 0xE60B0E14, 0xF206080C);

        // Header superior
        g.fill(0, 0, this.width, 28, 0xCC111622);
        g.fill(0, 27, this.width, 28, 0xFF00D4FF);
        g.drawString(this.font, "§b§lVSRCRAFT §8// §fCONFIGURACIÓN Y PERSONALIZACIÓN", 130, 10, 0xFFFFFFFF, true);

        // Contenedor Columna Izquierda (Ajustes)
        g.fill(leftPanelX, leftPanelY + 23, leftPanelX + leftPanelW, leftPanelY + leftPanelH, 0xD8121722);
        drawBorder(g, leftPanelX, leftPanelY + 23, leftPanelW, leftPanelH - 23, 0xFF26334D);

        // Contenedor Columna Derecha (Live Preview en Tiempo Real)
        renderLivePreviewPanel(g);

        if (this.listeningLane >= 0) {
            String hint = "§ePresiona cualquier tecla para Carril " + (this.listeningLane + 1) + " (ESC para cancelar)";
            g.drawString(this.font, hint, leftPanelX + 10, leftPanelY + leftPanelH - 14, 0xFFFF55, false);
        }

        super.render(g, mouseX, mouseY, partialTick);

        this.transition.renderOverlay(g, this.width, this.height);
    }

    private void renderLivePreviewPanel(GuiGraphics g) {
        // Marco del Previsualizador
        g.fill(rightPanelX, rightPanelY, rightPanelX + rightPanelW, rightPanelY + rightPanelH, 0x900B0E15);
        drawBorder(g, rightPanelX, rightPanelY, rightPanelW, rightPanelH, 0xFF1E2A3C);

        // Encabezado del Previsualizador
        g.fill(rightPanelX + 1, rightPanelY + 1, rightPanelX + rightPanelW - 1, rightPanelY + 22, 0xD0131924);
        g.drawString(this.font, "§b§lPREVISUALIZADOR EN VIVO", rightPanelX + 8, rightPanelY + 7, 0xFF00E5FF, true);

        String modeTag = "[" + this.editingKeyMode + "K]";
        int tagW = this.font.width(modeTag);
        g.drawString(this.font, modeTag, rightPanelX + rightPanelW - 8 - tagW, rightPanelY + 7, 0xFFFFCA28, false);

        // Área activa de la mini-pista
        int trackAreaTop = rightPanelY + 24;
        int trackAreaBottom = rightPanelY + rightPanelH - 20;
        int trackAreaH = trackAreaBottom - trackAreaTop;

        int keyCount = this.editingKeyMode;
        int configuredLaneW = RhythmConfig.getLaneWidth(keyCount);
        int laneW = Math.max(14, Math.min((rightPanelW - 16) / keyCount, Math.round(configuredLaneW * 0.72f)));
        int boardW = keyCount * laneW;
        int boardStartX = rightPanelX + (rightPanelW - boardW) / 2;

        // Calcular posición de la línea de impacto dentro del preview escalada armónicamente
        int hitOffset = VSRCraftSettings.get().hitPositionOffsetBottom;
        int hitLineY = trackAreaBottom - Math.max(20, Math.min(trackAreaH - 30, (int) (hitOffset * 0.8f)));

        g.enableScissor(rightPanelX + 1, trackAreaTop, rightPanelX + rightPanelW - 1, trackAreaBottom);

        // 1. Pista y carriles (aplica opacidad configurada)
        float alpha = VSRCraftSettings.get().laneAlpha;
        int bgAlpha = Math.max(10, Math.min(255, Math.round(alpha * 255)));
        int trackBgColor = (bgAlpha << 24) | 0x101015;

        g.fill(boardStartX, trackAreaTop, boardStartX + boardW, trackAreaBottom, trackBgColor);

        // Divisores de carriles
        int divAlpha = Math.max(5, Math.round(alpha * 0.4f * 255));
        int dividerColor = (divAlpha << 24) | 0xFFFFFF;
        for (int i = 0; i <= keyCount; i++) {
            int lx = boardStartX + (i * laneW);
            g.fill(lx, trackAreaTop, lx + 1, trackAreaBottom, dividerColor);
        }

        // Resplandor de carriles si la tecla está presionada
        if (VSRCraftSettings.get().enableLaneGlow) {
            for (int lane = 0; lane < keyCount; lane++) {
                if (this.previewPressed[lane]) {
                    int lx = boardStartX + (lane * laneW);
                    g.fill(lx, trackAreaTop, lx + laneW, trackAreaBottom, 0x3344AAFF);
                }
            }
        }

        // 2. Receptores / Línea de Impacto según el shape elegido
        VSRCraftSettings.NoteShape shape = VSRCraftSettings.get().getNoteShape();
        int previewRadius = Math.max(5, Math.min((laneW - 6) / 2, 14));
        int hitCenterY = hitLineY + 1;

        if (shape == VSRCraftSettings.NoteShape.BARS) {
            g.fill(boardStartX, hitLineY, boardStartX + boardW, hitLineY + 3, LaneTheme.HIT_LINE);
        } else {
            g.fill(boardStartX, hitCenterY, boardStartX + boardW, hitCenterY + 1, 0x33FFFFFF);
            for (int lane = 0; lane < keyCount; lane++) {
                int laneCenterX = boardStartX + (lane * laneW) + (laneW / 2);
                if (shape == VSRCraftSettings.NoteShape.CIRCLES) {
                    NoteShapeRenderer.drawCircleOutline(g, laneCenterX, hitCenterY, previewRadius, 2, 0xAAFFFFFF);
                } else {
                    NoteShapeRenderer.ArrowDir dir = NoteShapeRenderer.getArrowDirection(lane, keyCount);
                    NoteShapeRenderer.drawArrow(g, laneCenterX, hitCenterY, previewRadius, dir, 0xAAFFFFFF, true);
                }
            }
        }

        // 3. Notas en bucle continuo a la velocidad y grosor configurados
        long now = System.currentTimeMillis();
        long loopElapsed = (now - this.previewLoopStart) % LOOP_DURATION_MS;
        double currentPreviewTimeMs = loopElapsed;

        float scrollSpeed = VSRCraftSettings.get().scrollSpeed;
        int noteH = VSRCraftSettings.get().noteHeight;
        int halfNote = noteH / 2;

        List<Note> previewNotes = (keyCount == 4) ? this.previewNotes4K : this.previewNotes7K;

        for (Note note : previewNotes) {
            int noteX = boardStartX + (note.getLane() * laneW) + 2;
            int noteWidth = laneW - 3;
            int noteColor = LaneTheme.getLaneColor(note.getLane(), keyCount);

            // Cola de Hold
            if (note.getType() == Note.Type.HOLD && note.getDurationMs() > 0) {
                double timeUntilHead = note.getOffsetMs() - currentPreviewTimeMs;
                double timeUntilTail = (note.getOffsetMs() + note.getDurationMs()) - currentPreviewTimeMs;

                int headY = Math.round(hitCenterY - (float) (timeUntilHead * scrollSpeed));
                int tailY = Math.round(hitCenterY - (float) (timeUntilTail * scrollSpeed));

                if (headY >= trackAreaTop && tailY <= trackAreaBottom && headY > tailY) {
                    int clampedTailY = Math.max(trackAreaTop, tailY);
                    int clampedHeadY = Math.min(trackAreaBottom, headY);
                    int holdAlphaColor = (noteColor & 0x00FFFFFF) | 0x88000000;
                    g.fill(noteX + 3, clampedTailY, noteX + noteWidth - 3, clampedHeadY, holdAlphaColor);
                }
            }

            // Cabeza de la nota
            double timeUntilHit = note.getOffsetMs() - currentPreviewTimeMs;
            int centerY = Math.round(hitCenterY - (float) (timeUntilHit * scrollSpeed));
            int centerX = noteX + noteWidth / 2;

            if (shape == VSRCraftSettings.NoteShape.CIRCLES) {
                if (centerY + previewRadius >= trackAreaTop && centerY - previewRadius <= trackAreaBottom) {
                    NoteShapeRenderer.drawFilledCircle(g, centerX, centerY, previewRadius, noteColor);
                    NoteShapeRenderer.drawCircleOutline(g, centerX, centerY, Math.max(2, previewRadius - 2), 1, 0x55FFFFFF);
                }
            } else if (shape == VSRCraftSettings.NoteShape.ARROWS) {
                if (centerY + previewRadius >= trackAreaTop && centerY - previewRadius <= trackAreaBottom) {
                    NoteShapeRenderer.ArrowDir dir = NoteShapeRenderer.getArrowDirection(note.getLane(), keyCount);
                    NoteShapeRenderer.drawArrow(g, centerX, centerY, previewRadius, dir, noteColor, false);
                }
            } else {
                int topY = centerY - halfNote;
                int bottomY = topY + noteH;
                if (bottomY >= trackAreaTop && topY <= trackAreaBottom) {
                    g.fill(noteX, topY, noteX + noteWidth, bottomY, noteColor);
                }
            }
        }

        g.disableScissor();

        // Indicador inferior del Live Preview
        String hintText = "Toca tus teclas para probar en vivo";
        g.drawCenteredString(this.font, hintText, rightPanelX + rightPanelW / 2, trackAreaBottom + 5, 0xFF88AABB);
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void onClose() {
        if (this.transition.isExiting()) return;
        VSRCraftSettings.save();
        this.transition.startExit(180L, () -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parentScreen);
            }
        });
    }
}
