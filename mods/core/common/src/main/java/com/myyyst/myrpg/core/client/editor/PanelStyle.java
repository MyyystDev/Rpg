package com.myyyst.myrpg.core.client.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The RPG Framework editor design language: dark stone, pixel bevels,
 * 8px grid, 24px controls, square corners. Every editor screen draws
 * through these helpers so the language stays identical framework-wide.
 */
public final class PanelStyle {

    // grid
    public static final int GRID = 8;
    public static final int CONTROL_H = 24;
    public static final int ROW_H = 52;

    // palette — dark stone
    public static final int SCREEN_DIM  = 0xB0101012;
    public static final int PANEL_BG    = 0xFF2E2E31;
    public static final int PANEL_LIGHT = 0xFF4C4C50;   // top/left bevel
    public static final int PANEL_DARK  = 0xFF141416;   // bottom/right bevel
    public static final int INSET_BG    = 0xFF1C1C1F;
    public static final int ROW_BG      = 0xFF29292C;
    public static final int ROW_HOVER   = 0xFF36363B;
    public static final int ROW_SELECT  = 0xFF3A4A66;
    public static final int TEXT        = 0xFFE8E8E8;
    public static final int TEXT_DIM    = 0xFF9C9CA0;
    public static final int ACCENT      = 0xFF9858B8;   // the doc's corruption purple
    public static final int VALID       = 0xFF57B36A;
    public static final int EDITED      = 0xFFD8A93C;
    public static final int ERROR       = 0xFFD05050;

    /** Raised panel: bg + light top/left, dark bottom/right. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        g.fill(x, y, x + w, y + 1, PANEL_LIGHT);
        g.fill(x, y, x + 1, y + h, PANEL_LIGHT);
        g.fill(x, y + h - 1, x + w, y + h, PANEL_DARK);
        g.fill(x + w - 1, y, x + w, y + h, PANEL_DARK);
    }

    /** Sunken well (fields, list areas): inverted bevel. */
    public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, INSET_BG);
        g.fill(x, y, x + w, y + 1, PANEL_DARK);
        g.fill(x, y, x + 1, y + h, PANEL_DARK);
        g.fill(x, y + h - 1, x + w, y + h, PANEL_LIGHT);
        g.fill(x + w - 1, y, x + w, y + h, PANEL_LIGHT);
    }

    /** 24px-tall button; returns nothing — hit-testing is the caller's. */
    public static void button(GuiGraphicsExtractor g, Font font, String label,
                              int x, int y, int w, boolean hovered, boolean primary) {
        int bg = primary ? (hovered ? 0xFFAA6ACC : ACCENT)
                : (hovered ? ROW_HOVER : PANEL_BG);
        g.fill(x, y, x + w, y + CONTROL_H, bg);
        g.fill(x, y, x + w, y + 1, PANEL_LIGHT);
        g.fill(x, y, x + 1, y + CONTROL_H, PANEL_LIGHT);
        g.fill(x, y + CONTROL_H - 1, x + w, y + CONTROL_H, PANEL_DARK);
        g.fill(x + w - 1, y, x + w, y + CONTROL_H, PANEL_DARK);
        int tw = font.width(label);
        g.text(font, Component.literal(label), x + (w - tw) / 2, y + (CONTROL_H - 8) / 2, TEXT);
    }

    /** Small status chip (VALID / EDITED / N ERROR). */
    public static void chip(GuiGraphicsExtractor g, Font font, String label, int x, int y, int color) {
        int w = font.width(label) + 8;
        g.fill(x, y, x + w, y + 12, 0xFF202022);
        g.fill(x, y, x + w, y + 1, color);
        g.text(font, Component.literal(label), x + 4, y + 2, color);
    }

    public static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Vertical scrollbar inside a list frame. Draws only when needed. */
    public static void scrollbar(GuiGraphicsExtractor g, int x, int y, int h,
                                 int totalItems, int visibleItems, int scroll) {
        if (totalItems <= visibleItems) return;
        g.fill(x, y, x + 4, y + h, INSET_BG);
        int thumbH = Math.max(12, h * visibleItems / totalItems);
        int maxScroll = totalItems - visibleItems;
        int thumbY = y + (h - thumbH) * Math.min(scroll, maxScroll) / maxScroll;
        g.fill(x, thumbY, x + 4, thumbY + thumbH, PANEL_LIGHT);
    }

    private PanelStyle() {}
}