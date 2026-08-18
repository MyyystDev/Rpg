package com.myyyst.myrpg.core.client.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.myyyst.myrpg.core.network.RpgPayloads;

import java.util.ArrayList;
import java.util.List;

/**
 * The effect library's in-memory model: one entry per effect, dirty-tracked.
 * Effect-side twin of {@link StatWorkingSet}; see that class for the editing model.
 */
public final class EffectWorkingSet {

    /** One effect being edited. */
    public static final class Entry {
        /** Namespaced id, editable because "save as" can rename an entry. */
        public String effectId;
        /** Parsed JSON being edited; null when the file failed to parse. */
        public JsonObject json;
        /** True once edited and not yet saved. */
        public boolean dirty;
        public String parseError;   // null when valid

        public JsonObject pristine;   // snapshot taken when the editor opens

        public Entry(String effectId, JsonObject json) {
            this.effectId = effectId;
            this.json = json;
        }

        /** Declared name, or a capitalised id path as fallback. */
        public String displayName() {
            if (json != null && json.has("display")) {
                JsonObject d = json.getAsJsonObject("display");
                if (d.has("name")) return d.get("name").getAsString();
            }
            String path = effectId.contains(":") ? effectId.split(":", 2)[1] : effectId;
            return path.isEmpty() ? "?" : path.substring(0, 1).toUpperCase() + path.substring(1);
        }

        /** beneficial / harmful / neutral; drives the row's colour. */
        public String category() {
            return json == null ? "neutral" : JsonEdit.getString(json, "category", "neutral");
        }

        /** Right-column summary: "200t x5" / "INFINITE" etc. */
        public String summaryLabel() {
            if (json == null) return "?";
            if ("infinite".equals(JsonEdit.getString(json, "duration.type", "timed"))) {
                return "INFINITE";
            }
            long ticks = (long) JsonEdit.getDouble(json, "duration.default", 200);
            String label = ticks + "t";
            long max = (long) JsonEdit.getDouble(json, "stacking.max_stacks", 1);
            if (max > 1) label += "  x" + max;
            return label;
        }
    }

    /** Every effect in the editor, sorted by id. */
    public final List<Entry> entries = new ArrayList<>();

    /**
     * Builds the working set from the definitions the server sent.
     * Note the payload reuses {@code StatFile}, so {@code file.statId()} here is the effect id.
     */
    public static EffectWorkingSet from(RpgPayloads.OpenEffectEditor payload) {
        EffectWorkingSet set = new EffectWorkingSet();
        for (RpgPayloads.StatFile file : payload.effects()) {
            Entry entry;
            try {
                entry = new Entry(file.statId(), JsonParser.parseString(file.json()).getAsJsonObject());
            } catch (Exception e) {
                entry = new Entry(file.statId(), null);
                entry.parseError = e.getMessage();
            }
            set.entries.add(entry);
        }
        set.entries.sort((a, b) -> a.effectId.compareTo(b.effectId));
        return set;
    }
}
