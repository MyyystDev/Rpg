package com.myyyst.myrpg.core.client;

import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.network.RpgPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Draws the custom stat HUD in the top-left corner, one entry per visible stat.
 *
 * <p>Client only, called from each loader's HUD render hook. Everything it draws comes from
 * {@link ClientStatCache}; the {@code hudType} field of each entry picks the shape
 * (bar / number / percentage / icons / hidden) and {@code visibility} decides whether the
 * entry is drawn at all.</p>
 */
public final class StatHudOverlay {

    /** Bar geometry and the gap between an element and the value text next to it. */
    private static final int BAR_W = 80, BAR_H = 7, PAD = 4;

    private static long lastLog;

    /** Renders every visible stat, stacking downwards from the top-left. */
    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int y = 8;   // running cursor: each entry advances it by its own height
        for (RpgPayloads.StatEntry entry : ClientStatCache.entries()) {
            if (!visible(entry)) continue;

            int color = parseColor(entry.color());
            // Position inside the stat's range, clamped to 0..1. The epsilon guards
            // against a definition where min == max.
            double frac = (entry.value() - entry.min()) / Math.max(1e-9, entry.max() - entry.min());
            frac = Math.max(0, Math.min(1, frac));

            switch (entry.hudType()) {
                case "hidden" -> { /* tracked but never drawn */ }
                case "number" -> {
                    g.text(mc.font, Component.literal(entry.name() + ": " + format(entry.value())),
                            8, y, 0xFF000000 | color);
                    y += 12;
                }
                case "percentage" -> {
                    long pct = Math.round(frac * 100);
                    g.text(mc.font, Component.literal(entry.name() + ": " + pct + "%"),
                            8, y, 0xFF000000 | color);
                    y += 12;
                }
                case "icons" -> {   // a fixed row of 10 pips, filled proportionally
                    g.text(mc.font, Component.literal(entry.name()), 8, y, 0xFFFFFFFF);
                    int pips = 10;
                    int filled = (int) Math.round(frac * pips);
                    int pipY = y + 10;
                    for (int i = 0; i < pips; i++) {
                        drawIconSlot(g, entry.icon(), 8 + i * 10, pipY, i < filled, color);
                    }
                    if (entry.showValue()) {
                        g.text(mc.font, Component.literal(format(entry.value())),
                                8 + pips * 10 + PAD, pipY, 0xFFFFFFFF);
                    }
                    y += 10 + 8 + PAD;
                }
                default -> {   // bar - the default when a definition names no hud type
                    g.text(mc.font, Component.literal(entry.name()), 8, y, 0xFFFFFFFF);
                    int barY = y + 10;
                    drawBar(g, 8, barY, BAR_W, frac, color);
                    if (entry.showValue()) {
                        g.text(mc.font, Component.literal(format(entry.value())),
                                8 + BAR_W + PAD, barY, 0xFFFFFFFF);
                    }
                    y += 10 + 7 + PAD;
                }
            }
        }
    }

    /** One icon slot: creator texture if present, colored pip otherwise.
     *  Unfilled slots render dimmed. */
    public static void drawIconSlot(GuiGraphicsExtractor g, String icon,
                                    int x, int y, boolean filled, int rgb) {
        Identifier tex = icon.isEmpty() ? null : textureFor(icon);
        if (tex != null) {
            // NOTE drift: the blit-family call. Historical shape:
            //   g.blit(tex, x, y, 0, 0, 8, 8, 8, 8);
            // (texture, x, y, u, v, w, h, texW, texH) — newer versions may want
            // a RenderType/function first argument or use blitSprite. Type g.bl
            // and let autocomplete arbitrate; the intent is "draw this texture
            // scaled to 8x8 at x,y".
            g.blit(tex, x, y, 0, 0, 8, 8, 8, 8);
            if (!filled) {
                g.fill(x, y, x + 8, y + 8, 0xA0000000);   // dim overlay for empty slots
            }
        } else {
            g.fill(x, y, x + 8, y + 8, 0xA0000000);
            if (filled) {
                g.fill(x + 1, y + 1, x + 7, y + 7, 0xFF000000 | rgb);
            }
        }
    }

    /**
     * Expands a datapack icon id into the actual texture path.
     * @return null if the string is not a valid identifier, so the caller falls back to a pip
     */
    private static Identifier textureFor(String icon) {
        Identifier id = Identifier.tryParse(icon);
        if (id == null) return null;
        // "mypack:icons/corruption" → assets/mypack/textures/icons/corruption.png
        return Identifier.fromNamespaceAndPath(id.getNamespace(),
                "textures/" + id.getPath() + ".png");
    }

    /** Applies the stat's visibility mode; unknown modes are treated as "always". */
    private static boolean visible(RpgPayloads.StatEntry entry) {
        return switch (entry.visibility()) {
            case "never" -> false;
            case "when_non_default" -> entry.value() != entry.defaultValue();
            case "above_value" -> entry.value() > entry.visibilityValue();
            case "below_value" -> entry.value() < entry.visibilityValue();
            default -> true;   // "always", "when_changed" (v2)
        };
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

    /** Parses "#RRGGBB" into a packed RGB int; falls back to white on anything malformed. */
    private static int parseColor(String hex) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    /** Whole numbers print without a decimal point, everything else with one digit. */
    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }

    /** Draws a vanilla-boss-bar-styled bar: border, recessed bg, beveled fill, notches. */
    public static void drawBar(GuiGraphicsExtractor g, int x, int y, int w, double frac, int rgb) {
        int h = 7;   // 5px bar + 1px border each side

        // border
        g.fill(x, y, x + w, y + h, 0xFF000000);
        // recessed background (dark with a darker top row, like vanilla's empty bar)
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF3A3A3A);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF262626);

        // fill with the boss-bar bevel: light top, base middle, dark bottom
        int fillW = (int) Math.round((w - 2) * Math.max(0, Math.min(1, frac)));
        if (fillW > 0) {
            int base = 0xFF000000 | rgb;
            int light = 0xFF000000 | lighten(rgb, 0.45f);
            int dark = 0xFF000000 | darken(rgb, 0.35f);
            int fx = x + 1, fy = y + 1, fh = h - 2;
            g.fill(fx, fy, fx + fillW, fy + fh, base);
            g.fill(fx, fy, fx + fillW, fy + 2, light);              // top highlight (2px)
            g.fill(fx, fy + fh - 1, fx + fillW, fy + fh, dark);     // bottom shade
        }

        // segment notches every quarter, drawn over everything (subtle)
        for (int i = 1; i < 4; i++) {
            int nx = x + 1 + (w - 2) * i / 4;
            g.fill(nx, y + 1, nx + 1, y + h - 1, 0x40000000);
        }
    }

    /** Blends the colour towards white by {@code amount} (0..1), for the bar's top highlight. */
    private static int lighten(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r = Math.min(255, (int) (r + (255 - r) * amount));
        gr = Math.min(255, (int) (gr + (255 - gr) * amount));
        b = Math.min(255, (int) (b + (255 - b) * amount));
        return (r << 16) | (gr << 8) | b;
    }

    /** Blends the colour towards black by {@code amount} (0..1), for the bar's bottom shade. */
    private static int darken(int rgb, float amount) {
        int r = (int) (((rgb >> 16) & 0xFF) * (1 - amount));
        int gr = (int) (((rgb >> 8) & 0xFF) * (1 - amount));
        int b = (int) ((rgb & 0xFF) * (1 - amount));
        return (r << 16) | (gr << 8) | b;
    }
}
