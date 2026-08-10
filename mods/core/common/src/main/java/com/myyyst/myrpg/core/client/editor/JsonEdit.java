package com.myyyst.myrpg.core.client.editor;

import com.google.gson.JsonObject;

/**
 * Dotted-path helpers over the working JsonObject ("display.name",
 * "value.min"). Setters create intermediate objects; removing a value
 * that matches the schema default keeps saved JSON minimal-ish (v1:
 * we just write what the user typed — cleanliness via codec re-encode
 * happens server-side in OverlaySaver anyway).
 */
public final class JsonEdit {

    public static String getString(JsonObject root, String path, String fallback) {
        JsonObject parent = walk(root, path, false);
        String key = leaf(path);
        return parent != null && parent.has(key) ? parent.get(key).getAsString() : fallback;
    }

    public static double getDouble(JsonObject root, String path, double fallback) {
        JsonObject parent = walk(root, path, false);
        String key = leaf(path);
        try {
            return parent != null && parent.has(key) ? parent.get(key).getAsDouble() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public static boolean getBool(JsonObject root, String path, boolean fallback) {
        JsonObject parent = walk(root, path, false);
        String key = leaf(path);
        try {
            return parent != null && parent.has(key) ? parent.get(key).getAsBoolean() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public static void set(JsonObject root, String path, String value) {
        walk(root, path, true).addProperty(leaf(path), value);
    }

    public static void set(JsonObject root, String path, double value) {
        walk(root, path, true).addProperty(leaf(path), value);
    }

    public static void set(JsonObject root, String path, boolean value) {
        walk(root, path, true).addProperty(leaf(path), value);
    }

    public static void remove(JsonObject root, String path) {
        JsonObject parent = walk(root, path, false);
        if (parent != null) parent.remove(leaf(path));
    }

    private static JsonObject walk(JsonObject root, String path, boolean create) {
        String[] parts = path.split("\\.");
        JsonObject current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!current.has(parts[i]) || !current.get(parts[i]).isJsonObject()) {
                if (!create) return null;
                current.add(parts[i], new JsonObject());
            }
            current = current.getAsJsonObject(parts[i]);
        }
        return current;
    }

    private static String leaf(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    private JsonEdit() {}
}