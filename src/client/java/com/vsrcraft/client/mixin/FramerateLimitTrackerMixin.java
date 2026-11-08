package com.vsrcraft.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.vsrcraft.client.gui.RhythmGameScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Permite que la pantalla del minijuego de ritmo corra a la tasa de refresco completa
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
        if (this.minecraft != null && this.minecraft.screen instanceof RhythmGameScreen) {
            // Devolver el límite real de FPS fijado por el usuario (o VSync nativo del monitor)
            cir.setReturnValue(this.framerateLimit > 0 ? this.framerateLimit : 260);
        }
    }
}
