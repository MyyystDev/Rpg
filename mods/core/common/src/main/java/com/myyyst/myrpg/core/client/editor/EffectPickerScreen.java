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

/** Searchable effect catalog: rows of friendly label + raw id, by category. */
public class EffectPickerScreen extends Screen {

    private final StatEditorScreen parent;
    private final JsonObject stage;
    private EditBox searchBox;
    private final List<EffectSchemas.EffectSchema> visible = new ArrayList<>();
    private int selected = -1;
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

    private void refilter() {
        visible.clear();
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase();
        for (var schema : EffectSchemas.all().values()) {
            if (query.isEmpty() || schema.label().toLowerCase().contains(query)
                    || schema.typeId().contains(query)) {
                visible.add(schema);
            }
        }
        selected = visible.isEmpty() ? -1 : Math.min(Math.max(selected, 0), visible.size() - 1);
    }

    @Override
    public void tick() { refilter(); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, px, py, pw, ph);
        g.text(font, Component.literal("ADD EFFECT"), px + PanelStyle.GRID, py + PanelStyle.GRID, PanelStyle.TEXT);

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

    private void configure() {
        if (selected >= 0) {
            Minecraft.getInstance().gui.setScreen(
                    new EffectConfigScreen(parent, stage, visible.get(selected), null));
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}