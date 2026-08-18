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

    /** Reads a string at a dotted path, or {@code fallback} if absent. */
    public static String getString(JsonObject root, String path, String fallback) {
        JsonObject parent = walk(root, path, false);
        String key = leaf(path);
        return parent != null && parent.has(key) ? parent.get(key).getAsString() : fallback;
    }

    /** Reads a number; a wrong-typed value falls back rather than throwing at the user. */
    public static double getDouble(JsonObject root, String path, double fallback) {
        JsonObject parent = walk(root, path, false);
        String key = leaf(path);
        try {
            return parent != null && parent.has(key) ? parent.get(key).getAsDouble() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Reads a boolean; a wrong-typed value falls back. */
    public static boolean getBool(JsonObject root, String path, boolean fallback) {
        JsonObject parent = walk(root, path, false);
        String key = leaf(path);
        try {
            return parent != null && parent.has(key) ? parent.get(key).getAsBoolean() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Writes a string, creating any missing parent objects along the path. */
    public static void set(JsonObject root, String path, String value) {
        walk(root, path, true).addProperty(leaf(path), value);
    }

    public static void set(JsonObject root, String path, double value) {
        walk(root, path, true).addProperty(leaf(path), value);
    }

    public static void set(JsonObject root, String path, boolean value) {
        walk(root, path, true).addProperty(leaf(path), value);
    }

    /** Deletes the leaf key; missing parents are simply ignored. */
    public static void remove(JsonObject root, String path) {
        JsonObject parent = walk(root, path, false);
        if (parent != null) parent.remove(leaf(path));
    }

    /**
     * Follows all but the last path segment.
     *
     * @param create true to build missing (or wrong-typed) intermediate objects
     * @return the object holding the leaf key, or null when {@code create} is false and
     *         the path does not exist
     */
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

    /** Last segment of a dotted path - the key actually read or written. */
    private static String leaf(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    /** Static-only helper: never instantiated. */
    private JsonEdit() {}
}