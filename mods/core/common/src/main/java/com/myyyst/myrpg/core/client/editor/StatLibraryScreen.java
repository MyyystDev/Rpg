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
 * The Custom Stats library: search, 52px rows with status chips,
 * right-click context menu, + New Stat. Left-click opens (slice 3).
 */
public class StatLibraryScreen extends Screen {

    private final StatWorkingSet workingSet;
    private EditBox searchBox;
    private int scroll;
    private final List<Integer> visible = new ArrayList<>();   // filtered indices

    // context menu state
    private int menuEntry = -1;
    private int menuX, menuY;
    private static final String[] MENU = {"OPEN", "DUPLICATE", "COPY ID", "DELETE"};

    // delete confirmation state
    private int confirmEntry = -1;

    // layout, computed in init
    private int px, py, pw, ph, listY, listH;

    public StatLibraryScreen(StatWorkingSet workingSet) {
        super(Component.literal("Custom Stats"));
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

        // NOTE drift: EditBox constructor/args and addRenderableWidget —
        // mirror whatever compiled in the old project's dialogs.
        searchBox = new EditBox(font, px + PanelStyle.GRID * 2, py + PanelStyle.GRID * 5,
                pw - PanelStyle.GRID * 4, 18, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search name or resource ID..."));
        addRenderableWidget(searchBox);

        refilter();
    }

    private void refilter() {
        visible.clear();
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase();
        for (int i = 0; i < workingSet.entries.size(); i++) {
            StatWorkingSet.Entry entry = workingSet.entries.get(i);
            if (query.isEmpty() || entry.statId.toLowerCase().contains(query)
                    || entry.displayName().toLowerCase().contains(query)) {
                visible.add(i);
            }
        }
        scroll = 0;
    }

    // NOTE drift: render entry point — extractRenderState(GuiGraphicsExtractor,
    // int, int, float) per the ported-screens pattern; adjust name if your
    // Screen base differs, and call super for widget rendering.
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, px, py, pw, ph);

        // header
        g.text(font, Component.literal("CUSTOM STATS"), px + PanelStyle.GRID * 2, py + PanelStyle.GRID * 2, PanelStyle.TEXT);
        g.text(font, Component.literal(workingSet.entries.size() + " LOADED"),
                px + PanelStyle.GRID * 2 + font.width("CUSTOM STATS") + PanelStyle.GRID,
                py + PanelStyle.GRID * 2, PanelStyle.TEXT_DIM);
        int newW = 90;
        PanelStyle.button(g, font, "+ NEW STAT", px + pw - newW - PanelStyle.GRID * 2, py + PanelStyle.GRID * 2 - 6, newW,
                PanelStyle.hit(mouseX, mouseY, px + pw - newW - PanelStyle.GRID * 2, py + PanelStyle.GRID * 2 - 6, newW, PanelStyle.CONTROL_H), true);

        // list well
        PanelStyle.inset(g, px + PanelStyle.GRID, listY, pw - PanelStyle.GRID * 2, listH);

        int rows = listH / PanelStyle.ROW_H;
        for (int r = 0; r < rows; r++) {
            int idx = scroll + r;
            if (idx >= visible.size()) break;
            StatWorkingSet.Entry entry = workingSet.entries.get(visible.get(idx));
            int ry = listY + r * PanelStyle.ROW_H + 2;
            int rx = px + PanelStyle.GRID + 2;
            int rw = pw - PanelStyle.GRID * 2 - 4;
            boolean hovered = menuEntry < 0 && confirmEntry < 0
                    && PanelStyle.hit(mouseX, mouseY, rx, ry, rw, PanelStyle.ROW_H - 4);
            g.fill(rx, ry, rx + rw, ry + PanelStyle.ROW_H - 4, hovered ? PanelStyle.ROW_HOVER : PanelStyle.ROW_BG);

            g.text(font, Component.literal(entry.displayName().toUpperCase()), rx + PanelStyle.GRID, ry + 8, PanelStyle.TEXT);
            g.text(font, Component.literal(entry.statId), rx + PanelStyle.GRID, ry + 24, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal(entry.rangeLabel()), rx + rw - 140, ry + 8, PanelStyle.TEXT_DIM);

            if (entry.parseError != null) {
                PanelStyle.chip(g, font, "1 ERROR", rx + rw - 64, ry + 22, PanelStyle.ERROR);
            } else if (entry.dirty) {
                PanelStyle.chip(g, font, "EDITED", rx + rw - 64, ry + 22, PanelStyle.EDITED);
            } else {
                PanelStyle.chip(g, font, "VALID", rx + rw - 64, ry + 22, PanelStyle.VALID);
            }
        }

        g.text(font, Component.literal(visible.size() + " RESULTS"),
                px + PanelStyle.GRID * 2, py + ph - PanelStyle.GRID * 2, PanelStyle.TEXT_DIM);

        super.extractRenderState(g, mouseX, mouseY, delta);   // widgets (search box)

        // overlays last
        if (menuEntry >= 0) renderContextMenu(g, mouseX, mouseY);
        if (confirmEntry >= 0) renderConfirm(g, mouseX, mouseY);
    }

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

    private void renderConfirm(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int w = 220, h = 96;
        int cx = (width - w) / 2, cy = (height - h) / 2;
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, cx, cy, w, h);
        StatWorkingSet.Entry entry = workingSet.entries.get(confirmEntry);
        g.text(font, Component.literal("DELETE " + entry.displayName().toUpperCase() + "?"),
                cx + PanelStyle.GRID, cy + PanelStyle.GRID, PanelStyle.TEXT);
        g.text(font, Component.literal("Removes the overlay file."), cx + PanelStyle.GRID, cy + 28, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, "CANCEL", cx + PanelStyle.GRID, cy + h - 32, 96,
                PanelStyle.hit(mouseX, mouseY, cx + PanelStyle.GRID, cy + h - 32, 96, PanelStyle.CONTROL_H), false);
        PanelStyle.button(g, font, "DELETE", cx + w - 96 - PanelStyle.GRID, cy + h - 32, 96,
                PanelStyle.hit(mouseX, mouseY, cx + w - 96 - PanelStyle.GRID, cy + h - 32, 96, PanelStyle.CONTROL_H), true);
    }

    // NOTE drift: MouseButtonEvent accessors event.x()/y()/button() per the
    // ported-screens pattern.
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();

        if (confirmEntry >= 0) {
            int w = 220, h = 96, cx = (width - w) / 2, cy = (height - h) / 2;
            if (PanelStyle.hit(mx, my, cx + w - 96 - PanelStyle.GRID, cy + h - 32, 96, PanelStyle.CONTROL_H)) {
                StatWorkingSet.Entry entry = workingSet.entries.get(confirmEntry);
                ClientEditorNet.sendDelete(entry.statId);
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

        // + New Stat
        int newW = 90;
        if (PanelStyle.hit(mx, my, px + pw - newW - PanelStyle.GRID * 2, py + PanelStyle.GRID * 2 - 6, newW, PanelStyle.CONTROL_H)) {
            Minecraft.getInstance().gui.setScreen(
                    new CreateStatScreen(this, workingSet));
            return true;
        }

        // rows
        int rows = listH / PanelStyle.ROW_H;
        for (int r = 0; r < rows; r++) {
            int idx = scroll + r;
            if (idx >= visible.size()) break;
            int ry = listY + r * PanelStyle.ROW_H + 2;
            int rx = px + PanelStyle.GRID + 2;
            int rw = pw - PanelStyle.GRID * 2 - 4;
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

    private void handleMenu(String action) {
        StatWorkingSet.Entry entry = workingSet.entries.get(menuEntry);
        switch (action) {
            case "OPEN" -> openEntry(menuEntry);
            case "DUPLICATE" -> {
                StatWorkingSet.Entry copy = new StatWorkingSet.Entry(entry.statId + "_copy",
                        entry.json == null ? null : entry.json.deepCopy());
                copy.dirty = true;
                workingSet.entries.add(copy);
                refilter();
            }
            case "COPY ID" -> Minecraft.getInstance().keyboardHandler.setClipboard(entry.statId);
            // NOTE drift: clipboard accessor spelling.
            case "DELETE" -> confirmEntry = menuEntry;
        }
    }

    private void openEntry(int index) {
        StatWorkingSet.Entry entry = workingSet.entries.get(index);

        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(
                    Component.literal(
                            "[editor] " + entry.statId
                                    + " — editor pages arrive in slice 3"
                    )
            );
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int max = Math.max(0, visible.size() - listH / PanelStyle.ROW_H);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(vertical)));
        return true;
    }

    @Override
    public void tick() {
        // refilter on search change, cheaply
        refilter();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** Called by CreateStatScreen after a successful create. */
    void onCreated(StatWorkingSet.Entry entry) {
        workingSet.entries.add(entry);
        workingSet.entries.sort((a, b) -> a.statId.compareTo(b.statId));
        refilter();
    }
}