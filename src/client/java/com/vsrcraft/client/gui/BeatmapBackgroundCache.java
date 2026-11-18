package com.vsrcraft.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.vsrcraft.VSRCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Caché de texturas para imágenes de fondo (.jpg / .png) de beatmaps usadas en banners/cards.
 * Escala automáticamente imágenes grandes a un tamaño compacto para ahorrar VRAM y evitar stufferings
 */
public final class BeatmapBackgroundCache {

    private static final int MAX_TEX_W = 512;
    private static final int MAX_TEX_H = 288;

    private record CachedBg(Identifier id, int width, int height) {}

    private static final Map<String, CachedBg> CACHE = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static int nextId = 0;

    private BeatmapBackgroundCache() {}

    public static void drawBannerBackground(GuiGraphics g, String filePath, int x, int y, int w, int h) {
        if (filePath == null || filePath.isEmpty() || w <= 0 || h <= 0) return;

        CachedBg bg = getOrLoad(filePath);
        if (bg == null) return;

        // Crop proporcional (cover) centrado dentro del rectángulo [x, y, w, h]
        float boxAspect = (float) w / (float) h;
        float texAspect = (float) bg.width() / (float) bg.height();

        float u = 0.0f;
        float v = 0.0f;
        int regionW = bg.width();
        int regionH = bg.height();

        if (texAspect > boxAspect) {
            regionW = Math.max(1, Math.round(bg.height() * boxAspect));
            u = (bg.width() - regionW) / 2.0f;
        } else if (texAspect < boxAspect) {
            regionH = Math.max(1, Math.round(bg.width() / boxAspect));
            v = (bg.height() - regionH) / 2.0f;
        }

        g.blit(RenderPipelines.GUI_TEXTURED, bg.id(), x, y, u, v, w, h, regionW, regionH, bg.width(), bg.height());
    }

    private static CachedBg getOrLoad(String filePath) {
        CachedBg cached = CACHE.get(filePath);
        if (cached != null) return cached;
        if (FAILED.contains(filePath)) return null;

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            FAILED.add(filePath);
            return null;
        }

        try {
            BufferedImage raw = ImageIO.read(file);
            if (raw == null) {
                FAILED.add(filePath);
                return null;
            }

            int targetW = raw.getWidth();
            int targetH = raw.getHeight();
            if (targetW > MAX_TEX_W || targetH > MAX_TEX_H) {
                double scale = Math.min((double) MAX_TEX_W / targetW, (double) MAX_TEX_H / targetH);
                targetW = Math.max(1, (int) Math.round(targetW * scale));
                targetH = Math.max(1, (int) Math.round(targetH * scale));
            }

            BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = scaled.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(raw, 0, 0, targetW, targetH, null);
            g2d.dispose();

            NativeImage nativeImage = new NativeImage(targetW, targetH, false);
            for (int py = 0; py < targetH; py++) {
                for (int px = 0; px < targetW; px++) {
                    nativeImage.setPixel(px, py, scaled.getRGB(px, py));
                }
            }

            Identifier id = Identifier.fromNamespaceAndPath(VSRCraft.MOD_ID, "bg_" + (nextId++));
            DynamicTexture texture = new DynamicTexture(() -> id.toString(), nativeImage);
            Minecraft.getInstance().getTextureManager().register(id, texture);

            CachedBg result = new CachedBg(id, targetW, targetH);
            CACHE.put(filePath, result);
            return result;
        } catch (Exception e) {
            FAILED.add(filePath);
            return null;
        }
    }
}
