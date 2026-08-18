package com.myyyst.myrpg.core.client;

import com.myyyst.myrpg.core.network.RpgPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Active custom effects, drawn as chips in the top-right corner (below the
 * vanilla potion icons): colored/icon square, stack count, level, and a
 * remaining-time label — all gated by the effect's display_options.
 */
public final class EffectHudOverlay {

    private static final int CHIP = 18;      // chip square, px
    private static final int GAP = 3;
    private static final int ROW_H = CHIP + 11;   // chip + duration label
    private static final int TOP = 34;       // below vanilla effect icons

    /** Draws every active effect as a chip, filling right-to-left and wrapping downwards. */
    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ClientEffectCache.entries().isEmpty()) return;

        int right = g.guiWidth() - 8;
        int x = right;
        int y = TOP;

        for (ClientEffectCache.ActiveEffect active : ClientEffectCache.entries()) {
            RpgPayloads.EffectEntry entry = active.entry;
            x -= CHIP;
            if (x < 8) {   // wrap to a new row
                x = right - CHIP;
                y += ROW_H;
            }
            drawChip(g, mc, entry, active.remaining, x, y);
            x -= GAP;
        }
    }

    /**
     * Draws one effect chip at (x, y).
     *
     * <p>Also used by the editor to preview an effect, which is why it is public and takes
     * an explicit position instead of reading the layout cursor.</p>
     *
     * @param remaining ticks left, or negative for an infinite effect (no timer drawn)
     */
    public static void drawChip(GuiGraphicsExtractor g, Minecraft mc,
                                 RpgPayloads.EffectEntry entry, int remaining,
                                 int x, int y) {
        int rgb = parseColor(entry.color());

        // recessed dark chip with a subtle category-colored border
        g.fill(x, y, x + CHIP, y + CHIP, 0xC0101014);
        g.fill(x, y, x + CHIP, y + 1, 0xFF000000 | darken(rgb));
        g.fill(x, y + CHIP - 1, x + CHIP, y + CHIP, 0xFF000000 | darken(rgb));
        g.fill(x, y, x + 1, y + CHIP, 0xFF000000 | darken(rgb));
        g.fill(x + CHIP - 1, y, x + CHIP, y + CHIP, 0xFF000000 | darken(rgb));

        Identifier tex = entry.showIcon() ? textureFor(entry.icon()) : null;
        if (tex != null) {
            g.blit(tex, x + 1, y + 1, 0, 0, CHIP - 2, CHIP - 2, CHIP - 2, CHIP - 2);
        } else {
            // fallback: colored inner square + first letter of the name
            g.fill(x + 2, y + 2, x + CHIP - 2, y + CHIP - 2, 0xFF000000 | rgb);
            String letter = entry.name().isEmpty() ? "?"
                    : entry.name().substring(0, 1).toUpperCase();
            int lw = mc.font.width(letter);
            g.text(mc.font, Component.literal(letter),
                    x + (CHIP - lw) / 2, y + (CHIP - 8) / 2, 0xFF101014);
        }

        // stacks: bottom-right corner
        if (entry.showStacks() && entry.stacks() > 1) {
            String stacks = String.valueOf(entry.stacks());
            g.text(mc.font, Component.literal(stacks),
                    x + CHIP - mc.font.width(stacks), y + CHIP - 8, 0xFFFFFFFF);
        }

        // level: top-left corner
        if (entry.showLevel() && entry.level() > 1) {
            g.text(mc.font, Component.literal(String.valueOf(entry.level())),
                    x + 1, y + 1, 0xFFFFE080);
        }

        // remaining time, centered under the chip
        if (entry.showDuration() && remaining >= 0) {
            String time = formatTime(remaining);
            int tw = mc.font.width(time);
            g.text(mc.font, Component.literal(time),
                    x + (CHIP - tw) / 2, y + CHIP + 2, 0xFFDADAE0);
        }
    }

    /** 20 ticks = 1 second; shown as "M:SS" past a minute, otherwise "Ns". */
    private static String formatTime(int ticks) {
        int seconds = ticks / 20;
        if (seconds >= 60) {
            return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
        }
        return seconds + "s";
    }

    /** Accepts "mypack:effect/frozen" or the doc's "mypack:textures/effect/frozen.png". */
    private static Identifier textureFor(String icon) {
        if (icon.isEmpty()) return null;
        Identifier id = Identifier.tryParse(icon);
        if (id == null) return null;
        String path = id.getPath();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path = path + ".png";
        return Identifier.fromNamespaceAndPath(id.getNamespace(), path);
    }

    /** Halves each channel, giving the chip border a dimmer shade of the effect colour. */
    private static int darken(int rgb) {
        int r = (rgb >> 16 & 0xFF) / 2, gr = (rgb >> 8 & 0xFF) / 2, b = (rgb & 0xFF) / 2;
        return r << 16 | gr << 8 | b;
    }

    /** Parses "#RRGGBB"; falls back to the neutral grey used for uncategorised effects. */
    private static int parseColor(String hex) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return 0xA8A8B8;
        }
    }

    /** Static-only renderer: never instantiated. */
    private EffectHudOverlay() {}
}
