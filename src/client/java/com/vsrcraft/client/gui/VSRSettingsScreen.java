package com.vsrcraft.client.gui;

import com.vsrcraft.client.audio.AudioManager;
import com.vsrcraft.client.config.VSRCraftSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class VSRSettingsScreen extends Screen {
    private final Screen parentScreen;

    public VSRSettingsScreen(Screen parentScreen) {
        super(Component.literal("Configuración de VSRCraft"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        VSRCraftSettings settings = VSRCraftSettings.get();

        int centerX = this.width / 2;
        int startY = 40;
        int buttonWidth = 240;
        int spacing = 26;
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
                Component.literal(String.format("Velocidad Scroll: %.2fx", settings.scrollSpeed)),
                Math.max(0.0, Math.min(1.0, scrollNorm))
        ) {
            @Override
            protected void updateMessage() {
                float val = 0.2f + (float) this.value * (1.2f - 0.2f);
                this.setMessage(Component.literal(String.format("Velocidad Scroll: %.2fx", val)));
            }

            @Override
            protected void applyValue() {
                settings.scrollSpeed = 0.2f + (float) this.value * (1.2f - 0.2f);
            }
        });

        // 5. Slider: Opacidad de Carriles (10% - 100%)
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

        // 6. Slider: Altura / Grosor de Notas (6px a 24px)
        double heightNorm = (settings.noteHeight - 6.0) / (24.0 - 6.0);
        this.addRenderableWidget(new AbstractSliderButton(
                centerX - buttonWidth / 2, startY + (row++ * spacing), buttonWidth, 20,
                Component.literal("Grosor Notas: " + settings.noteHeight + " px"),
                Math.max(0.0, Math.min(1.0, heightNorm))
        ) {
            @Override
            protected void updateMessage() {
                int px = 6 + (int) Math.round(this.value * 18.0);
                this.setMessage(Component.literal("Grosor Notas: " + px + " px"));
            }

            @Override
            protected void applyValue() {
                settings.noteHeight = 6 + (int) Math.round(this.value * 18.0);
            }
        });

        // 7. Slider: Offset Global de Audio (-100ms a +100ms)
        double offsetNorm = (settings.globalAudioOffsetMs + 100.0) / 200.0;
        this.addRenderableWidget(new AbstractSliderButton(
                centerX - buttonWidth / 2, startY + (row++ * spacing), buttonWidth, 20,
                Component.literal(String.format("Offset Audio: %+d ms", settings.globalAudioOffsetMs)),
                Math.max(0.0, Math.min(1.0, offsetNorm))
        ) {
            @Override
            protected void updateMessage() {
                long ms = Math.round((this.value * 200.0) - 100.0);
                this.setMessage(Component.literal(String.format("Offset Audio: %+d ms", ms)));
            }

            @Override
            protected void applyValue() {
                settings.globalAudioOffsetMs = Math.round((this.value * 200.0) - 100.0);
            }
        });

        // Botón Listo / Guardar
        this.addRenderableWidget(Button.builder(
                Component.literal("Listo / Guardar"),
                btn -> this.onClose()
        ).bounds(centerX - buttonWidth / 2, this.height - 35, buttonWidth, 20).build());
    }

    private static Component getHitSoundToggleText(boolean enabled) {
        return Component.literal("Efectos HitSound: " + (enabled ? "§aACTIVADOS" : "§cDESACTIVADOS"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Fondo translúcido oscuro
        graphics.fillGradient(0, 0, this.width, this.height, 0xD0101010, 0xE0101010);

        // Título
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);

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
