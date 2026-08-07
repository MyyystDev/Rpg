package com.myyyst.myrpg.core.util;

import net.minecraft.network.chat.Component;

public final class TextResolver {

    private TextResolver() {}

    public static Component resolve(String text) {
        return looksLikeKey(text) ? Component.translatable(text) : Component.literal(text);
    }

    public static boolean looksLikeKey(String text) {
        if (text.isEmpty()) return false;
        if (text.indexOf(' ') >= 0) return false;
        if (text.indexOf('.') < 0) return false;
        char last = text.charAt(text.length() - 1);
        return last != '.' && last != '!' && last != '?';
    }
}