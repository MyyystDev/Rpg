package com.myyyst.myrpg.core.client.editor;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Searchable effect catalog: rows of friendly label + raw id, by category.
 *
 * <p>Step one of adding a stage effect: pick the type here, then
 * {@link EffectConfigScreen} fills in its fields. The screen returns to {@code parent}
 * either way, and only the config screen actually writes into the stage.</p>
 */
public class EffectPickerScreen extends Screen {

    /** Screen to return to; also the owner of the definition being edited. */
    private final StatEditorScreen parent;
    /** The stage object the chosen effect will be appended to. */
    private final JsonObject stage;
    private EditBox searchBox;
    /** Schemas matching the current search text, rebuilt every tick. */
    private final List<EffectSchemas.EffectSchema> visible = new ArrayList<>();
    private int selected = -1;
    /** Panel geometry, recomputed in init() so the dialog stays centred on resize. */
    private int px, py, pw, ph;

    public EffectPickerScreen(StatEditorScreen parent, JsonObject stage) {
        super(Component.literal("Add Effect"));
        this.parent = parent;
        this.stage = stage;
    }

    @Override
    protected void init() {
        pw = 300; ph = 240;
        px = (width - pw) / 2; py = (height - ph) / 2;
        searchBox = new EditBox(font, px + PanelStyle.GRID, py + 28, pw - PanelStyle.GRID * 2, 18, Component.empty());
        searchBox.setHint(Component.literal("Search effects..."));
        addRenderableWidget(searchBox);
        refilter();
    }

    /** Rebuilds the visible list from the search box; matches label or type id. */
    private void refilter() {
        visible.clear();
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase();
        for (var schema : EffectSchemas.all().values()) {
            if (query.isEmpty() || schema.label().toLowerCase().contains(query)
                    || schema.typeId().contains(query)) {
                visible.add(schema);
            }
        }
        // Keep the selection inside the new list rather than losing it on every keystroke.
        selected = visible.isEmpty() ? -1 : Math.min(Math.max(selected, 0), visible.size() - 1);
    }

    /** Cheap enough to refilter every tick, which keeps the list live as the user types. */
    @Override
    public void tick() { refilter(); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, px, py, pw, ph);
        g.text(font, Component.literal("ADD EFFECT"), px + PanelStyle.GRID, py + PanelStyle.GRID, PanelStyle.TEXT);

        // Rows are drawn until they would collide with the button strip at the bottom.
        int ry = py + 54;
        for (int i = 0; i < visible.size() && ry < py + ph - 44; i++) {
            var schema = visible.get(i);
            boolean hovered = PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID, ry, pw - PanelStyle.GRID * 2, 26);
            if (i == selected) g.fill(px + PanelStyle.GRID, ry, px + pw - PanelStyle.GRID, ry + 26, PanelStyle.ROW_SELECT);
            else if (hovered) g.fill(px + PanelStyle.GRID, ry, px + pw - PanelStyle.GRID, ry + 26, PanelStyle.ROW_HOVER);
            g.text(font, Component.literal(schema.label().toUpperCase()), px + PanelStyle.GRID + 4, ry + 3, PanelStyle.TEXT);
            g.text(font, Component.literal(schema.typeId()), px + PanelStyle.GRID + 4, ry + 14, PanelStyle.TEXT_DIM);
            String cat = schema.category();
            g.text(font, Component.literal(cat), px + pw - PanelStyle.GRID - 6 - font.width(cat), ry + 8, PanelStyle.ACCENT);
            ry += 28;
        }

        PanelStyle.button(g, font, "CANCEL", px + PanelStyle.GRID, py + ph - 32, 90,
                PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID, py + ph - 32, 90, PanelStyle.CONTROL_H), false);
        PanelStyle.button(g, font, "CONFIGURE", px + pw - 90 - PanelStyle.GRID, py + ph - 32, 90,
                PanelStyle.hit(mouseX, mouseY, px + pw - 90 - PanelStyle.GRID, py + ph - 32, 90, PanelStyle.CONTROL_H), true);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    /**
     * Hit-testing mirrors the layout in {@code extractRenderState} - the rows are drawn
     * immediately, not backed by widgets, so both methods must walk the same geometry.
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int ry = py + 54;
        for (int i = 0; i < visible.size(); i++) {
            if (PanelStyle.hit(mx, my, px + PanelStyle.GRID, ry, pw - PanelStyle.GRID * 2, 26)) {
                selected = i;
                if (doubleClick) configure();
                return true;
            }
            ry += 28;
        }
        if (PanelStyle.hit(mx, my, px + PanelStyle.GRID, py + ph - 32, 90, PanelStyle.CONTROL_H)) {
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        if (PanelStyle.hit(mx, my, px + pw - 90 - PanelStyle.GRID, py + ph - 32, 90, PanelStyle.CONTROL_H)) {
            configure();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Opens the config form for the selected type in "add" mode (existing == null). */
    private void configure() {
        if (selected >= 0) {
            Minecraft.getInstance().gui.setScreen(
                    new EffectConfigScreen(parent, stage, visible.get(selected), null));
        }
    }

    /** Editing must not pause a singleplayer world - the game keeps running behind it. */
    @Override
    public boolean isPauseScreen() { return false; }
}