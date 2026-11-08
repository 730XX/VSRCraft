package com.vsrcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.vsrcraft.client.domain.BeatmapRepository;
import com.vsrcraft.client.gui.SongSelectScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class VSRCraftClient implements ClientModInitializer {
    private static KeyMapping openVSRGKey;

    @Override
    public void onInitializeClient() {
        // Precargar catálogo de beatmaps al iniciar el cliente
        BeatmapRepository.reload();

        // Registrar tecla 'V' para abrir el Selector de Canciones de VSRCraft
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
                    client.setScreen(new SongSelectScreen());
                }
            }
        });
    }
}
