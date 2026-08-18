package com.myyyst.myrpg.core.client.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.myyyst.myrpg.core.network.RpgPayloads;

import java.util.ArrayList;
import java.util.List;

/**
 * The library's in-memory model: one entry per stat, dirty-tracked.
 *
 * <p>The editor never mutates server state directly. It edits this scratch copy of every
 * definition, and only a "save" action sends the JSON back through {@code ClientEditorNet}.
 * That is what makes cancelling free and lets the UI show which entries changed.</p>
 */
public final class StatWorkingSet {

    /** One stat being edited. */
    public static final class Entry {
        /** Namespaced id, editable because "save as" can rename an entry. */
        public String statId;
        /** Parsed JSON being edited; null when the file failed to parse. */
        public JsonObject json;
        /** True once edited and not yet saved - drives the EDITED chip in the list. */
        public boolean dirty;
        public String parseError;   // null when valid

        public JsonObject pristine;   // snapshot taken when the editor opens

        public Entry(String statId, JsonObject json) {
            this.statId = statId;
            this.json = json;
        }

        /** "min - max" for the list row, using the codec defaults when unset. */
        public String rangeLabel() {
            if (json == null || !json.has("value")) return "0 - 100";
            JsonObject v = json.getAsJsonObject("value");
            double min = v.has("min") ? v.get("min").getAsDouble() : 0;
            double max = v.has("max") ? v.get("max").getAsDouble() : 100;
            return trim(min) + " - " + trim(max);
        }

        /**
         * The declared display name, or a capitalised version of the id path as a fallback
         * ("mypack:corruption" -> "Corruption").
         */
        public String displayName() {
            if (json != null && json.has("display")) {
                JsonObject d = json.getAsJsonObject("display");
                if (d.has("name")) return d.get("name").getAsString();
            }
            String path = statId.contains(":") ? statId.split(":", 2)[1] : statId;
            return path.substring(0, 1).toUpperCase() + path.substring(1);
        }

        /** Drops the ".0" from whole numbers so ranges read "0 - 100", not "0.0 - 100.0". */
        private static String trim(double d) {
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }
    }

    /** Every stat in the editor, sorted by id. */
    public final List<Entry> entries = new ArrayList<>();

    /**
     * Builds the working set from the definitions the server sent.
     * A file that fails to parse still becomes an entry, with its error recorded, so the
     * library can show the problem instead of silently dropping the stat.
     */
    public static StatWorkingSet from(RpgPayloads.OpenStatEditor payload) {
        StatWorkingSet set = new StatWorkingSet();
        for (RpgPayloads.StatFile file : payload.stats()) {
            Entry entry;
            try {
                entry = new Entry(file.statId(), JsonParser.parseString(file.json()).getAsJsonObject());
            } catch (Exception e) {
                entry = new Entry(file.statId(), null);
                entry.parseError = e.getMessage();
            }
            set.entries.add(entry);
        }
        set.entries.sort((a, b) -> a.statId.compareTo(b.statId));
        return set;
    }
}