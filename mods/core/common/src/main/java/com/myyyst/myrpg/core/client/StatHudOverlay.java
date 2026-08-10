package com.myyyst.myrpg.core.client;

import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.network.RpgPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class StatHudOverlay {

    private static final int BAR_W = 80, BAR_H = 7, PAD = 4;


    private static long lastLog;

    public static void render(GuiGraphicsExtractor graphics) {

        long now = System.currentTimeMillis();
        if (now - lastLog > 3000) {
            lastLog = now;
            int count = 0;
            for (var e : ClientStatCache.entries()) count++;
            Constants.LOG.info("[myrpg/hud] render tick, cache entries: {}", count);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // NOTE drift: the hide-GUI flag (F1) — was mc.options.hideGui;
        // autocomplete mc.options.h... for the current name, or drop the
        // check for v1 (F1 usually suppresses the whole HUD layer anyway
        // when registered through the loaders' layer APIs).

        int y = 8;
        for (RpgPayloads.StatEntry entry : ClientStatCache.entries()) {
            if (!visible(entry)) continue;

            int color = parseColor(entry.color());
            if ("number".equals(entry.hudType())) {
                graphics.text(mc.font,
                        Component.literal(entry.name() + ": " + format(entry.value())),
                        8, y, 0xFF000000 | color);
                y += 12;
            } else {
                graphics.text(mc.font, Component.literal(entry.name()), 8, y, 0xFFFFFFFF);
                int barY = y + 10;
                fillRect(graphics, 8, barY, BAR_W, BAR_H, 0xA0000000);
                double frac = (entry.value() - entry.min()) / Math.max(1e-9, entry.max() - entry.min());
                int fillW = (int) Math.round(BAR_W * Math.max(0, Math.min(1, frac)));
                fillRect(graphics, 8, barY, fillW, BAR_H, 0xFF000000 | color);
                if (entry.showValue()) {
                    graphics.text(mc.font, Component.literal(format(entry.value())),
                            8 + BAR_W + PAD, barY, 0xFFFFFFFF);
                }
                y += 10 + BAR_H + PAD;
            }
        }
    }

    /**
     * NOTE drift: check fill's parameter meaning — type graphics.fill( and
     * read the hints. The editor-era code used corner form (x1,y1,x2,y2,color);
     * if yours shows width/height form, change the body to
     * graphics.fill(x, y, w, h, color) instead.
     */
    private static void fillRect(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + h, color);
    }

    private static boolean visible(RpgPayloads.StatEntry entry) {
        return !"never".equals(entry.visibility());
    }

    private static int parseColor(String hex) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }
}
