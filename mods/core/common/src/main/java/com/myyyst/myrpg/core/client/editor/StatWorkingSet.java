package com.myyyst.myrpg.core.client.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.myyyst.myrpg.core.network.RpgPayloads;

import java.util.ArrayList;
import java.util.List;

/** The library's in-memory model: one entry per stat, dirty-tracked. */
public final class StatWorkingSet {

    public static final class Entry {
        public String statId;
        public JsonObject json;
        public boolean dirty;
        public String parseError;   // null when valid

        public Entry(String statId, JsonObject json) {
            this.statId = statId;
            this.json = json;
        }

        public String rangeLabel() {
            if (json == null || !json.has("value")) return "0 - 100";
            JsonObject v = json.getAsJsonObject("value");
            double min = v.has("min") ? v.get("min").getAsDouble() : 0;
            double max = v.has("max") ? v.get("max").getAsDouble() : 100;
            return trim(min) + " - " + trim(max);
        }

        public String displayName() {
            if (json != null && json.has("display")) {
                JsonObject d = json.getAsJsonObject("display");
                if (d.has("name")) return d.get("name").getAsString();
            }
            String path = statId.contains(":") ? statId.split(":", 2)[1] : statId;
            return path.substring(0, 1).toUpperCase() + path.substring(1);
        }

        private static String trim(double d) {
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }
    }

    public final List<Entry> entries = new ArrayList<>();

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