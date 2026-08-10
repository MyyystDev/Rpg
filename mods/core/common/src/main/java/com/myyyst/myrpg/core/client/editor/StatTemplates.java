package com.myyyst.myrpg.core.client.editor;

import com.google.gson.JsonObject;

public enum StatTemplates {
    RESOURCE("Resource", "For pools that spend and refill, like mana or stamina.",
            0, 100, 100, false, true),
    ACCUMULATION("Accumulation", "For values that build up over time and become more dangerous.",
            0, 100, 0, false, false),
    BIPOLAR("Bipolar", "For values that swing both ways, like reputation or morality.",
            -100, 100, 0, false, false),
    STACKS("Stacks", "For small integer counters, like bleeding or combo stacks.",
            0, 10, 0, false, false),
    EMPTY("Empty", "Only the minimum required configuration.",
            0, 100, 0, false, false);

    public final String label, description;
    public final double min, max, defaultValue;
    public final boolean decimal, hudBar;

    StatTemplates(String label, String description, double min, double max,
                  double defaultValue, boolean decimal, boolean hudBar) {
        this.label = label; this.description = description;
        this.min = min; this.max = max; this.defaultValue = defaultValue;
        this.decimal = decimal; this.hudBar = hudBar;
    }

    /** Builds the starting JSON for this template + the dialog's values. */
    public JsonObject build(String displayName, double min, double max, double defaultValue) {
        JsonObject root = new JsonObject();
        if (this != EMPTY) {
            JsonObject display = new JsonObject();
            display.addProperty("name", displayName);
            root.add("display", display);
        }
        JsonObject value = new JsonObject();
        value.addProperty("default", defaultValue);
        value.addProperty("min", min);
        value.addProperty("max", max);
        if (decimal) value.addProperty("decimal", true);
        root.add("value", value);
        if (hudBar) {
            JsonObject hud = new JsonObject();
            hud.addProperty("visible", true);
            hud.addProperty("type", "bar");
            root.add("hud", hud);
        }
        return root;
    }
}