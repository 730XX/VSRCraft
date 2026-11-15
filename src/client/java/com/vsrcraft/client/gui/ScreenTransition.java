package com.vsrcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Utilidad ligera para transiciones visuales suaves (Fade-In / Fade-Out + progreso de entrada)
 * entre las pantallas de VSRCraft sin bloquear el hilo de renderizado.
 */
public class ScreenTransition {
    private long enterStartNano;
    private long enterDurationMs;

    private boolean exiting = false;
    private boolean exitCompleted = false;
    private long exitStartNano = 0L;
    private long exitDurationMs = 240L;
    private Runnable onExitComplete = null;

    public ScreenTransition() {
        this(280L);
    }

    public ScreenTransition(long enterDurationMs) {
        startEnter(enterDurationMs);
    }

    public void startEnter(long durationMs) {
        this.enterStartNano = System.nanoTime();
        this.enterDurationMs = Math.max(1L, durationMs);
        this.exiting = false;
        this.exitCompleted = false;
        this.onExitComplete = null;
    }

    public void startExit(long durationMs, Runnable onComplete) {
        if (this.exiting) return;
        this.exiting = true;
        this.exitCompleted = false;
        this.exitStartNano = System.nanoTime();
        this.exitDurationMs = Math.max(1L, durationMs);
        this.onExitComplete = onComplete;
    }

    public boolean isExiting() {
        return this.exiting;
    }

    /**
     * Progreso suavizado (0.0 a 1.0) de la animación de entrada para deslizar o escalar paneles.
     */
    public float getEnterProgress() {
        double elapsedMs = (System.nanoTime() - this.enterStartNano) / 1_000_000.0;
        float t = (float) Math.max(0.0, Math.min(1.0, elapsedMs / this.enterDurationMs));
        // Cubic ease-out para una entrada fluida
        float inv = 1.0f - t;
        return 1.0f - (inv * inv * inv);
    }

    /**
     * Dibuja el velo de transición encima de la pantalla actual y dispara el callback al terminar el Fade-Out.
     */
    public void renderOverlay(GuiGraphics g, int width, int height) {
        float darknessAlpha = 0.0f;

        if (this.exiting) {
            double elapsedMs = (System.nanoTime() - this.exitStartNano) / 1_000_000.0;
            float t = (float) Math.max(0.0, Math.min(1.0, elapsedMs / this.exitDurationMs));
            darknessAlpha = t * t * (3.0f - 2.0f * t);

            if (elapsedMs >= this.exitDurationMs && !this.exitCompleted) {
                this.exitCompleted = true;
                if (this.onExitComplete != null) {
                    Runnable callback = this.onExitComplete;
                    this.onExitComplete = null;
                    callback.run();
                }
            }
        } else {
            double elapsedMs = (System.nanoTime() - this.enterStartNano) / 1_000_000.0;
            if (elapsedMs < this.enterDurationMs) {
                float t = (float) Math.max(0.0, Math.min(1.0, elapsedMs / this.enterDurationMs));
                float smooth = t * t * (3.0f - 2.0f * t);
                darknessAlpha = 1.0f - smooth;
            }
        }

        if (darknessAlpha > 0.005f) {
            int alphaByte = Math.max(0, Math.min(255, Math.round(darknessAlpha * 255.0f)));
            g.fill(0, 0, width, height, (alphaByte << 24));
        }
    }
}
