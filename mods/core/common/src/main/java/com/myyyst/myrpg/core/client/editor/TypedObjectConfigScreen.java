package com.myyyst.myrpg.core.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.myyyst.myrpg.core.client.editor.EffectSchemas.FieldSpec;
import static com.myyyst.myrpg.core.client.editor.EffectSchemas.FieldType;

/**
 * Schema-driven form for one typed JSON object (effect, action, or
 * condition). existing == null → add mode (appends to targetList on
 * confirm); otherwise edits in place.
 *
 * Special field keys:
 *   __var_value          → writes a VarValue object under "value"
 *   __var_value_default  → writes a VarValue object under "default"
 *   (number-parse-first: "5" → {"number":5}, "yes" → {"string":"yes"})
 * Cycle options "true"/"false" are written as real booleans.
 */
public class TypedObjectConfigScreen extends Screen {

    private final Screen parent;
    /** Array the new object is appended to; only used in add mode. */
    private final JsonArray targetList;
    /** Value written to the "type" field - the dispatch key the codec reads. */
    private final String typeId;
    private final String label;
    /** Fields to render, taken from the schema of this type. */
    private final List<FieldSpec> fields;
    /** Object being edited, or null when adding a new one. */
    @Nullable private final JsonObject existing;
    /** Notifies the host screen that the definition changed. */
    private final Runnable onDirty;

    /** Text/number controls, keyed by their JSON key. */
    private final Map<String, EditBox> boxes = new HashMap<>();
    /** Current index into {@code options} for each CYCLE field. */
    private final Map<String, Integer> cycles = new HashMap<>();
    /** Panel geometry; the height grows with the number of fields. */
    private int px, py, pw, ph;

    public TypedObjectConfigScreen(Screen parent, JsonArray targetList,
                                   String typeId, String label, List<FieldSpec> fields,
                                   @Nullable JsonObject existing, Runnable onDirty) {
        super(Component.literal(label));
        this.parent = parent;
        this.targetList = targetList;
        this.typeId = typeId;
        this.label = label;
        this.fields = fields;
        this.existing = existing;
        this.onDirty = onDirty;
    }

    /** Builds one control per field, pre-filled from the existing object if any. */
    @Override
    protected void init() {
        pw = 280;
        ph = Math.max(100, 90 + fields.size() * 40);   // floor keeps field-less types usable
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        int fy = py + 40;
        for (FieldSpec field : fields) {
            if (field.type() == FieldType.CYCLE) {
                cycles.put(field.key(), existingCycleIndex(field));
            } else {
                EditBox box = new EditBox(font, px + PanelStyle.GRID, fy + 10,
                        pw - PanelStyle.GRID * 2, 18, Component.empty());
                box.setValue(existingValue(field));
                addRenderableWidget(box);
                boxes.put(field.key(), box);
            }
            fy += 40;
        }
    }

    /** @return index of the stored value among the field's options, or 0 if it is absent. */
    private int existingCycleIndex(FieldSpec field) {
        if (existing != null && existing.has(field.key())) {
            String current = existing.get(field.key()).getAsString();
            // booleans arrive as true/false primitives; getAsString covers both
            for (int i = 0; i < field.options().length; i++) {
                if (field.options()[i].equals(current)) return i;
            }
        }
        return 0;
    }

    /**
     * Text to prefill a field with. Handles the two special cases: VarValue fields, which
     * live under a different key and are wrapped in an object, and numeric primitives,
     * which are trimmed so "5" does not display as "5.0".
     */
    private String existingValue(FieldSpec field) {
        if (existing == null) return field.fallback();
        // VarValue specials read back from their real JSON location
        if (field.key().startsWith("__var_value")) {
            String realKey = field.key().equals("__var_value") ? "value" : "default";
            if (existing.has(realKey) && existing.get(realKey).isJsonObject()) {
                JsonObject varValue = existing.getAsJsonObject(realKey);
                if (varValue.has("number")) return trimNum(varValue.get("number").getAsDouble());
                if (varValue.has("string")) return varValue.get("string").getAsString();
            }
            return field.fallback();
        }
        if (existing.has(field.key())) {
            var element = existing.get(field.key());
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return trimNum(element.getAsDouble());
            }
            return element.getAsString();
        }
        return field.fallback();
    }

    /** Drops the ".0" from whole numbers so fields show what the author typed. */
    private static String trimNum(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, px, py, pw, ph);
        g.text(font, Component.literal(label.toUpperCase()), px + PanelStyle.GRID, py + PanelStyle.GRID, PanelStyle.TEXT);
        g.text(font, Component.literal(typeId), px + PanelStyle.GRID, py + PanelStyle.GRID + 11, PanelStyle.TEXT_DIM);

        int fy = py + 40;
        for (FieldSpec field : fields) {
            g.text(font, Component.literal(field.label()), px + PanelStyle.GRID, fy, PanelStyle.TEXT_DIM);
            if (field.type() == FieldType.CYCLE) {
                String value = field.options()[cycles.get(field.key())];
                PanelStyle.button(g, font, value, px + PanelStyle.GRID, fy + 8, pw - PanelStyle.GRID * 2,
                        PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID, fy + 8,
                                pw - PanelStyle.GRID * 2, PanelStyle.CONTROL_H), false);
            }
            fy += 40;
        }

        if (fields.isEmpty()) {
            g.text(font, Component.literal("No configuration needed."),
                    px + PanelStyle.GRID, py + 44, PanelStyle.TEXT_DIM);
        }

        PanelStyle.button(g, font, "CANCEL", px + PanelStyle.GRID, py + ph - 32, 80,
                PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID, py + ph - 32, 80, PanelStyle.CONTROL_H), false);
        String confirm = existing == null ? "ADD" : "APPLY";
        PanelStyle.button(g, font, confirm, px + pw - 90 - PanelStyle.GRID, py + ph - 32, 90,
                PanelStyle.hit(mouseX, mouseY, px + pw - 90 - PanelStyle.GRID, py + ph - 32, 90, PanelStyle.CONTROL_H), true);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int fy = py + 40;
        for (FieldSpec field : fields) {
            if (field.type() == FieldType.CYCLE
                    && PanelStyle.hit(mx, my, px + PanelStyle.GRID, fy + 8,
                    pw - PanelStyle.GRID * 2, PanelStyle.CONTROL_H)) {
                cycles.merge(field.key(), 1, (a, b) -> (a + 1) % field.options().length);
                return true;
            }
            fy += 40;
        }
        if (PanelStyle.hit(mx, my, px + PanelStyle.GRID, py + ph - 32, 80, PanelStyle.CONTROL_H)) {
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        if (PanelStyle.hit(mx, my, px + pw - 90 - PanelStyle.GRID, py + ph - 32, 90, PanelStyle.CONTROL_H)) {
            apply();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Writes the form back into the JSON and returns to the parent screen.
     *
     * <p>Three conversions happen here, each because the codec expects a specific JSON
     * shape rather than the string a text field naturally produces: booleans, numbers,
     * and the wrapped VarValue objects.</p>
     */
    private void apply() {
        JsonObject object = existing != null ? existing : new JsonObject();
        object.addProperty("type", typeId);
        for (FieldSpec field : fields) {
            switch (field.type()) {
                case CYCLE -> {
                    String value = field.options()[cycles.get(field.key())];
                    // A cycle over "true"/"false" is really a checkbox: write a real boolean.
                    if (value.equals("true") || value.equals("false")) {
                        object.addProperty(field.key(), Boolean.parseBoolean(value));
                    } else {
                        object.addProperty(field.key(), value);
                    }
                }
                case NUMBER -> {
                    // Unparseable input leaves the previous value untouched.
                    try {
                        object.addProperty(field.key(),
                                Double.parseDouble(boxes.get(field.key()).getValue().trim()));
                    } catch (NumberFormatException ignored) { }
                }
                case STRING -> {
                    String raw = boxes.get(field.key()).getValue().trim();
                    if (field.key().startsWith("__var_value")) {
                        // VarValue: one text box, two possible JSON shapes. Numbers win
                        // when the text parses as one, so "5" is a number and "yes" a string.
                        String realKey = field.key().equals("__var_value") ? "value" : "default";
                        if (raw.isEmpty()) {
                            object.remove(realKey);
                        } else {
                            JsonObject varValue = new JsonObject();
                            try {
                                varValue.addProperty("number", Double.parseDouble(raw));
                            } catch (NumberFormatException e) {
                                varValue.addProperty("string", raw);
                            }
                            object.add(realKey, varValue);
                        }
                    } else if (!raw.isEmpty()) {
                        object.addProperty(field.key(), raw);
                    }
                }
            }
        }
        if (existing == null) {
            targetList.add(object);
        }
        onDirty.run();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    /** Editing must not pause a singleplayer world. */
    @Override
    public boolean isPauseScreen() { return false; }
}