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

/** Schema-driven form for one effect. existing == null → add mode. */
public class EffectConfigScreen extends Screen {

    private final StatEditorScreen parent;
    private final JsonObject stage;
    private final EffectSchemas.EffectSchema schema;
    @Nullable private final JsonObject existing;

    private final Map<String, EditBox> boxes = new HashMap<>();
    private final Map<String, Integer> cycles = new HashMap<>();
    private int px, py, pw, ph;

    public EffectConfigScreen(StatEditorScreen parent, JsonObject stage,
                              EffectSchemas.EffectSchema schema, @Nullable JsonObject existing) {
        super(Component.literal(schema.label()));
        this.parent = parent;
        this.stage = stage;
        this.schema = schema;
        this.existing = existing;
    }

    @Override
    protected void init() {
        pw = 280;
        ph = 90 + schema.fields().size() * 40;
        px = (width - pw) / 2; py = (height - ph) / 2;

        int fy = py + 40;
        for (var field : schema.fields()) {
            if (field.type() == EffectSchemas.FieldType.CYCLE) {
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

    private void apply() {
        JsonObject effect = existing != null ? existing : new JsonObject();
        effect.addProperty("type", schema.typeId());
        for (var field : schema.fields()) {
            switch (field.type()) {
                case CYCLE -> effect.addProperty(field.key(), field.options()[cycles.get(field.key())]);
                case NUMBER -> {
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
        parent.markDirtyFromChild();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}