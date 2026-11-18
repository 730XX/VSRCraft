package com.vsrcraft.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.vsrcraft.client.gui.ResultsScreen;
import com.vsrcraft.client.gui.RhythmGameScreen;
import com.vsrcraft.client.gui.SongSelectScreen;
import com.vsrcraft.client.gui.VSRSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Permite que las pantallas de VSRCraft corran a la tasa de refresco completa
 * configurada por el usuario (ej. 144Hz, 240Hz, ilimitado) en vez del tope de 60 FPS de menús.
 */
@Mixin(FramerateLimitTracker.class)
public class FramerateLimitTrackerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private int framerateLimit;

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void unlockVSRCraftFps(CallbackInfoReturnable<Integer> cir) {
        if (this.minecraft != null) {
            Screen s = this.minecraft.screen;
            if (s instanceof RhythmGameScreen
                    || s instanceof SongSelectScreen
                    || s instanceof VSRSettingsScreen
                    || s instanceof ResultsScreen) {
                cir.setReturnValue(Math.max(this.framerateLimit, 260));
            }
        }
    }
}
