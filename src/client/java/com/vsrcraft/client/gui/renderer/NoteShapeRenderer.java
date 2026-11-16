package com.vsrcraft.client.gui.renderer;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Helper compartido para dibujar formas de notas y receptores (Círculos y Flechas)
 * sin depender de texturas externas.
 */
public final class NoteShapeRenderer {

    public enum ArrowDir {
        LEFT, DOWN, UP, RIGHT, DIAMOND
    }

    private NoteShapeRenderer() {}

    public static ArrowDir getArrowDirection(int lane, int totalLanes) {
        if (totalLanes == 4) {
            return switch (lane) {
                case 0 -> ArrowDir.LEFT;
                case 1 -> ArrowDir.DOWN;
                case 2 -> ArrowDir.UP;
                default -> ArrowDir.RIGHT;
            };
        }
        if (totalLanes == 7) {
            return switch (lane) {
                case 0 -> ArrowDir.LEFT;
                case 1, 4 -> ArrowDir.DOWN;
                case 2, 5 -> ArrowDir.UP;
                case 3 -> ArrowDir.DIAMOND;
                default -> ArrowDir.RIGHT;
            };
        }
        return switch (lane % 4) {
            case 0 -> ArrowDir.LEFT;
            case 1 -> ArrowDir.DOWN;
            case 2 -> ArrowDir.UP;
            default -> ArrowDir.RIGHT;
        };
    }

    public static void drawFilledCircle(GuiGraphics g, int cx, int cy, int radius, int color) {
        if (radius <= 0) return;
        int r2 = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.floor(Math.sqrt(Math.max(0, r2 - dy * dy)));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    public static void drawCircleOutline(GuiGraphics g, int cx, int cy, int radius, int thickness, int color) {
        if (radius <= 0) return;
        int outer2 = radius * radius;
        int innerR = Math.max(0, radius - Math.max(1, thickness));
        int inner2 = innerR * innerR;
        for (int dy = -radius; dy <= radius; dy++) {
            int outDx = (int) Math.floor(Math.sqrt(Math.max(0, outer2 - dy * dy)));
            if (Math.abs(dy) >= innerR) {
                g.fill(cx - outDx, cy + dy, cx + outDx + 1, cy + dy + 1, color);
            } else {
                int inDx = (int) Math.floor(Math.sqrt(Math.max(0, inner2 - dy * dy)));
                if (outDx > inDx) {
                    g.fill(cx - outDx, cy + dy, cx - inDx, cy + dy + 1, color);
                    g.fill(cx + inDx + 1, cy + dy, cx + outDx + 1, cy + dy + 1, color);
                }
            }
        }
    }

    public static void drawArrow(GuiGraphics g, int cx, int cy, int radius, ArrowDir dir, int color, boolean outlineOnly) {
        if (radius <= 2) return;
        int innerRadius = Math.max(1, radius - 2);

        for (int dy = -radius; dy <= radius; dy++) {
            int spanStart = Integer.MIN_VALUE;
            for (int dx = -radius; dx <= radius; dx++) {
                boolean outer = isInsideShape(dx, dy, radius, dir);
                boolean draw = outlineOnly
                        ? (outer && !isInsideShape(dx, dy, innerRadius, dir))
                        : outer;

                if (draw) {
                    if (spanStart == Integer.MIN_VALUE) {
                        spanStart = dx;
                    }
                } else if (spanStart != Integer.MIN_VALUE) {
                    g.fill(cx + spanStart, cy + dy, cx + dx, cy + dy + 1, color);
                    spanStart = Integer.MIN_VALUE;
                }
            }
            if (spanStart != Integer.MIN_VALUE) {
                g.fill(cx + spanStart, cy + dy, cx + radius + 1, cy + dy + 1, color);
            }
        }
    }

    private static boolean isInsideShape(int dx, int dy, int r, ArrowDir dir) {
        if (dir == ArrowDir.DIAMOND) {
            return Math.abs(dx) + Math.abs(dy) <= r;
        }
        // Rotar coordenadas para normalizar todas las flechas a apuntar hacia ARRIBA (y negativo)
        int rx;
        int ry;
        switch (dir) {
            case UP -> {
                rx = dx;
                ry = dy;
            }
            case DOWN -> {
                rx = dx;
                ry = -dy;
            }
            case LEFT -> {
                rx = dy;
                ry = dx;
            }
            case RIGHT -> {
                rx = dy;
                ry = -dx;
            }
            default -> {
                rx = dx;
                ry = dy;
            }
        }

        // Cabeza triangular en [-r, 0] y tallo rectangular en [0, r]
        int headBaseY = Math.max(1, r / 6);
        if (ry <= headBaseY) {
            int distFromTip = ry + r;
            int totalHeadH = headBaseY + r;
            if (distFromTip < 0 || totalHeadH <= 0) return false;
            int halfWidth = (distFromTip * r) / totalHeadH;
            return Math.abs(rx) <= halfWidth;
        } else {
            int stemHalfW = Math.max(2, (r * 36) / 100);
            int stemBottom = (r * 88) / 100;
            return ry <= stemBottom && Math.abs(rx) <= stemHalfW;
        }
    }

    /**
     * Dibuja el cuerpo de una nota Hold (LN) con la punta superior (tailY) redondeada en semicírculo.
     */
    public static void drawRoundedHoldBody(
            GuiGraphics g,
            int leftX,
            int rightX,
            int tailY,
            int headY,
            int minClipY,
            int maxClipY,
            int bodyColor,
            boolean isHolding
    ) {
        int width = rightX - leftX;
        if (width <= 2 || headY <= tailY) return;

        int capRadius = Math.min(width / 2, headY - tailY);
        int bodyTopY = tailY + capRadius;
        double centerX = (leftX + rightX) / 2.0;

        // 1. Semicírculo superior redondeado en tailY
        if (capRadius > 1) {
            double r2 = capRadius * (double) capRadius;
            for (int dy = 0; dy < capRadius; dy++) {
                int y = tailY + dy;
                if (y < minClipY || y >= maxClipY) continue;
                double distFromCenter = capRadius - dy;
                double halfSpan = Math.sqrt(Math.max(0.0, r2 - distFromCenter * distFromCenter));
                int rowLeft = Math.max(leftX, (int) Math.floor(centerX - halfSpan));
                int rowRight = Math.min(rightX, (int) Math.ceil(centerX + halfSpan));
                if (rowRight > rowLeft) {
                    g.fill(rowLeft, y, rowRight, y + 1, bodyColor);
                }
            }
        }

        // 2. Cuerpo recto desde bodyTopY hasta headY
        int clampedTop = Math.max(minClipY, bodyTopY);
        int clampedBottom = Math.min(maxClipY, headY);
        if (clampedBottom > clampedTop) {
            g.fill(leftX, clampedTop, rightX, clampedBottom, bodyColor);
        }
    }
}
