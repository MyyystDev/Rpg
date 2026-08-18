package com.myyyst.myrpg.core.client.editor;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Schema-driven form for one effect. existing == null → add mode.
 *
 * <p>Nothing about this screen is specific to a particular effect type: it walks the
 * {@link EffectSchemas.EffectSchema} it was handed, creates one control per field, and
 * writes the values back under those keys. Adding a new stage-effect type therefore needs
 * a schema entry, not a new screen.</p>
 *
 * <p>In edit mode it mutates the {@code existing} object in place, so the change is already
 * part of the stage; in add mode the new object is appended on confirm.</p>
 */
public class EffectConfigScreen extends Screen {

    private final StatEditorScreen parent;
    /** Stage that owns the effect list; only touched in add mode. */
    private final JsonObject stage;
    /** Describes the fields to render. */
    private final EffectSchemas.EffectSchema schema;
    /** The effect being edited, or null when adding a new one. */
    @Nullable private final JsonObject existing;

    /** Text/number controls, keyed by their JSON key. */
    private final Map<String, EditBox> boxes = new HashMap<>();
    /** Current index into {@code options} for each CYCLE field. */
    private final Map<String, Integer> cycles = new HashMap<>();
    /** Panel geometry; the height grows with the number of fields. */
    private int px, py, pw, ph;

    public EffectConfigScreen(StatEditorScreen parent, JsonObject stage,
                              EffectSchemas.EffectSchema schema, @Nullable JsonObject existing) {
        super(Component.literal(schema.label()));
        this.parent = parent;
        this.stage = stage;
        this.schema = schema;
        this.existing = existing;
    }

    /** Builds one control per schema field, pre-filled from the existing object if any. */
    @Override
    protected void init() {
        pw = 280;
        ph = 90 + schema.fields().size() * 40;   // header + fields + button strip
        px = (width - pw) / 2; py = (height - ph) / 2;

        int fy = py + 40;
        for (var field : schema.fields()) {
            if (field.type() == EffectSchemas.FieldType.CYCLE) {
                // Cycles are not widgets: only the selected index is kept, and clicking
                // the button advances it.
                int idx = 0;
                if (existing != null && existing.has(field.key())) {
                    String current = existing.get(field.key()).getAsString();
                    for (int i = 0; i < field.options().length; i++) {
                        if (field.options()[i].equals(current)) idx = i;
                    }
                }
                cycles.put(field.key(), idx);
            } else {
                EditBox box = new EditBox(font, px + PanelStyle.GRID, fy + 10, pw - PanelStyle.GRID * 2, 18, Component.empty());
                String value = existing != null && existing.has(field.key())
                        ? existing.get(field.key()).getAsString()
                        : field.fallback();
                // NOTE: numbers arrive via getAsString on primitives — fine for display.
                box.setValue(existing != null && existing.has(field.key())
                        ? existing.get(field.key()).toString().replace("\"", "")
                        : field.fallback());
                addRenderableWidget(box);
                boxes.put(field.key(), box);
            }
            fy += 40;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, px, py, pw, ph);
        g.text(font, Component.literal(schema.label().toUpperCase()), px + PanelStyle.GRID, py + PanelStyle.GRID, PanelStyle.TEXT);
        g.text(font, Component.literal(schema.typeId()), px + PanelStyle.GRID, py + PanelStyle.GRID + 11, PanelStyle.TEXT_DIM);

        int fy = py + 40;
        for (var field : schema.fields()) {
            g.text(font, Component.literal(field.label()), px + PanelStyle.GRID, fy, PanelStyle.TEXT_DIM);
            if (field.type() == EffectSchemas.FieldType.CYCLE) {
                String value = field.options()[cycles.get(field.key())];
                PanelStyle.button(g, font, value, px + PanelStyle.GRID, fy + 8, pw - PanelStyle.GRID * 2,
                        PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID, fy + 8, pw - PanelStyle.GRID * 2, PanelStyle.CONTROL_H), false);
            }
            fy += 40;
        }

        PanelStyle.button(g, font, "CANCEL", px + PanelStyle.GRID, py + ph - 32, 80,
                PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID, py + ph - 32, 80, PanelStyle.CONTROL_H), false);
        String confirm = existing == null ? "ADD EFFECT" : "APPLY";
        PanelStyle.button(g, font, confirm, px + pw - 90 - PanelStyle.GRID, py + ph - 32, 90,
                PanelStyle.hit(mouseX, mouseY, px + pw - 90 - PanelStyle.GRID, py + ph - 32, 90, PanelStyle.CONTROL_H), true);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int fy = py + 40;
        for (var field : schema.fields()) {
            if (field.type() == EffectSchemas.FieldType.CYCLE
                    && PanelStyle.hit(mx, my, px + PanelStyle.GRID, fy + 8, pw - PanelStyle.GRID * 2, PanelStyle.CONTROL_H)) {
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
     * In add mode the effect is appended to the stage's "effects" array, creating it
     * if this is the stage's first effect.
     */
    private void apply() {
        JsonObject effect = existing != null ? existing : new JsonObject();
        effect.addProperty("type", schema.typeId());   // the dispatch key the codec reads
        for (var field : schema.fields()) {
            switch (field.type()) {
                case CYCLE -> effect.addProperty(field.key(), field.options()[cycles.get(field.key())]);
                case NUMBER -> {
                    // An unparseable number leaves the previous value in place rather than
                    // writing something the codec would reject.
                    try {
                        effect.addProperty(field.key(), Double.parseDouble(boxes.get(field.key()).getValue().trim()));
                    } catch (NumberFormatException ignored) { }
                }
                case STRING -> effect.addProperty(field.key(), boxes.get(field.key()).getValue().trim());
            }
        }
        if (existing == null) {
            if (!stage.has("effects")) stage.add("effects", new com.google.gson.JsonArray());
            stage.getAsJsonArray("effects").add(effect);
        }
        parent.markDirtyFromChild();   // the parent owns the dirty flag and the save button
        Minecraft.getInstance().gui.setScreen(parent);
    }

    /** Editing must not pause a singleplayer world. */
    @Override
    public boolean isPauseScreen() { return false; }
}