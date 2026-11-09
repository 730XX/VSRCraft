package com.vsrcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.vsrcraft.block.RhythmArcadeBlock;
import com.vsrcraft.client.domain.BeatmapRepository;
import com.vsrcraft.client.gui.SongSelectScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class VSRCraftClient implements ClientModInitializer {
    private static KeyMapping openVSRGKey;

    @Override
    public void onInitializeClient() {
        // Precargar catálogo de beatmaps al iniciar el cliente
        BeatmapRepository.reload();

        // Conectar la interacción del bloque Mini-Arcade Mecánico de Redstone con la GUI de canciones
        RhythmArcadeBlock.clientOpenScreenAction = () -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && client.screen == null) {
                client.setScreen(new SongSelectScreen());
            }
        };

        // Registrar tecla 'V' (acceso rápido en modo Creativo; en Supervivencia guía al bloque Arcade)
        openVSRGKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.vsrcraft.open_vsrg",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                KeyMapping.Category.MISC
        ));

        // Detectar pulsación en el cliente
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openVSRGKey.consumeClick()) {
                if (client.player != null && client.screen == null) {
                    if (client.player.isCreative() || client.player.isSpectator()) {
                        client.setScreen(new SongSelectScreen());
                    } else {
                        client.player.displayClientMessage(
                                Component.translatable("message.vsrcraft.use_arcade_block"),
                                true
                        );
                    }
                }
            }
        });
    }
}
