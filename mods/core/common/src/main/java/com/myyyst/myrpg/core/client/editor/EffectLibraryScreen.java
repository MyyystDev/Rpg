package com.myyyst.myrpg.core.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Custom Effects library: search, rows with category + status chips,
 * right-click context menu, + New Effect.
 *
 * <p>Effect-side twin of {@code StatLibraryScreen}: same layout, same interactions,
 * with a category colour on each row instead of a value range.</p>
 */
public class EffectLibraryScreen extends Screen {

    /** The editor's scratch copy of every effect; shared with the screens opened from here. */
    private final EffectWorkingSet workingSet;
    private EditBox searchBox;
    /** Index of the first visible row. */
    private int scroll;
    private final List<Integer> visible = new ArrayList<>();   // filtered indices

    // context menu state
    /** Entry the context menu belongs to, or -1 when no menu is open. */
    private int menuEntry = -1;
    private int menuX, menuY;
    private static final String[] MENU = {"OPEN", "DUPLICATE", "COPY ID", "DELETE"};

    // delete confirmation state
    /** Entry awaiting delete confirmation, or -1. */
    private int confirmEntry = -1;

    // layout, computed in init
    private int px, py, pw, ph, listY, listH;

    public EffectLibraryScreen(EffectWorkingSet workingSet) {
        super(Component.literal("Custom Effects"));
        this.workingSet = workingSet;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 4 * PanelStyle.GRID, 440);
        ph = Math.min(height - 4 * PanelStyle.GRID, 320);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listY = py + PanelStyle.GRID * 9;
        listH = ph - PanelStyle.GRID * 12;

        searchBox = new EditBox(font, px + PanelStyle.GRID * 2, py + PanelStyle.GRID * 5,
                pw - PanelStyle.GRID * 4, 18, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search name or resource ID..."));
        addRenderableWidget(searchBox);

        refilter();
    }

    /** Rebuilds the filtered index list from the search text; resets scrolling. */
    private void refilter() {
        visible.clear();
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase();
        for (int i = 0; i < workingSet.entries.size(); i++) {
            EffectWorkingSet.Entry entry = workingSet.entries.get(i);
            if (query.isEmpty() || entry.effectId.toLowerCase().contains(query)
                    || entry.displayName().toLowerCase().contains(query)) {
                visible.add(i);
            }
        }
        scroll = 0;
    }

    /** Row tint per category: green good, red bad, grey neutral - matches the HUD's palette. */
    private static int categoryColor(String category) {
        return switch (category) {
            case "beneficial" -> PanelStyle.VALID;
            case "harmful" -> PanelStyle.ERROR;
            default -> PanelStyle.TEXT_DIM;
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, px, py, pw, ph);

        // header
        g.text(font, Component.literal("CUSTOM EFFECTS"), px + PanelStyle.GRID * 2, py + PanelStyle.GRID * 2, PanelStyle.TEXT);
        g.text(font, Component.literal(workingSet.entries.size() + " LOADED"),
                px + PanelStyle.GRID * 2 + font.width("CUSTOM EFFECTS") + PanelStyle.GRID,
                py + PanelStyle.GRID * 2, PanelStyle.TEXT_DIM);
        int newW = 100;
        PanelStyle.button(g, font, "+ NEW EFFECT", px + pw - newW - PanelStyle.GRID * 2, py + PanelStyle.GRID * 2 - 6, newW,
                PanelStyle.hit(mouseX, mouseY, px + pw - newW - PanelStyle.GRID * 2, py + PanelStyle.GRID * 2 - 6, newW, PanelStyle.CONTROL_H), true);

        // list well
        PanelStyle.inset(g, px + PanelStyle.GRID, listY, pw - PanelStyle.GRID * 2, listH);

        int rows = listH / PanelStyle.ROW_H;
        for (int r = 0; r < rows; r++) {
            int idx = scroll + r;
            if (idx >= visible.size()) break;
            EffectWorkingSet.Entry entry = workingSet.entries.get(visible.get(idx));
            int ry = listY + r * PanelStyle.ROW_H + 2;
            int rx = px + PanelStyle.GRID + 2;
            int rw = pw - PanelStyle.GRID * 2 - 18;
            boolean hovered = menuEntry < 0 && confirmEntry < 0
                    && PanelStyle.hit(mouseX, mouseY, rx, ry, rw, PanelStyle.ROW_H - 4);
            g.fill(rx, ry, rx + rw, ry + PanelStyle.ROW_H - 4, hovered ? PanelStyle.ROW_HOVER : PanelStyle.ROW_BG);

            // left column: name (with chips beside it) over id
            String name = entry.displayName().toUpperCase();
            g.text(font, Component.literal(name), rx + PanelStyle.GRID, ry + 8, PanelStyle.TEXT);
            int chipX = rx + PanelStyle.GRID + font.width(name) + 8;
            String category = entry.category().toUpperCase();
            PanelStyle.chip(g, font, category, chipX, ry + 6, categoryColor(entry.category()));
            chipX += font.width(category) + 14;
            if (entry.parseError != null) {
                PanelStyle.chip(g, font, "ERROR", chipX, ry + 6, PanelStyle.ERROR);
            } else if (entry.dirty) {
                PanelStyle.chip(g, font, "EDITED", chipX, ry + 6, PanelStyle.EDITED);
            }
            g.text(font, Component.literal(entry.effectId), rx + PanelStyle.GRID, ry + 26, PanelStyle.TEXT_DIM);

            g.fill(rx, ry + PanelStyle.ROW_H - 5, rx + rw, ry + PanelStyle.ROW_H - 4, PanelStyle.PANEL_DARK);

            // right column: labeled duration summary, right-aligned
            String summary = entry.summaryLabel();
            g.text(font, Component.literal("DURATION"), rx + rw - PanelStyle.GRID - font.width("DURATION"),
                    ry + 8, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal(summary), rx + rw - PanelStyle.GRID - font.width(summary),
                    ry + 26, PanelStyle.TEXT);
        }

        PanelStyle.scrollbar(g, px + pw - PanelStyle.GRID - 14, listY + 2, listH - 4,
                visible.size(), rows, scroll);

        g.text(font, Component.literal(visible.size() + " RESULTS"),
                px + PanelStyle.GRID * 2, py + ph - PanelStyle.GRID * 2, PanelStyle.TEXT_DIM);

        super.extractRenderState(g, mouseX, mouseY, delta);   // widgets (search box)

        // overlays last
        if (menuEntry >= 0) renderContextMenu(g, mouseX, mouseY);
        if (confirmEntry >= 0) renderConfirm(g, mouseX, mouseY);
    }

    /** Right-click menu, drawn at the click position. DELETE is tinted red. */
    private void renderContextMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int w = 110, h = MENU.length * 16 + 4;
        PanelStyle.panel(g, menuX, menuY, w, h);
        for (int i = 0; i < MENU.length; i++) {
            int iy = menuY + 2 + i * 16;
            boolean hovered = PanelStyle.hit(mouseX, mouseY, menuX + 2, iy, w - 4, 16);
            if (hovered) g.fill(menuX + 2, iy, menuX + w - 2, iy + 16, PanelStyle.ROW_HOVER);
            int color = MENU[i].equals("DELETE") ? PanelStyle.ERROR : PanelStyle.TEXT;
            g.text(font, Component.literal(MENU[i]), menuX + PanelStyle.GRID, iy + 4, color);
        }
    }

    /** Centred "are you sure" dialog for deletion. */
    private void renderConfirm(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int w = 220, h = 96;
        int cx = (width - w) / 2, cy = (height - h) / 2;
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, cx, cy, w, h);
        EffectWorkingSet.Entry entry = workingSet.entries.get(confirmEntry);
        g.text(font, Component.literal("DELETE " + entry.displayName().toUpperCase() + "?"),
                cx + PanelStyle.GRID, cy + PanelStyle.GRID, PanelStyle.TEXT);
        g.text(font, Component.literal("Removes the overlay file."), cx + PanelStyle.GRID, cy + 28, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, "CANCEL", cx + PanelStyle.GRID, cy + h - 32, 96,
                PanelStyle.hit(mouseX, mouseY, cx + PanelStyle.GRID, cy + h - 32, 96, PanelStyle.CONTROL_H), false);
        PanelStyle.button(g, font, "DELETE", cx + w - 96 - PanelStyle.GRID, cy + h - 32, 96,
                PanelStyle.hit(mouseX, mouseY, cx + w - 96 - PanelStyle.GRID, cy + h - 32, 96, PanelStyle.CONTROL_H), true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();

        if (confirmEntry >= 0) {
            int w = 220, h = 96, cx = (width - w) / 2, cy = (height - h) / 2;
            if (PanelStyle.hit(mx, my, cx + w - 96 - PanelStyle.GRID, cy + h - 32, 96, PanelStyle.CONTROL_H)) {
                EffectWorkingSet.Entry entry = workingSet.entries.get(confirmEntry);
                ClientEditorNet.sendDeleteEffect(entry.effectId);
                workingSet.entries.remove(confirmEntry);
                refilter();
            }
            confirmEntry = -1;
            return true;
        }

        if (menuEntry >= 0) {
            int w = 110;
            for (int i = 0; i < MENU.length; i++) {
                if (PanelStyle.hit(mx, my, menuX + 2, menuY + 2 + i * 16, w - 4, 16)) {
                    handleMenu(MENU[i]);
                    menuEntry = -1;
                    return true;
                }
            }
            menuEntry = -1;
            return true;
        }

        // + New Effect
        int newW = 100;
        if (PanelStyle.hit(mx, my, px + pw - newW - PanelStyle.GRID * 2, py + PanelStyle.GRID * 2 - 6, newW, PanelStyle.CONTROL_H)) {
            Minecraft.getInstance().gui.setScreen(
                    new CreateEffectScreen(this, workingSet));
            return true;
        }

        // rows
        int rows = listH / PanelStyle.ROW_H;
        for (int r = 0; r < rows; r++) {
            int idx = scroll + r;
            if (idx >= visible.size()) break;
            int ry = listY + r * PanelStyle.ROW_H + 2;
            int rx = px + PanelStyle.GRID + 2;
            int rw = pw - PanelStyle.GRID * 2 - 18;
            if (PanelStyle.hit(mx, my, rx, ry, rw, PanelStyle.ROW_H - 4)) {
                if (event.button() == 1) {
                    menuEntry = visible.get(idx);
                    menuX = (int) mx;
                    menuY = (int) my;
                } else {
                    openEntry(visible.get(idx));
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Runs a context-menu action; DUPLICATE stays local until saved, DELETE asks first. */
    private void handleMenu(String action) {
        EffectWorkingSet.Entry entry = workingSet.entries.get(menuEntry);
        switch (action) {
            case "OPEN" -> openEntry(menuEntry);
            case "DUPLICATE" -> {
                EffectWorkingSet.Entry copy = new EffectWorkingSet.Entry(entry.effectId + "_copy",
                        entry.json == null ? null : entry.json.deepCopy());
                copy.dirty = true;
                workingSet.entries.add(copy);
                refilter();
            }
            case "COPY ID" -> Minecraft.getInstance().keyboardHandler.setClipboard(entry.effectId);
            case "DELETE" -> confirmEntry = menuEntry;
        }
    }

    /** Opens the full effect editor for one entry. */
    private void openEntry(int index) {
        EffectWorkingSet.Entry entry = workingSet.entries.get(index);
        if (entry.json == null) return;   // parse-error rows aren't editable
        Minecraft.getInstance().gui.setScreen(
                new EffectDefEditorScreen(this, entry));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int max = Math.max(0, visible.size() - listH / PanelStyle.ROW_H);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(vertical)));
        return true;
    }

    /** Last search text seen, so the list is only rebuilt when it actually changes. */
    private String lastQuery = "";

    @Override
    public void tick() {
        String query = searchBox == null ? "" : searchBox.getValue();
        if (!query.equals(lastQuery)) {
            lastQuery = query;
            refilter();
        }
    }

    /** Editing must not pause a singleplayer world. */
    @Override
    public boolean isPauseScreen() { return false; }

    /** Called by CreateEffectScreen after a successful create. */
    /** Called by CreateEffectScreen after a successful create. */
    void onCreated(EffectWorkingSet.Entry entry) {
        workingSet.entries.add(entry);
        workingSet.entries.sort((a, b) -> a.effectId.compareTo(b.effectId));
        refilter();
    }
}
