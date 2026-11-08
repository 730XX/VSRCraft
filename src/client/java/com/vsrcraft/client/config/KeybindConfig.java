package com.vsrcraft.client.config;

import org.lwjgl.glfw.GLFW;

public class KeybindConfig {
    public static final int[] DEFAULT_6K = {
            GLFW.GLFW_KEY_S,
            GLFW.GLFW_KEY_D,
            GLFW.GLFW_KEY_F,
            GLFW.GLFW_KEY_J,
            GLFW.GLFW_KEY_K,
            GLFW.GLFW_KEY_L
    };

    public static final int[] DEFAULT_7K = {
            GLFW.GLFW_KEY_S,
            GLFW.GLFW_KEY_D,
            GLFW.GLFW_KEY_F,
            GLFW.GLFW_KEY_SPACE,
            GLFW.GLFW_KEY_J,
            GLFW.GLFW_KEY_K,
            GLFW.GLFW_KEY_L
    };

    public static int[] getKeysForMode(int keyCount) {
        if (keyCount == 4) {
            return VSRCraftSettings.get().keybinds4K;
        }
        return switch (keyCount) {
            case 6 -> DEFAULT_6K;
            case 7 -> DEFAULT_7K;
            default -> VSRCraftSettings.get().keybinds4K;
        };
    }
}
