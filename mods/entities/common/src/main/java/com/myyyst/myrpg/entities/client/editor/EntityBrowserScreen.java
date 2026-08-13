package com.myyyst.myrpg.entities.client.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.myyyst.myrpg.core.client.editor.PanelStyle;
import com.myyyst.myrpg.core.platform.Services;
import com.myyyst.myrpg.entities.network.EntitiesPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Custom Entities browser — design book page 03. Rows carry an accent
 * bar, head thumbnail, and role tags; right-click opens the full context
 * menu; + NEW ENTITY opens the create dialog; rows open the component
 * editor. JSON view stays available via OPEN JSON.
 */
public class EntityBrowserScreen extends Screen {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final int ROW = 64;
    private static final String[] MENU =
            {"OPEN", "SPAWN", "DUPLICATE", "COPY ID", "OPEN JSON", "DELETE"};

    private final EntityWorkingSet workingSet;
    private final List<Integer> visible = new ArrayList<>();
    private final List<String> filters = new ArrayList<>();
    private int filterIndex;

    private EditBox searchBox;
    private int scroll;

    private int menuEntry = -1;
    private int menuX, menuY;
    private int confirmEntry = -1;

    private int jsonEntry = -1;
    private int jsonScroll;
    private List<String> jsonLines = List.of();

    private int px, py, pw, ph, listY, listH;

    public static void open(EntitiesPayloads.OpenEntityBrowser payload) {
        Minecraft.getInstance().gui.setScreen(new EntityBrowserScreen(payload));
    }

    /** Wand flow: open the browser with the editor already pushed on one entry. */
    public static void openFocused(EntitiesPayloads.OpenEntityEditor payload) {
        EntityBrowserScreen browser = new EntityBrowserScreen(
                new EntitiesPayloads.OpenEntityBrowser(payload.entities()));
        Minecraft.getInstance().gui.setScreen(browser);
        for (EntityWorkingSet.Entry entry : browser.workingSet.entries) {
            if (entry.entityId.equals(payload.focus())) {
                Minecraft.getInstance().gui.setScreen(new EntityEditorScreen(browser, entry));
                return;
            }
        }
    }

    public EntityBrowserScreen(EntitiesPayloads.OpenEntityBrowser payload) {
        super(Component.literal("Custom Entities"));
        this.workingSet = new EntityWorkingSet(payload);
        rebuildFilters();
    }

    private void rebuildFilters() {
        Set<String> tagSet = new LinkedHashSet<>();
        for (EntityWorkingSet.Entry entry : workingSet.entries) {
            tagSet.addAll(entry.tags());
        }
        String current = filters.isEmpty() ? "ALL" : filters.get(filterIndex);
        filters.clear();
        filters.add("ALL");
        filters.addAll(tagSet.stream().sorted().toList());
        filterIndex = Math.max(0, filters.indexOf(current));
    }

    /** Called by child screens after the working set changes. */
    public void refresh() {
        rebuildFilters();
        refilter();
    }

    @Override
    protected void init() {
        pw = Math.min(width - 4 * PanelStyle.GRID, 660);
        ph = Math.min(height - 4 * PanelStyle.GRID, 380);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listY = py + PanelStyle.GRID * 9;
        listH = ph - PanelStyle.GRID * 12;

        searchBox = new EditBox(font, px + PanelStyle.GRID * 2, py + PanelStyle.GRID * 5,
                pw - PanelStyle.GRID * 6 - 200, 18, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search name or resource ID..."));
        searchBox.setVisible(jsonEntry < 0);
        addRenderableWidget(searchBox);

        refilter();
    }

    private void refilter() {
        visible.clear();
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase();
        String filter = filters.get(filterIndex);
        for (int i = 0; i < workingSet.entries.size(); i++) {
            EntityWorkingSet.Entry entry = workingSet.entries.get(i);
            if (!filter.equals("ALL") && !entry.tags().contains(filter)) continue;
            if (query.isEmpty() || entry.entityId.toLowerCase().contains(query)
                    || entry.displayName().toLowerCase().contains(query)) {
                visible.add(i);
            }
        }
        scroll = 0;
    }

    // ------------------------------------------------------------ render

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, px, py, pw, ph);

        if (jsonEntry >= 0) {
            renderJsonView(g, mouseX, mouseY);
            super.extractRenderState(g, mouseX, mouseY, delta);
            return;
        }

        g.text(font, Component.literal("CUSTOM ENTITIES"),
                px + PanelStyle.GRID * 2, py + PanelStyle.GRID * 2, PanelStyle.TEXT);
        PanelStyle.chip(g, font, workingSet.entries.size() + " LOADED",
                px + PanelStyle.GRID * 2 + font.width("CUSTOM ENTITIES") + PanelStyle.GRID * 2,
                py + PanelStyle.GRID * 2 - 2, PanelStyle.ACCENT);
        int newW = 96;
        PanelStyle.button(g, font, "+ NEW ENTITY",
                px + pw - newW - PanelStyle.GRID * 2, py + PanelStyle.GRID * 2 - 6, newW,
                PanelStyle.hit(mouseX, mouseY, px + pw - newW - PanelStyle.GRID * 2,
                        py + PanelStyle.GRID * 2 - 6, newW, PanelStyle.CONTROL_H), true);

        int fw = 110, rw2 = 64;
        int fx = px + pw - PanelStyle.GRID * 2 - rw2 - PanelStyle.GRID - fw;
        int ty = py + PanelStyle.GRID * 5 - 3;
        PanelStyle.button(g, font, "FILTER: " + filters.get(filterIndex), fx, ty, fw,
                PanelStyle.hit(mouseX, mouseY, fx, ty, fw, PanelStyle.CONTROL_H), false);
        int rx2 = px + pw - PanelStyle.GRID * 2 - rw2;
        PanelStyle.button(g, font, "RELOAD", rx2, ty, rw2,
                PanelStyle.hit(mouseX, mouseY, rx2, ty, rw2, PanelStyle.CONTROL_H), false);

        PanelStyle.inset(g, px + PanelStyle.GRID, listY, pw - PanelStyle.GRID * 2, listH);
        int rows = listH / ROW;
        for (int r = 0; r < rows; r++) {
            int idx = scroll + r;
            if (idx >= visible.size()) break;
            EntityWorkingSet.Entry entry = workingSet.entries.get(visible.get(idx));
            int ry = listY + r * ROW + 2;
            int rx = px + PanelStyle.GRID + 2;
            int rw = pw - PanelStyle.GRID * 2 - 18;
            boolean hovered = menuEntry < 0 && confirmEntry < 0
                    && PanelStyle.hit(mouseX, mouseY, rx, ry, rw, ROW - 4);
            g.fill(rx, ry, rx + rw, ry + ROW - 4, hovered ? PanelStyle.ROW_HOVER : PanelStyle.ROW_BG);

            int accent = entry.accent();
            g.fill(rx, ry, rx + 3, ry + ROW - 4, accent);

            int thx = rx + 10, thy = ry + (ROW - 4 - 40) / 2;
            PanelStyle.inset(g, thx, thy, 40, 40);
            g.fill(thx + 14, thy + 5, thx + 26, thy + 15, 0xFFB98A5F);
            g.fill(thx + 16, thy + 9, thx + 18, thy + 11, 0xFF3A2E22);
            g.fill(thx + 22, thy + 9, thx + 24, thy + 11, 0xFF3A2E22);
            g.fill(thx + 12, thy + 16, thx + 28, thy + 28, accent);
            g.fill(thx + 14, thy + 29, thx + 19, thy + 36, PanelStyle.PANEL_DARK);
            g.fill(thx + 21, thy + 29, thx + 26, thy + 36, PanelStyle.PANEL_DARK);

            int tx = thx + 48;
            String name = entry.displayName().toUpperCase();
            g.text(font, Component.literal(name), tx, ry + 8, PanelStyle.TEXT);
            if (entry.dirty) {
                PanelStyle.chip(g, font, "UNSAVED", tx + font.width(name) + 8, ry + 6, PanelStyle.EDITED);
            }
            g.text(font, Component.literal(entry.entityId), tx, ry + 22, PanelStyle.TEXT_DIM);
            int cx = tx;
            for (String tag : entry.tags()) {
                PanelStyle.chip(g, font, tag, cx, ry + 38, accent);
                cx += font.width(tag) + 8 + 6;
            }

            int bw = 52;
            int bx = rx + rw - bw - 26;
            int by = ry + (ROW - 4 - PanelStyle.CONTROL_H) / 2;
            PanelStyle.button(g, font, "SPAWN", bx, by, bw,
                    PanelStyle.hit(mouseX, mouseY, bx, by, bw, PanelStyle.CONTROL_H), true);
            g.text(font, Component.literal(">"), rx + rw - 14, ry + (ROW - 4 - 8) / 2,
                    hovered ? PanelStyle.TEXT : PanelStyle.TEXT_DIM);

            g.fill(rx, ry + ROW - 5, rx + rw, ry + ROW - 4, PanelStyle.PANEL_DARK);
        }
        PanelStyle.scrollbar(g, px + pw - PanelStyle.GRID - 6, listY, listH,
                visible.size(), rows, scroll);

        g.text(font, Component.literal(visible.size() + " RESULTS  /  FILTER: " + filters.get(filterIndex)),
                px + PanelStyle.GRID * 2, py + ph - PanelStyle.GRID * 2, PanelStyle.TEXT_DIM);

        if (menuEntry >= 0) renderMenu(g, mouseX, mouseY);
        if (confirmEntry >= 0) renderConfirm(g, mouseX, mouseY);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void renderMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        EntityWorkingSet.Entry entry = workingSet.entries.get(menuEntry);
        int mw = 120, mh = 18 + MENU.length * 16 + 4;
        PanelStyle.panel(g, menuX, menuY, mw, mh);
        g.text(font, Component.literal(entry.displayName().toUpperCase()),
                menuX + 8, menuY + 6, PanelStyle.EDITED);
        for (int i = 0; i < MENU.length; i++) {
            int iy = menuY + 18 + i * 16;
            boolean h = PanelStyle.hit(mouseX, mouseY, menuX, iy, mw, 16);
            if (h) g.fill(menuX + 1, iy, menuX + mw - 1, iy + 16, PanelStyle.ROW_HOVER);
            boolean danger = MENU[i].equals("DELETE");
            g.text(font, Component.literal(MENU[i]), menuX + 8, iy + 4,
                    danger ? PanelStyle.ERROR : PanelStyle.TEXT);
        }
    }

    private void renderConfirm(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        EntityWorkingSet.Entry entry = workingSet.entries.get(confirmEntry);
        int w = 240, h = 96, cx = (width - w) / 2, cy = (height - h) / 2;
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, cx, cy, w, h);
        g.text(font, Component.literal("DELETE " + entry.displayName().toUpperCase() + "?"),
                cx + PanelStyle.GRID, cy + PanelStyle.GRID, PanelStyle.ERROR);
        g.text(font, Component.literal("Only overlay (editor-saved) files can"),
                cx + PanelStyle.GRID, cy + 28, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("be deleted. Originals will report an error."),
                cx + PanelStyle.GRID, cy + 40, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, "CANCEL", cx + PanelStyle.GRID, cy + h - 32, 96,
                PanelStyle.hit(mouseX, mouseY, cx + PanelStyle.GRID, cy + h - 32, 96, PanelStyle.CONTROL_H), false);
        PanelStyle.button(g, font, "DELETE", cx + w - 96 - PanelStyle.GRID, cy + h - 32, 96,
                PanelStyle.hit(mouseX, mouseY, cx + w - 96 - PanelStyle.GRID, cy + h - 32, 96, PanelStyle.CONTROL_H), true);
    }

    private void renderJsonView(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        EntityWorkingSet.Entry entry = workingSet.entries.get(jsonEntry);
        g.text(font, Component.literal(entry.entityId),
                px + PanelStyle.GRID * 2, py + PanelStyle.GRID * 2, PanelStyle.TEXT);
        int bw = 56;
        PanelStyle.button(g, font, "BACK", px + pw - bw - PanelStyle.GRID * 2,
                py + PanelStyle.GRID * 2 - 6, bw,
                PanelStyle.hit(mouseX, mouseY, px + pw - bw - PanelStyle.GRID * 2,
                        py + PanelStyle.GRID * 2 - 6, bw, PanelStyle.CONTROL_H), false);

        int jy = py + PanelStyle.GRID * 5;
        int jh = ph - PanelStyle.GRID * 7;
        PanelStyle.inset(g, px + PanelStyle.GRID, jy, pw - PanelStyle.GRID * 2, jh);
        int lineH = 10;
        int maxLines = (jh - PanelStyle.GRID * 2) / lineH;
        for (int i = 0; i < maxLines; i++) {
            int li = jsonScroll + i;
            if (li >= jsonLines.size()) break;
            String line = jsonLines.get(li);
            if (font.width(line) > pw - PanelStyle.GRID * 4) {
                line = font.plainSubstrByWidth(line, pw - PanelStyle.GRID * 5) + "…";
            }
            g.text(font, Component.literal(line),
                    px + PanelStyle.GRID * 2, jy + PanelStyle.GRID + i * lineH, PanelStyle.TEXT_DIM);
        }
        PanelStyle.scrollbar(g, px + pw - PanelStyle.GRID - 6, jy, jh,
                jsonLines.size(), maxLines, jsonScroll);
    }

    // ------------------------------------------------------------ actions

    private void spawn(EntityWorkingSet.Entry entry) {
        Services.NETWORK.sendToServer(new EntitiesPayloads.SpawnEntity(entry.entityId));
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("Spawning " + entry.entityId));
        }
    }

    private void openEditor(int entryIdx) {
        Minecraft.getInstance().gui.setScreen(
                new EntityEditorScreen(this, workingSet.entries.get(entryIdx)));
    }

    private void openJson(int entryIdx) {
        jsonEntry = entryIdx;
        jsonScroll = 0;
        EntityWorkingSet.Entry entry = workingSet.entries.get(entryIdx);
        jsonLines = entry.json == null ? List.of("(unparseable json)")
                : List.of(PRETTY.toJson(entry.json).split("\n"));
        if (searchBox != null) searchBox.setVisible(false);
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();

        if (jsonEntry >= 0) {
            int bw = 56;
            if (PanelStyle.hit(mx, my, px + pw - bw - PanelStyle.GRID * 2,
                    py + PanelStyle.GRID * 2 - 6, bw, PanelStyle.CONTROL_H)) {
                jsonEntry = -1;
                if (searchBox != null) searchBox.setVisible(true);
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        if (confirmEntry >= 0) {
            int w = 240, h = 96, cx = (width - w) / 2, cy = (height - h) / 2;
            if (PanelStyle.hit(mx, my, cx + w - 96 - PanelStyle.GRID, cy + h - 32, 96, PanelStyle.CONTROL_H)) {
                EntityWorkingSet.Entry entry = workingSet.entries.get(confirmEntry);
                Services.NETWORK.sendToServer(new EntitiesPayloads.DeleteEntity(entry.entityId));
                workingSet.entries.remove(confirmEntry);
                refresh();
            }
            confirmEntry = -1;
            return true;
        }

        if (menuEntry >= 0) {
            int mw = 120;
            for (int i = 0; i < MENU.length; i++) {
                int iy = menuY + 18 + i * 16;
                if (PanelStyle.hit(mx, my, menuX, iy, mw, 16)) {
                    handleMenu(MENU[i]);
                    return true;
                }
            }
            menuEntry = -1;
            return true;
        }

        int newW = 96;
        if (PanelStyle.hit(mx, my, px + pw - newW - PanelStyle.GRID * 2,
                py + PanelStyle.GRID * 2 - 6, newW, PanelStyle.CONTROL_H)) {
            Minecraft.getInstance().gui.setScreen(new CreateEntityScreen(this, workingSet));
            return true;
        }

        int fw = 110, rw2 = 64;
        int fx = px + pw - PanelStyle.GRID * 2 - rw2 - PanelStyle.GRID - fw;
        int ty = py + PanelStyle.GRID * 5 - 3;
        if (PanelStyle.hit(mx, my, fx, ty, fw, PanelStyle.CONTROL_H)) {
            filterIndex = (filterIndex + 1) % filters.size();
            refilter();
            return true;
        }
        int rx2 = px + pw - PanelStyle.GRID * 2 - rw2;
        if (PanelStyle.hit(mx, my, rx2, ty, rw2, PanelStyle.CONTROL_H)) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.connection.sendCommand("myrpg editor entities");
            }
            return true;
        }

        int rows = listH / ROW;
        for (int r = 0; r < rows; r++) {
            int idx = scroll + r;
            if (idx >= visible.size()) break;
            int ry = listY + r * ROW + 2;
            int rx = px + PanelStyle.GRID + 2;
            int rw = pw - PanelStyle.GRID * 2 - 18;
            if (!PanelStyle.hit(mx, my, rx, ry, rw, ROW - 4)) continue;

            int entryIdx = visible.get(idx);
            if (event.button() == 1) {
                menuEntry = entryIdx;
                menuX = (int) Math.min(mx, px + pw - 128);
                menuY = (int) Math.min(my, py + ph - (18 + MENU.length * 16 + 8));
                return true;
            }
            int bw = 52;
            int bx = rx + rw - bw - 26;
            int by = ry + (ROW - 4 - PanelStyle.CONTROL_H) / 2;
            if (PanelStyle.hit(mx, my, bx, by, bw, PanelStyle.CONTROL_H)) {
                spawn(workingSet.entries.get(entryIdx));
                return true;
            }
            openEditor(entryIdx);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void handleMenu(String action) {
        int entryIdx = menuEntry;
        menuEntry = -1;
        EntityWorkingSet.Entry entry = workingSet.entries.get(entryIdx);
        switch (action) {
            case "OPEN" -> openEditor(entryIdx);
            case "SPAWN" -> spawn(entry);
            case "DUPLICATE" -> {
                EntityWorkingSet.Entry copy = new EntityWorkingSet.Entry(
                        entry.entityId + "_copy",
                        entry.json == null ? null : entry.json.deepCopy());
                copy.dirty = true;
                workingSet.entries.add(copy);
                refresh();
            }
            case "COPY ID" -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(entry.entityId);
                if (minecraft != null && minecraft.player != null) {
                    minecraft.player.sendSystemMessage(
                            Component.literal("Copied " + entry.entityId));
                }
            }
            case "OPEN JSON" -> openJson(entryIdx);
            case "DELETE" -> confirmEntry = entryIdx;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (jsonEntry >= 0) {
            int jh = ph - PanelStyle.GRID * 7;
            int maxLines = (jh - PanelStyle.GRID * 2) / 10;
            int max = Math.max(0, jsonLines.size() - maxLines);
            jsonScroll = Math.max(0, Math.min(max, jsonScroll - (int) Math.signum(vertical) * 3));
            return true;
        }
        int max = Math.max(0, visible.size() - listH / ROW);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(vertical)));
        return true;
    }

    private String lastQuery = "";

    @Override
    public void tick() {
        String query = searchBox == null ? "" : searchBox.getValue();
        if (!query.equals(lastQuery)) {
            lastQuery = query;
            refilter();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
