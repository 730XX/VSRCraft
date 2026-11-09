package com.vsrcraft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.vsrcraft.client.audio.AudioManager;
import com.vsrcraft.client.config.VSRCraftSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class VSRSettingsScreen extends Screen {
    private final Screen parentScreen;
    private int listeningLane = -1;
    private int editingKeyMode = 4; // 4K o 7K
    private Button[] keybindButtons = new Button[7];
    private Button keyModeToggleButton;

    public VSRSettingsScreen(Screen parentScreen) {
        super(Component.literal("Configuración de VSRCraft"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        VSRCraftSettings settings = VSRCraftSettings.get();

        int centerX = this.width / 2;
        int startY = 24;
        int buttonWidth = 240;
        int spacing = 21;
        int row = 0;

        // 1. Slider: Volumen de Música (0% - 100%)
        this.addRenderableWidget(new AbstractSliderButton(
                centerX - buttonWidth / 2, startY + (row++ * spacing), buttonWidth, 20,
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

        // 2. Slider: Volumen de Hitsounds (0% - 100%)
        this.addRenderableWidget(new AbstractSliderButton(
                centerX - buttonWidth / 2, startY + (row++ * spacing), buttonWidth, 20,
                Component.literal("Volumen Hitsounds: " + Math.round(settings.hitSoundVolume * 100) + "%"),
                settings.hitSoundVolume
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal("Volumen Hitsounds: " + Math.round(this.value * 100) + "%"));
            }

            @Override
            protected void applyValue() {
                settings.hitSoundVolume = (float) this.value;
            }
        });

        // 3. Botón: Activar / Desactivar Hitsounds
        Button hitSoundToggle = Button.builder(
                getHitSoundToggleText(settings.enableHitSounds),
                btn -> {
                    settings.enableHitSounds = !settings.enableHitSounds;
                    btn.setMessage(getHitSoundToggleText(settings.enableHitSounds));
                }
        ).bounds(centerX - buttonWidth / 2, startY + (row++ * spacing), buttonWidth, 20).build();
        this.addRenderableWidget(hitSoundToggle);

        // 4. Slider: Velocidad de Scroll (0.2 a 1.2 px/ms)
        double scrollNorm = (settings.scrollSpeed - 0.2) / (1.2 - 0.2);
        this.addRenderableWidget(new AbstractSliderButton(
                centerX - buttonWidth / 2, startY + (row++ * spacing), buttonWidth, 20,
                Component.literal(String.format(java.util.Locale.ROOT, "Velocidad Scroll: %.2fx", settings.scrollSpeed)),
                Math.max(0.0, Math.min(1.0, scrollNorm))
        ) {
            @Override
            protected void updateMessage() {
                float val = 0.2f + (float) this.value * (1.2f - 0.2f);
                this.setMessage(Component.literal(String.format(java.util.Locale.ROOT, "Velocidad Scroll: %.2fx", val)));
            }

            @Override
            protected void applyValue() {
                settings.scrollSpeed = 0.2f + (float) this.value * (1.2f - 0.2f);
            }
        });

        // 5. Slider: Posición de Línea de Impacto (Hit Position: 30px a 160px)
        double hitPosNorm = (settings.hitPositionOffsetBottom - 30.0) / (160.0 - 30.0);
        this.addRenderableWidget(new AbstractSliderButton(
                centerX - buttonWidth / 2, startY + (row++ * spacing), buttonWidth, 20,
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

        // 6. Slider: Oscurecimiento de Fondo (Background Dim: 0% a 100%)
        this.addRenderableWidget(new AbstractSliderButton(
                centerX - buttonWidth / 2, startY + (row++ * spacing), buttonWidth, 20,
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

        // 7. Slider: Opacidad de Carriles (5% - 100%)
        this.addRenderableWidget(new AbstractSliderButton(
                centerX - buttonWidth / 2, startY + (row++ * spacing), buttonWidth, 20,
                Component.literal("Opacidad Pista: " + Math.round(settings.laneAlpha * 100) + "%"),
                settings.laneAlpha
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal("Opacidad Pista: " + Math.round(this.value * 100) + "%"));
            }

            @Override
            protected void applyValue() {
                settings.laneAlpha = (float) Math.max(0.05, this.value);
            }
        });

        // 8. Botón: Activar / Desactivar Hit Glow de los Carriles
        Button laneGlowToggle = Button.builder(
                getLaneGlowToggleText(settings.enableLaneGlow),
                btn -> {
                    settings.enableLaneGlow = !settings.enableLaneGlow;
                    btn.setMessage(getLaneGlowToggleText(settings.enableLaneGlow));
                }
        ).bounds(centerX - buttonWidth / 2, startY + (row++ * spacing), buttonWidth, 20).build();
        this.addRenderableWidget(laneGlowToggle);

        // 9. Selector de Modo de Teclas (Toggle 4K / 7K)
        this.keyModeToggleButton = Button.builder(
                getKeyModeToggleText(),
                btn -> {
                    this.editingKeyMode = (this.editingKeyMode == 4) ? 7 : 4;
                    this.listeningLane = -1;
                    this.clearWidgets();
                    this.init();
                }
        ).bounds(centerX - buttonWidth / 2, startY + (row++ * spacing), buttonWidth, 20).build();
        this.addRenderableWidget(this.keyModeToggleButton);

        // 10. Botones de Teclas según el modo (4K o 7K)
        int keyY = startY + (row++ * spacing) + 2;
        int count = this.editingKeyMode;
        int keyBtnW = (count == 4) ? 56 : 38;
        int keyGap = (count == 4) ? 6 : 4;
        int totalKeyWidth = count * keyBtnW + (count - 1) * keyGap;
        int keyStartX = centerX - totalKeyWidth / 2;

        int[] currentKeys = (count == 4) ? settings.keybinds4K : settings.keybinds7K;

        for (int i = 0; i < count; i++) {
            final int laneIdx = i;
            Button keyBtn = Button.builder(
                    getKeyButtonText(laneIdx, currentKeys[laneIdx]),
                    btn -> {
                        this.listeningLane = laneIdx;
                        updateKeyButtonLabels();
                    }
            ).bounds(keyStartX + i * (keyBtnW + keyGap), keyY, keyBtnW, 20).build();
            this.keybindButtons[i] = keyBtn;
            this.addRenderableWidget(keyBtn);
        }

        // Botón Listo / Guardar
        this.addRenderableWidget(Button.builder(
                Component.literal("Listo / Guardar"),
                btn -> this.onClose()
        ).bounds(centerX - buttonWidth / 2, this.height - 26, buttonWidth, 20).build());
    }

    private Component getKeyModeToggleText() {
        return Component.literal("Configurar Teclas: " + (this.editingKeyMode == 4 ? "§b[ 4K ]" : "§e[ 7K ]") + " §7(Clic para cambiar)");
    }

    private void updateKeyButtonLabels() {
        VSRCraftSettings settings = VSRCraftSettings.get();
        int[] keys = (this.editingKeyMode == 4) ? settings.keybinds4K : settings.keybinds7K;
        for (int i = 0; i < this.editingKeyMode; i++) {
            if (this.keybindButtons[i] != null) {
                if (this.listeningLane == i) {
                    this.keybindButtons[i].setMessage(Component.literal("§e[...]"));
                } else {
                    this.keybindButtons[i].setMessage(getKeyButtonText(i, keys[i]));
                }
            }
        }
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
            if (name.length() > 4) {
                name = name.substring(0, 4);
            }
            return name;
        } catch (Exception e) {
            return "K" + keyCode;
        }
    }

    private static Component getHitSoundToggleText(boolean enabled) {
        return Component.literal("Efectos HitSound: " + (enabled ? "§aACTIVADOS" : "§cDESACTIVADOS"));
    }

    private static Component getLaneGlowToggleText(boolean enabled) {
        return Component.literal("Hit Glow de Carriles: " + (enabled ? "§aACTIVADO" : "§cDESACTIVADO"));
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
            updateKeyButtonLabels();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xD0101010, 0xE0101010);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        if (this.listeningLane >= 0) {
            graphics.drawCenteredString(this.font, "§ePresiona cualquier tecla para Carril " + (this.listeningLane + 1) + " (ESC para cancelar)", this.width / 2, this.height - 42, 0xFFFF55);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        VSRCraftSettings.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }
}
