package com.myyyst.myrpg.core.util;

import net.minecraft.network.chat.Component;

/**
 * Turns a raw string from a datapack into a displayable {@link Component}.
 *
 * <p>Pack authors may write either a literal message ("You feel enraged!") or a translation
 * key ("myrpg.effect.rage.applied"). Rather than forcing them to declare which is which,
 * this class guesses using {@link #looksLikeKey(String)}.</p>
 */
public final class TextResolver {

    /** Static-only helper: never instantiated. */
    private TextResolver() {}

    /** @return a translatable component when {@code text} looks like a key, otherwise a literal one. */
    public static Component resolve(String text) {
        return looksLikeKey(text) ? Component.translatable(text) : Component.literal(text);
    }

    /**
     * Heuristic for "is this a translation key rather than a sentence?".
     * A key has no spaces, contains at least one dot, and does not end in sentence
     * punctuation - so "myrpg.foo.bar" is a key while "Careful." or "Watch out!" are not.
     */
    public static boolean looksLikeKey(String text) {
        if (text.isEmpty()) return false;
        if (text.indexOf(' ') >= 0) return false;      // keys never contain spaces
        if (text.indexOf('.') < 0) return false;       // keys are dot-separated
        char last = text.charAt(text.length() - 1);
        return last != '.' && last != '!' && last != '?';   // trailing punctuation => prose
    }
}