package com.myyyst.myrpg.core.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.myyyst.myrpg.core.client.editor.EffectSchemas.FieldSpec;

/**
 * Edits one JsonArray of typed objects (conditions, actions, or stage
 * effects). Two modes in one screen:
 *   LIST — rows with EDIT (click) and X (delete), + ADD button
 *   PICK — searchable catalog of registered types; choosing one opens
 *          TypedObjectConfigScreen in add mode
 * Unknown types (addon content, combinators without forms) render as
 * rows with their raw id and can be deleted but not edited — payload
 * preserved, per the design book's law.
 */
public class TypedObjectListScreen extends Screen {

    public enum Kind { CONDITION, ACTION, EFFECT }

    /** Uniform view over the three schema record types. */
    private record SchemaView(String typeId, String label, String category, List<FieldSpec> fields) {}

    private final Screen parent;
    private final String title;
    private final JsonArray list;
    private final Kind kind;
    private final Runnable onDirty;

    private final Map<String, SchemaView> schemas = new LinkedHashMap<>();

    private boolean picking;
    private EditBox searchBox;
    private final List<SchemaView> pickVisible = new ArrayList<>();
    private int scroll;
    private int px, py, pw, ph, listY, listH;

    public TypedObjectListScreen(Screen parent, String title, JsonArray list,
                                 Kind kind, Runnable onDirty) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.list = list;
        this.kind = kind;
        this.onDirty = onDirty;
        loadSchemas();
    }

    private void loadSchemas() {
        switch (kind) {
            case CONDITION -> ConditionSchemas.all().values().forEach(s ->
                    schemas.put(s.typeId(), new SchemaView(s.typeId(), s.label(), s.category(), s.fields())));
            case ACTION -> ActionSchemas.all().values().forEach(s ->
                    schemas.put(s.typeId(), new SchemaView(s.typeId(), s.label(), s.category(), s.fields())));
            case EFFECT -> EffectSchemas.all().values().forEach(s ->
                    schemas.put(s.typeId(), new SchemaView(s.typeId(), s.label(), s.category(), s.fields())));
        }
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void init() {
        pw = 320;
        ph = 260;
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listY = py + (picking ? 54 : 34);
        listH = ph - (picking ? 96 : 76);

        if (picking) {
            searchBox = new EditBox(font, px + PanelStyle.GRID, py + 28,
                    pw - PanelStyle.GRID * 2, 18, Component.empty());
            searchBox.setHint(Component.literal("Search..."));
            addRenderableWidget(searchBox);
            refilter();
        }
    }

    private void setPicking(boolean value) {
        picking = value;
        scroll = 0;
        clearWidgets();   // NOTE drift: same spelling as StatEditorScreen.setPage
        init();
    }

    private void refilter() {
        pickVisible.clear();
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase();
        for (SchemaView schema : schemas.values()) {
            if (query.isEmpty() || schema.label().toLowerCase().contains(query)
                    || schema.typeId().contains(query)
                    || schema.category().toLowerCase().contains(query)) {
                pickVisible.add(schema);
            }
        }
    }

    @Override
    public void tick() {
        if (picking) refilter();
    }

    // ------------------------------------------------------------ render

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, px, py, pw, ph);

        if (picking) {
            renderPicker(g, mouseX, mouseY);
        } else {
            renderList(g, mouseX, mouseY);
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void renderList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal(title), px + PanelStyle.GRID, py + PanelStyle.GRID, PanelStyle.TEXT);
        PanelStyle.button(g, font, "+ ADD", px + pw - 64 - PanelStyle.GRID, py + PanelStyle.GRID - 2, 64,
                PanelStyle.hit(mouseX, mouseY, px + pw - 64 - PanelStyle.GRID, py + PanelStyle.GRID - 2, 64, PanelStyle.CONTROL_H), true);

        PanelStyle.inset(g, px + PanelStyle.GRID, listY, pw - PanelStyle.GRID * 2, listH);

        int rowH = 28;
        int rows = listH / rowH;
        for (int r = 0; r < rows; r++) {
            int idx = scroll + r;
            if (idx >= list.size()) break;
            JsonObject object = list.get(idx).getAsJsonObject();
            String type = object.has("type") ? object.get("type").getAsString() : "?";
            SchemaView schema = schemas.get(type);
            int ry = listY + 2 + r * rowH;
            int rx = px + PanelStyle.GRID + 2;
            int rw = pw - PanelStyle.GRID * 2 - 4;
            boolean editable = schema != null;
            boolean hovered = PanelStyle.hit(mouseX, mouseY, rx, ry, rw - 20, rowH - 2);
            g.fill(rx, ry, rx + rw, ry + rowH - 2, hovered && editable ? PanelStyle.ROW_HOVER : PanelStyle.ROW_BG);

            if (editable) {
                g.text(font, Component.literal(schema.label()), rx + 6, ry + 4, PanelStyle.TEXT);
                g.text(font, Component.literal(summarize(object, schema)), rx + 6, ry + 15, PanelStyle.TEXT_DIM);
            } else {
                g.text(font, Component.literal(type), rx + 6, ry + 4, PanelStyle.TEXT);
                g.text(font, Component.literal("Unknown type — payload preserved"), rx + 6, ry + 15, PanelStyle.EDITED);
            }
            boolean xHover = PanelStyle.hit(mouseX, mouseY, rx + rw - 16, ry + 6, 12, 12);
            g.text(font, Component.literal("X"), rx + rw - 14, ry + 8,
                    xHover ? PanelStyle.ERROR : PanelStyle.TEXT_DIM);
        }

        if (list.isEmpty()) {
            g.text(font, Component.literal("Nothing here yet."),
                    px + PanelStyle.GRID + 8, listY + 10, PanelStyle.TEXT_DIM);
        }

        PanelStyle.button(g, font, "DONE", px + pw - 80 - PanelStyle.GRID, py + ph - 32, 80,
                PanelStyle.hit(mouseX, mouseY, px + pw - 80 - PanelStyle.GRID, py + ph - 32, 80, PanelStyle.CONTROL_H), true);
    }

    private void renderPicker(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("ADD " + singular()), px + PanelStyle.GRID, py + PanelStyle.GRID, PanelStyle.TEXT);

        int rowH = 28;
        int rows = listH / rowH;
        for (int r = 0; r < rows; r++) {
            int idx = scroll + r;
            if (idx >= pickVisible.size()) break;
            SchemaView schema = pickVisible.get(idx);
            int ry = listY + 2 + r * rowH;
            int rx = px + PanelStyle.GRID + 2;
            int rw = pw - PanelStyle.GRID * 2 - 4;
            boolean hovered = PanelStyle.hit(mouseX, mouseY, rx, ry, rw, rowH - 2);
            g.fill(rx, ry, rx + rw, ry + rowH - 2, hovered ? PanelStyle.ROW_HOVER : PanelStyle.ROW_BG);
            g.text(font, Component.literal(schema.label().toUpperCase()), rx + 6, ry + 4, PanelStyle.TEXT);
            g.text(font, Component.literal(schema.typeId()), rx + 6, ry + 15, PanelStyle.TEXT_DIM);
            String cat = schema.category();
            g.text(font, Component.literal(cat), rx + rw - 6 - font.width(cat), ry + 9, PanelStyle.ACCENT);
        }

        PanelStyle.button(g, font, "BACK", px + PanelStyle.GRID, py + ph - 32, 80,
                PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID, py + ph - 32, 80, PanelStyle.CONTROL_H), false);
    }

    private String singular() {
        return switch (kind) {
            case CONDITION -> "CONDITION";
            case ACTION -> "ACTION";
            case EFFECT -> "EFFECT";
        };
    }

    /** One-line summary from the object's own fields (never stored). */
    private String summarize(JsonObject object, SchemaView schema) {
        StringBuilder out = new StringBuilder();
        for (FieldSpec field : schema.fields()) {
            String key = field.key().startsWith("__var_value")
                    ? (field.key().equals("__var_value") ? "value" : "default")
                    : field.key();
            if (!object.has(key)) continue;
            var element = object.get(key);
            String shown;
            if (element.isJsonObject()) {           // VarValue
                JsonObject varValue = element.getAsJsonObject();
                shown = varValue.has("number") ? varValue.get("number").getAsString()
                        : varValue.has("string") ? varValue.get("string").getAsString() : "?";
            } else {
                shown = element.getAsString();
            }
            if (out.length() > 0) out.append("  ");
            out.append(shown);
            if (out.length() > 42) {
                out.setLength(42);
                out.append("...");
                break;
            }
        }
        return out.isEmpty() ? "(defaults)" : out.toString();
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int rowH = 28;
        int rows = listH / rowH;

        if (picking) {
            for (int r = 0; r < rows; r++) {
                int idx = scroll + r;
                if (idx >= pickVisible.size()) break;
                int ry = listY + 2 + r * rowH;
                if (PanelStyle.hit(mx, my, px + PanelStyle.GRID + 2, ry, pw - PanelStyle.GRID * 2 - 4, rowH - 2)) {
                    SchemaView schema = pickVisible.get(idx);
                    setPicking(false);
                    Minecraft.getInstance().gui.setScreen(new TypedObjectConfigScreen(
                            this, list, schema.typeId(), schema.label(), schema.fields(), null, onDirty));
                    return true;
                }
            }
            if (PanelStyle.hit(mx, my, px + PanelStyle.GRID, py + ph - 32, 80, PanelStyle.CONTROL_H)) {
                setPicking(false);
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        // list mode
        if (PanelStyle.hit(mx, my, px + pw - 64 - PanelStyle.GRID, py + PanelStyle.GRID - 2, 64, PanelStyle.CONTROL_H)) {
            setPicking(true);
            return true;
        }
        for (int r = 0; r < rows; r++) {
            int idx = scroll + r;
            if (idx >= list.size()) break;
            int ry = listY + 2 + r * rowH;
            int rx = px + PanelStyle.GRID + 2;
            int rw = pw - PanelStyle.GRID * 2 - 4;
            if (PanelStyle.hit(mx, my, rx + rw - 16, ry + 6, 12, 12)) {
                list.remove(idx);
                onDirty.run();
                return true;
            }
            if (PanelStyle.hit(mx, my, rx, ry, rw - 20, rowH - 2)) {
                JsonObject object = list.get(idx).getAsJsonObject();
                String type = object.has("type") ? object.get("type").getAsString() : "?";
                SchemaView schema = schemas.get(type);
                if (schema != null) {
                    Minecraft.getInstance().gui.setScreen(new TypedObjectConfigScreen(
                            this, list, schema.typeId(), schema.label(), schema.fields(), object, onDirty));
                }
                return true;
            }
        }
        if (PanelStyle.hit(mx, my, px + pw - 80 - PanelStyle.GRID, py + ph - 32, 80, PanelStyle.CONTROL_H)) {
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int size = picking ? pickVisible.size() : list.size();
        int max = Math.max(0, size - listH / 28);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(vertical)));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}