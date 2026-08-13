package com.myyyst.myrpg.entities.client.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.myyyst.myrpg.core.client.editor.PanelStyle;
import com.myyyst.myrpg.entities.network.EntitiesPayloads;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Client-side working copies of every entity definition (mirrors StatWorkingSet). */
public class EntityWorkingSet {

    public static class Entry {
        public String entityId;
        public @Nullable JsonObject json;
        public @Nullable String parseError;
        public boolean dirty;

        public Entry(String entityId, @Nullable JsonObject json) {
            this.entityId = entityId;
            this.json = json;
        }

        public String displayName() {
            if (json != null && json.has("display")
                    && json.getAsJsonObject("display").has("name")) {
                return json.getAsJsonObject("display").get("name").getAsString();
            }
            return entityId.contains(":") ? entityId.split(":", 2)[1] : entityId;
        }

        public List<String> tags() {
            List<String> tags = new ArrayList<>();
            if (json != null && json.has("tags")) {
                try {
                    for (var t : json.getAsJsonArray("tags")) {
                        String tag = t.getAsString();
                        tags.add((tag.contains(":") ? tag.split(":", 2)[1] : tag).toUpperCase());
                    }
                } catch (Exception ignored) {}
            }
            if (tags.isEmpty()) tags.add(hostile() ? "HOSTILE" : "NPC");
            return tags;
        }

        public boolean hostile() {
            return json != null && json.has("targeting")
                    && json.get("targeting").isJsonArray()
                    && !json.getAsJsonArray("targeting").isEmpty();
        }

        public boolean boss() {
            return (json != null && json.has("boss_bar")) || tagsRaw().contains("boss");
        }

        private String tagsRaw() {
            return json != null && json.has("tags") ? json.get("tags").toString().toLowerCase() : "";
        }

        public int accent() {
            return boss() ? PanelStyle.ACCENT : hostile() ? PanelStyle.ERROR : PanelStyle.EDITED;
        }
    }

    public final List<Entry> entries = new ArrayList<>();

    public EntityWorkingSet(EntitiesPayloads.OpenEntityBrowser payload) {
        for (EntitiesPayloads.EntityFile file : payload.entities()) {
            Entry entry = new Entry(file.entityId(), null);
            try {
                entry.json = JsonParser.parseString(file.json()).getAsJsonObject();
            } catch (Exception e) {
                entry.parseError = e.getMessage();
            }
            entries.add(entry);
        }
        entries.sort((a, b) -> a.entityId.compareTo(b.entityId));
    }
}
