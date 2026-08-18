package com.myyyst.myrpg.core.client.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.myyyst.myrpg.core.client.StatHudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The object editor. Three framed zones sharing edges: header strip,
 * nav column, content well. All page content renders — and is clipped —
 * inside the well; every page fits without scrolling (lists scroll
 * inside their own frames).
 *
 * <p>The main editing surface for one stat. The nav column switches between {@link Page}s,
 * each of which renders into the same content well; {@link #buildPageWidgets()} recreates
 * the text fields whenever the page changes, since only the current page's controls exist.</p>
 *
 * <p>Every field writes straight into {@code entry.json} through {@code JsonEdit}, so there
 * is no separate apply step - {@code entry.pristine} keeps the state at open time so the
 * screen can tell whether anything actually changed, and saving is an explicit action.</p>
 */
public class StatEditorScreen extends Screen {

    /** Compact form, used for what goes over the wire. */
    private static final Gson GSON = new Gson();
    /** Indented form, used for the raw-JSON view on the ADVANCED page. */
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    /** The nav column's entries; each maps to one render method and widget set. */
    enum Page { GENERAL, VALUE, STAGES, RULES, DISPLAY, ADVANCED }

    private final StatLibraryScreen parent;
    private final StatWorkingSet workingSet;
    /** The stat being edited; its {@code json} is mutated directly. */
    private final StatWorkingSet.Entry entry;
    private Page page = Page.GENERAL;

    // layout — all computed in init(), so the screen re-centres on resize
    private int px, py, pw, ph, navW;
    private int frameTop, frameBottom;
    private int wellFrameX1, wellFrameX2;
    /** Inner content area of the well, where every page draws. */
    private int wellX, wellY, wellW, wellH;

    /** Text fields belonging to the current page only; cleared on every page switch. */
    private final List<EditBox> pageBoxes = new ArrayList<>();

    /** Value the live HUD preview is drawn at; draggable to see other stages. */
    private double previewValue = 60;
    private boolean draggingPreview;

    /** Index of the stage open in the detail form, or -1 for none. */
    private int selectedStage = -1;
    /** Stage threshold currently being dragged on the timeline, or -1. */
    private int draggingThreshold = -1;
    private int stageScroll;
    private int effectScroll;
    private int ruleScroll;

    /** Result of the last {@code StatValidator} run, shown as chips per page. */
    private List<StatValidator.Issue> issues = new ArrayList<>();

    /** True while the "you have unsaved changes" dialog is up. */
    private boolean confirmClose;

    public StatEditorScreen(StatLibraryScreen parent, StatWorkingSet workingSet, StatWorkingSet.Entry entry) {
        super(Component.literal("Stat Editor"));
        this.parent = parent;
        this.workingSet = workingSet;
        this.entry = entry;
        // Snapshot for the unsaved-changes check and for reverting.
        entry.pristine = entry.json == null ? null : entry.json.deepCopy();
    }

    // ------------------------------------------------------------ layout

    /** Computes the three zones (header strip, nav column, content well) and builds widgets. */
    @Override
    protected void init() {
        pw = Math.min(width - 2 * PanelStyle.GRID, 520);
        ph = Math.min(height - 2 * PanelStyle.GRID, 340);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        navW = 88;

        frameTop = py + PanelStyle.GRID * 4 + 8;
        frameBottom = py + ph - PanelStyle.GRID;
        wellFrameX1 = px + navW + PanelStyle.GRID;
        wellFrameX2 = px + pw - PanelStyle.GRID;

        wellX = wellFrameX1 + 8;
        wellY = frameTop + 8;
        wellW = wellFrameX2 - wellFrameX1 - 16;
        wellH = frameBottom - frameTop - 16;

        buildPageWidgets();
    }

    /** Switches page: resets that page's scroll state and rebuilds its widgets. */
    private void setPage(Page newPage) {
        page = newPage;
        effectScroll = 0;
        ruleScroll = 0;
        clearWidgets();
        buildPageWidgets();
    }

    /**
     * Creates the text fields for the current page.
     *
     * <p>Pages that are drawn entirely with buttons and immediate-mode rows (RULES,
     * ADVANCED, and most of STAGES) add nothing here.</p>
     */
    private void buildPageWidgets() {
        pageBoxes.clear();
        int half = wellW / 2;
        switch (page) {
            case GENERAL -> {
                addFieldAt("display.name", "", wellX + 8, wellY + 26, half - 16);
                // "__id" is not a JSON path: it edits the entry's id rather than its content.
                addFieldAt("__id", entry.statId, wellX + half + 8, wellY + 26, half - 16);
                addFieldAt("display.description", "", wellX + 8, wellY + 66, wellW - 16);
                addFieldAt("display.icon", "", wellX + 8, wellY + 106, half - 16);
                addFieldAt("display.color", "#FFFFFF", wellX + half + 8, wellY + 106, 80);
            }
            case VALUE -> {
                addNumberField("value.default", 0, wellY + 26, 64);
                addNumberField("value.min", 0, wellY + 26, 64, 80);
                addNumberField("value.max", 100, wellY + 26, 64, 160);
            }
            case STAGES -> {
                if (selectedStage >= 0 && selectedStage < stages().size()) {
                    var stage = stage(selectedStage);
                    int fieldW = (wellW - 132) / 2;
                    addStageFieldAt(stage, "display.name", wellX + 8, wellY + 28, fieldW);
                    addStageFieldAt(stage, "id", wellX + 16 + fieldW, wellY + 28, fieldW);
                    addStageFieldAt(stage, "min", wellX + 8, wellY + 68, 56);
                    addStageFieldAt(stage, "max", wellX + 8, wellY + 108, 56);
                }
            }
            case DISPLAY -> {
                // The threshold field only exists for the two visibility modes that use it.
                String vis = JsonEdit.getString(entry.json, "hud.visibility", "always");
                if (vis.equals("above_value") || vis.equals("below_value")) {
                    addNumberField("hud.visibility_value", 0, wellY + 62, 64, 320);
                }
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------ json helpers

    /** The "stages" array, created empty on first use. */
    private com.google.gson.JsonArray stages() {
        if (!entry.json.has("stages")) entry.json.add("stages", new com.google.gson.JsonArray());
        return entry.json.getAsJsonArray("stages");
    }

    /** Stage at {@code index} in the stages array. */
    private com.google.gson.JsonObject stage(int index) {
        return stages().get(index).getAsJsonObject();
    }

    /** One of a stage's arrays ("effects", "on_enter", "on_exit"), created empty on first use. */
    private com.google.gson.JsonArray stageArray(com.google.gson.JsonObject stage, String key) {
        if (!stage.has(key)) stage.add(key, new com.google.gson.JsonArray());
        return stage.getAsJsonArray(key);
    }

    /** The "rules" array, created empty on first use. */
    private com.google.gson.JsonArray rulesArray() {
        if (!entry.json.has("rules")) entry.json.add("rules", new com.google.gson.JsonArray());
        return entry.json.getAsJsonArray("rules");
    }

    // ------------------------------------------------------------ widget builders

    /**
     * Text field bound to a dotted JSON path, writing on every keystroke.
     *
     * <p>Clearing the box removes the key entirely rather than storing an empty string,
     * which keeps optional fields absent from the saved file. The pseudo-path
     * {@code "__id"} edits the entry's resource id instead of its JSON.</p>
     */
    private void addFieldAt(String path, String fallback, int x, int y, int w) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
        String value = path.equals("__id") ? entry.statId
                : JsonEdit.getString(entry.json, path, fallback);
        box.setValue(value);
        box.setResponder(text -> {
            if (path.equals("__id")) {
                entry.statId = text;
            } else if (text.isEmpty()) {
                JsonEdit.remove(entry.json, path);
            } else {
                JsonEdit.set(entry.json, path, text);
            }
            entry.dirty = true;
        });
        addRenderableWidget(box);
        pageBoxes.add(box);
    }

    /** Numeric field at the well's left edge. */
    private void addNumberField(String path, double fallback, int y, int w) {
        addNumberField(path, fallback, y, w, 0);
    }

    /**
     * Numeric field, offset from the well's left edge.
     * Unparseable text is ignored, so a half-typed number never clobbers the value.
     */
    private void addNumberField(String path, double fallback, int y, int w, int xOffset) {
        EditBox box = new EditBox(font, wellX + 8 + xOffset, y, w, 18, Component.empty());
        box.setValue(trimNum(JsonEdit.getDouble(entry.json, path, fallback)));
        box.setResponder(text -> {
            try {
                JsonEdit.set(entry.json, path, Double.parseDouble(text.trim()));
                entry.dirty = true;
            } catch (NumberFormatException ignored) { }
        });
        addRenderableWidget(box);
        pageBoxes.add(box);
    }

    /**
     * Field bound to a path inside one stage object rather than the stat root.
     * "min"/"max" are treated as numbers; everything else as optional text.
     */
    private void addStageFieldAt(com.google.gson.JsonObject stage, String path, int x, int y, int w) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
        boolean numeric = path.equals("min") || path.equals("max");
        box.setValue(numeric
                ? trimNum(JsonEdit.getDouble(stage, path, 0))
                : JsonEdit.getString(stage, path, ""));
        box.setResponder(text -> {
            if (numeric) {
                try { JsonEdit.set(stage, path, Double.parseDouble(text.trim())); }
                catch (NumberFormatException ignored) { return; }
            } else if (text.isEmpty()) {
                JsonEdit.remove(stage, path);
            } else {
                JsonEdit.set(stage, path, text);
            }
            entry.dirty = true;
        });
        addRenderableWidget(box);
        pageBoxes.add(box);
    }

    /** Drops the ".0" from whole numbers so fields show what the author typed. */
    private static String trimNum(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    // ------------------------------------------------------------ render

    /** Draws the three zones, then the current page clipped inside the content well. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, px, py, pw, ph);

        renderHeader(g, mouseX, mouseY);

        PanelStyle.inset(g, px + PanelStyle.GRID, frameTop,
                navW - PanelStyle.GRID, frameBottom - frameTop);
        renderNav(g, mouseX, mouseY);

        PanelStyle.inset(g, wellFrameX1, frameTop,
                wellFrameX2 - wellFrameX1, frameBottom - frameTop);
        g.fill(wellFrameX1 + 1, frameTop + 1, wellFrameX2 - 1, frameBottom - 1, PanelStyle.PANEL_BG);

        // Scissor clips page content to the well, so a long list cannot bleed over the
        // nav column or the header.
        // NOTE drift: scissor spelling — adjust or remove if g. lacks it.
        g.enableScissor(wellFrameX1 + 1, frameTop + 1, wellFrameX2 - 1, frameBottom - 1);
        switch (page) {
            case GENERAL -> renderGeneral(g);
            case VALUE -> renderValue(g, mouseX, mouseY);
            case STAGES -> renderStages(g, mouseX, mouseY);
            case RULES -> renderRules(g, mouseX, mouseY);
            case DISPLAY -> renderDisplay(g, mouseX, mouseY);
            case ADVANCED -> renderAdvanced(g, mouseX, mouseY);
        }
        g.disableScissor();

        super.extractRenderState(g, mouseX, mouseY, delta);

        if (confirmClose) renderConfirmClose(g, mouseX, mouseY);
    }

    /** Header strip: back arrow, stat name, UNSAVED chip, save button. */
    private void renderHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int hy = py + PanelStyle.GRID;
        PanelStyle.button(g, font, "<", px + PanelStyle.GRID, hy, 20,
                PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID, hy, 20, PanelStyle.CONTROL_H), false);
        String name = entry.displayName().toUpperCase();
        g.text(font, Component.literal(name), px + PanelStyle.GRID + 28, hy + 8, PanelStyle.TEXT);
        if (entry.dirty) {
            PanelStyle.chip(g, font, "UNSAVED", px + PanelStyle.GRID + 32 + font.width(name), hy + 6, PanelStyle.EDITED);
        }
        PanelStyle.button(g, font, "SAVE", px + pw - 64 - PanelStyle.GRID, hy, 64,
                PanelStyle.hit(mouseX, mouseY, px + pw - 64 - PanelStyle.GRID, hy, 64, PanelStyle.CONTROL_H), true);

        g.fill(px + PanelStyle.GRID, py + PanelStyle.GRID * 4 + 2,
                px + pw - PanelStyle.GRID, py + PanelStyle.GRID * 4 + 3, PanelStyle.PANEL_DARK);
        g.fill(px + PanelStyle.GRID, py + PanelStyle.GRID * 4 + 3,
                px + pw - PanelStyle.GRID, py + PanelStyle.GRID * 4 + 4, PanelStyle.PANEL_LIGHT);
    }

    /** Nav column: one row per {@link Page}, the current one highlighted. */
    private void renderNav(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int ny = frameTop + 6;
        for (Page p : Page.values()) {
            boolean selected = p == page;
            boolean hovered = PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID + 2, ny, navW - PanelStyle.GRID - 4, 20);
            if (selected) {
                g.fill(px + PanelStyle.GRID + 2, ny, px + navW - 2, ny + 20, PanelStyle.ROW_SELECT);
            } else if (hovered) {
                g.fill(px + PanelStyle.GRID + 2, ny, px + navW - 2, ny + 20, PanelStyle.ROW_HOVER);
            }
            g.text(font, Component.literal(p.name()), px + PanelStyle.GRID * 2, ny + 6,
                    selected ? PanelStyle.TEXT : PanelStyle.TEXT_DIM);
            ny += 22;
        }
    }

    /** GENERAL page: name, id, description, icon and colour. */
    private void renderGeneral(GuiGraphicsExtractor g) {
        int half = wellW / 2;
        g.text(font, Component.literal("GENERAL"), wellX + 8, wellY + 4, PanelStyle.TEXT);
        g.text(font, Component.literal("DISPLAY NAME"), wellX + 8, wellY + 16, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("RESOURCE ID"), wellX + half + 8, wellY + 16, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("DESCRIPTION"), wellX + 8, wellY + 56, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("ICON (texture path)"), wellX + 8, wellY + 96, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("COLOR"), wellX + half + 8, wellY + 96, PanelStyle.TEXT_DIM);
        String hex = JsonEdit.getString(entry.json, "display.color", "#FFFFFF");
        g.fill(wellX + half + 96, wellY + 106, wellX + half + 114, wellY + 124, 0xFF000000 | parseColor(hex));
    }

    /** VALUE page: default/min/max, the decimal and clamp flags, and persistence. */
    private void renderValue(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("VALUE"), wellX + 8, wellY + 4, PanelStyle.TEXT);
        g.text(font, Component.literal("DEFAULT"), wellX + 8, wellY + 16, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("MIN"), wellX + 88, wellY + 16, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("MAX"), wellX + 168, wellY + 16, PanelStyle.TEXT_DIM);

        boolean decimal = JsonEdit.getBool(entry.json, "value.decimal", false);
        renderToggle(g, mouseX, mouseY, "NUMBER TYPE", decimal ? "Decimal" : "Integer", wellX + 8, wellY + 56);
        boolean clamp = JsonEdit.getBool(entry.json, "value.clamp", true);
        renderCheckbox(g, mouseX, mouseY, "Clamp value to range", clamp, wellX + 168, wellY + 66);

        g.text(font, Component.literal("PERSISTENCE"), wellX + 8, wellY + 100, PanelStyle.TEXT);
        boolean keep = JsonEdit.getBool(entry.json, "persistence.keep_on_death", true);
        renderToggle(g, mouseX, mouseY, "ON DEATH", keep ? "Keep value" : "Reset", wellX + 8, wellY + 112);
        boolean respawnReset = JsonEdit.getBool(entry.json, "persistence.reset_on_respawn", false);
        renderToggle(g, mouseX, mouseY, "ON RESPAWN", respawnReset ? "Reset" : "Keep value", wellX + 168, wellY + 112);
    }

    /** STAGES page: the timeline overview, or one stage's detail form when one is selected. */
    private void renderStages(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (selectedStage >= 0 && selectedStage < stages().size()) {
            renderStageDetail(g, mouseX, mouseY);
        } else {
            renderStageList(g, mouseX, mouseY);
        }
    }

    /**
     * Stage overview: a timeline spanning the stat's min..max with one band per stage,
     * above a scrolling list. Positions are computed as fractions of the value range, so
     * the timeline reflects the actual configured bounds.
     */
    private void renderStageList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("STAGES"), wellX + 8, wellY + 4, PanelStyle.TEXT);
        PanelStyle.button(g, font, "+ ADD STAGE", wellX + wellW - 100, wellY, 92,
                PanelStyle.hit(mouseX, mouseY, wellX + wellW - 100, wellY, 92, PanelStyle.CONTROL_H), true);

        double min = JsonEdit.getDouble(entry.json, "value.min", 0);
        double max = JsonEdit.getDouble(entry.json, "value.max", 100);
        int tlX = wellX + 8, tlY = wellY + 30, tlW = wellW - 16, tlH = 26;

        PanelStyle.inset(g, tlX, tlY, tlW, tlH);
        var arr = stages();
        // pass 1: segments
        for (int i = 0; i < arr.size(); i++) {
            var stage = stage(i);
            double sMin = JsonEdit.getDouble(stage, "min", 0);
            double sMax = JsonEdit.getDouble(stage, "max", 0);
            int x1 = tlX + 2 + (int) ((sMin - min) / (max - min) * (tlW - 4));
            int x2 = tlX + 2 + (int) ((sMax + 1 - min) / (max - min) * (tlW - 4));
            x2 = Math.min(x2, tlX + tlW - 2);
            int color = i % 2 == 0 ? 0xFF3A3A40 : 0xFF44444A;
            g.fill(x1, tlY + 2, x2, tlY + tlH - 2, color);
            String label = JsonEdit.getString(stage, "id", "?");
            if (font.width(label) < x2 - x1 - 4) {
                g.text(font, Component.literal(label), x1 + 3, tlY + 9, PanelStyle.TEXT_DIM);
            }
        }
        // pass 2: threshold handles, on top
        for (int i = 0; i < arr.size() - 1; i++) {
            double sMax = JsonEdit.getDouble(stage(i), "max", 0);
            int x2 = tlX + 2 + (int) ((sMax + 1 - min) / (max - min) * (tlW - 4));
            x2 = Math.min(x2, tlX + tlW - 2);
            boolean hot = draggingThreshold == i
                    || PanelStyle.hit(mouseX, mouseY, x2 - 3, tlY, 6, tlH);
            g.fill(x2 - 1, tlY, x2 + 1, tlY + tlH, hot ? PanelStyle.ACCENT : PanelStyle.PANEL_LIGHT);
        }
        g.text(font, Component.literal(trimNum(min)), tlX, tlY + tlH + 3, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal(trimNum(max)), tlX + tlW - font.width(trimNum(max)), tlY + tlH + 3, PanelStyle.TEXT_DIM);
        if (draggingThreshold >= 0) {
            double t = JsonEdit.getDouble(stage(draggingThreshold + 1), "min", 0);
            g.text(font, Component.literal("THRESHOLD " + trimNum(t)),
                    tlX + (tlW - font.width("THRESHOLD " + trimNum(t))) / 2, tlY + tlH + 3, PanelStyle.ACCENT);
        }

        int listTop = wellY + 76;
        int listBottom = wellY + wellH - 4;
        int rows = (listBottom - listTop) / 18;
        for (int r = 0; r < rows; r++) {
            int i = stageScroll + r;
            if (i >= arr.size()) break;
            int rowY = listTop + r * 18;
            var stage = stage(i);
            boolean hovered = PanelStyle.hit(mouseX, mouseY, wellX + 8, rowY, wellW - 16, 16);
            if (hovered) g.fill(wellX + 8, rowY, wellX + wellW - 8, rowY + 16, PanelStyle.ROW_HOVER);
            g.text(font, Component.literal(JsonEdit.getString(stage, "id", "?").toUpperCase()),
                    wellX + 12, rowY + 4, PanelStyle.TEXT);
            String range = trimNum(JsonEdit.getDouble(stage, "min", 0)) + " - "
                    + trimNum(JsonEdit.getDouble(stage, "max", 0));
            g.text(font, Component.literal(range + "  >"), wellX + wellW - 24 - font.width(range),
                    rowY + 4, PanelStyle.TEXT_DIM);
        }
        if (arr.isEmpty()) {
            g.text(font, Component.literal("No stages. + ADD STAGE to begin."),
                    wellX + 12, listTop + 4, PanelStyle.TEXT_DIM);
        }
    }

    /** One stage's form: name/id/range fields plus its effects and enter/exit action lists. */
    private void renderStageDetail(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        var stage = stage(selectedStage);
        String stageId = JsonEdit.getString(stage, "id", "?").toUpperCase();
        int fieldW = (wellW - 132) / 2;

        boolean crumbHover = PanelStyle.hit(mouseX, mouseY, wellX + 8, wellY + 2, font.width("< STAGES"), 12);
        g.text(font, Component.literal("< STAGES"), wellX + 8, wellY + 4,
                crumbHover ? PanelStyle.TEXT : PanelStyle.ACCENT);
        g.text(font, Component.literal(" / " + stageId), wellX + 8 + font.width("< STAGES"), wellY + 4, PanelStyle.TEXT);
        PanelStyle.button(g, font, "DELETE", wellX + wellW - 68, wellY - 2, 60,
                PanelStyle.hit(mouseX, mouseY, wellX + wellW - 68, wellY - 2, 60, PanelStyle.CONTROL_H), false);

        // row 1: NAME | STAGE ID, ADD EFFECT right-aligned
        g.text(font, Component.literal("NAME"), wellX + 8, wellY + 18, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("STAGE ID"), wellX + 16 + fieldW, wellY + 18, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, "+ ADD EFFECT", wellX + wellW - 100, wellY + 24, 92,
                PanelStyle.hit(mouseX, mouseY, wellX + wellW - 100, wellY + 24, 92, PanelStyle.CONTROL_H), true);

        // left column: FROM over TO
        g.text(font, Component.literal("FROM"), wellX + 8, wellY + 58, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("TO"), wellX + 8, wellY + 98, PanelStyle.TEXT_DIM);

        // effect list frame: right of the left column, down to the events row
        int frameX = wellX + 128;
        int frameW = wellW - 136;
        int frameY = wellY + 56;
        int eventsRowY = wellY + wellH - 26;
        int frameH = eventsRowY - frameY - 8;

        PanelStyle.inset(g, frameX, frameY, frameW, frameH);
        var effects = stageArray(stage, "effects");
        int rowH = 18;
        int visibleRows = (frameH - 4) / rowH;
        int maxScroll = Math.max(0, effects.size() - visibleRows);
        effectScroll = Math.min(effectScroll, maxScroll);

        for (int r = 0; r < visibleRows; r++) {
            int i = effectScroll + r;
            if (i >= effects.size()) break;
            var effect = effects.get(i).getAsJsonObject();
            int ry = frameY + 2 + r * rowH;
            int rx = frameX + 2;
            int rw = frameW - 20;
            String type = effect.has("type") ? effect.get("type").getAsString() : "?";
            var schema = EffectSchemas.all().get(type);
            boolean editable = schema != null;
            boolean hovered = PanelStyle.hit(mouseX, mouseY, rx, ry, rw - 18, rowH - 2);
            g.fill(rx, ry, rx + rw, ry + rowH - 2, hovered && editable ? PanelStyle.ROW_HOVER : PanelStyle.ROW_BG);
            g.text(font, Component.literal(editable ? schema.label() : type), rx + 4, ry + 4,
                    editable ? PanelStyle.TEXT : PanelStyle.EDITED);
            g.text(font, Component.literal("X"), rx + rw - 12, ry + 4,
                    PanelStyle.hit(mouseX, mouseY, rx + rw - 16, ry + 2, 12, 12) ? PanelStyle.ERROR : PanelStyle.TEXT_DIM);
        }
        if (effects.isEmpty()) {
            g.text(font, Component.literal("No effects in this stage."),
                    frameX + 8, frameY + 6, PanelStyle.TEXT_DIM);
        }
        PanelStyle.scrollbar(g, frameX + frameW - 6, frameY + 2, frameH - 4,
                effects.size(), visibleRows, effectScroll);

        // events, bottom split
        int eventBtnW = (wellW - 24) / 2;
        PanelStyle.button(g, font, "ON ENTER (" + stageArray(stage, "on_enter").size() + ")",
                wellX + 8, eventsRowY, eventBtnW,
                PanelStyle.hit(mouseX, mouseY, wellX + 8, eventsRowY, eventBtnW, PanelStyle.CONTROL_H), false);
        PanelStyle.button(g, font, "ON EXIT (" + stageArray(stage, "on_exit").size() + ")",
                wellX + 16 + eventBtnW, eventsRowY, eventBtnW,
                PanelStyle.hit(mouseX, mouseY, wellX + 16 + eventBtnW, eventsRowY, eventBtnW, PanelStyle.CONTROL_H), false);
    }

    /** RULES page: one row per rule, summarised as trigger + condition/action counts. */
    private void renderRules(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("RULES"), wellX + 8, wellY + 4, PanelStyle.TEXT);
        g.text(font, Component.literal("Automatic ways this stat changes."), wellX + 8, wellY + 16, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, "+ NEW RULE", wellX + wellW - 92, wellY, 84,
                PanelStyle.hit(mouseX, mouseY, wellX + wellW - 92, wellY, 84, PanelStyle.CONTROL_H), true);

        int frameY = wellY + 30;
        int frameH = wellH - 34;
        PanelStyle.inset(g, wellX + 8, frameY, wellW - 16, frameH);

        var rules = rulesArray();
        int rowH = 52;
        int visibleRows = (frameH - 4) / rowH;
        int maxScroll = Math.max(0, rules.size() - visibleRows);
        ruleScroll = Math.min(ruleScroll, maxScroll);

        for (int r = 0; r < visibleRows; r++) {
            int i = ruleScroll + r;
            if (i >= rules.size()) break;
            var rule = rules.get(i).getAsJsonObject();
            int ry = frameY + 2 + r * rowH;
            int rx = wellX + 10;
            int rw = wellW - 28;
            boolean hovered = PanelStyle.hit(mouseX, mouseY, rx, ry, rw, rowH - 4);
            g.fill(rx, ry, rx + rw, ry + rowH - 4, hovered ? PanelStyle.ROW_HOVER : PanelStyle.ROW_BG);

            g.text(font, Component.literal(String.format("%02d", i + 1)), rx + 6, ry + 6, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("TRIGGER  " + summarizeTrigger(rule)), rx + 28, ry + 6, PanelStyle.TEXT);
            g.text(font, Component.literal("IF  " + summarizeList(rule, "conditions", "always")), rx + 28, ry + 18, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("DO  " + summarizeList(rule, "actions", "nothing")), rx + 28, ry + 30, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("X"), rx + rw - 14, ry + 6,
                    PanelStyle.hit(mouseX, mouseY, rx + rw - 18, ry + 4, 12, 12) ? PanelStyle.ERROR : PanelStyle.TEXT_DIM);
        }
        if (rules.isEmpty()) {
            g.text(font, Component.literal("No rules yet."), wellX + 16, frameY + 8, PanelStyle.TEXT_DIM);
        }

        PanelStyle.scrollbar(g, wellX + wellW - 14, frameY + 2, frameH - 4,
                rules.size(), visibleRows, ruleScroll);
    }

    /** Human-readable one-liner for a rule's trigger ("every 200 ticks", "on player_death"). */
    private String summarizeTrigger(com.google.gson.JsonObject rule) {
        if (!rule.has("trigger")) return "(none)";
        var trigger = rule.getAsJsonObject("trigger");
        String type = trigger.has("type") ? trigger.get("type").getAsString() : "?";
        if (type.endsWith(":interval")) {
            return "EVERY " + (trigger.has("ticks") ? trigger.get("ticks").getAsInt() : 0) + " TICKS";
        }
        if (type.endsWith(":event")) {
            String event = trigger.has("event") ? trigger.get("event").getAsString() : "?";
            return "ON " + event.substring(event.indexOf(':') + 1).replace('_', ' ').toUpperCase();
        }
        return type;
    }

    /** "3 conditions" / the empty word when the list is absent or empty. */
    private String summarizeList(com.google.gson.JsonObject rule, String key, String emptyWord) {
        if (!rule.has(key) || rule.getAsJsonArray(key).isEmpty()) return emptyWord;
        var array = rule.getAsJsonArray(key);
        var first = array.get(0).getAsJsonObject();
        String type = first.has("type") ? first.get("type").getAsString() : "?";
        String label = type.substring(type.indexOf(':') + 1).replace('_', ' ');
        return array.size() == 1 ? label : label + " +" + (array.size() - 1) + " more";
    }

    /**
     * DISPLAY page: HUD type and visibility options, with a live preview drawn by the real
     * {@code StatHudOverlay} - so what the author sees here is exactly what players get.
     * The preview value is draggable to check how the bar looks across the range.
     */
    private void renderDisplay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("DISPLAY"), wellX + 8, wellY + 4, PanelStyle.TEXT);
        boolean visible = JsonEdit.getBool(entry.json, "hud.visible", false);
        renderCheckbox(g, mouseX, mouseY, "Show on HUD", visible, wellX + 8, wellY + 16);
        boolean showValue = JsonEdit.getBool(entry.json, "hud.show_value", false);
        renderCheckbox(g, mouseX, mouseY, "Show numeric value", showValue, wellX + 168, wellY + 16);

        String type = JsonEdit.getString(entry.json, "hud.type", "bar");
        renderToggle(g, mouseX, mouseY, "DISPLAY TYPE", type, wellX + 8, wellY + 38);
        String vis = JsonEdit.getString(entry.json, "hud.visibility", "always");
        renderToggle(g, mouseX, mouseY, "VISIBILITY", vis, wellX + 168, wellY + 38);

        // conditional threshold field (widget built in buildPageWidgets)
        if (vis.equals("above_value") || vis.equals("below_value")) {
            g.text(font, Component.literal("THRESHOLD"), wellX + 320, wellY + 52, PanelStyle.TEXT_DIM);
        }

        // ---- live preview, matching the HUD's rendering per type ----
        g.text(font, Component.literal("LIVE PREVIEW"), wellX + 8, wellY + 78, PanelStyle.TEXT_DIM);
        PanelStyle.inset(g, wellX + 8, wellY + 88, wellW - 16, 40);

        double min = JsonEdit.getDouble(entry.json, "value.min", 0);
        double max = JsonEdit.getDouble(entry.json, "value.max", 100);
        int color = parseColor(JsonEdit.getString(entry.json, "display.color", "#FFFFFF"));
        double frac = Math.max(0, Math.min(1, (previewValue - min) / Math.max(1e-9, max - min)));
        String name = entry.displayName();
        int pvX = wellX + 16, pvY = wellY + 94;

        switch (type) {
            case "hidden" -> g.text(font,
                    Component.literal("(hidden type - synced but never drawn)"),
                    pvX, pvY + 8, PanelStyle.TEXT_DIM);
            case "number" -> g.text(font,
                    Component.literal(name + ": " + trimNum(previewValue)),
                    pvX, pvY + 8, 0xFF000000 | color);
            case "percentage" -> g.text(font,
                    Component.literal(name + ": " + Math.round(frac * 100) + "%"),
                    pvX, pvY + 8, 0xFF000000 | color);
            case "icons" -> {
                g.text(font, Component.literal(name), pvX, pvY, 0xFFFFFFFF);
                String icon = JsonEdit.getString(entry.json, "display.icon", "");
                int pips = 10;
                int filled = (int) Math.round(frac * pips);
                int pipY = pvY + 12;
                for (int i = 0; i < pips; i++) {
                    StatHudOverlay.drawIconSlot(g, icon, pvX + i * 10, pipY, i < filled, color);
                }
                if (showValue) {
                    g.text(font, Component.literal(trimNum(previewValue)),
                            pvX + pips * 10 + 4, pipY, 0xFFFFFFFF);
                }
            }
            default -> {   // bar — the shared boss-bar painter
                g.text(font, Component.literal(name), pvX, pvY, 0xFFFFFFFF);
                int barY = pvY + 12;
                int barW = Math.min(120, wellW - 140);
                StatHudOverlay.drawBar(g, pvX, barY, barW, frac, color);
                if (showValue) {
                    g.text(font, Component.literal(trimNum(previewValue) + " / " + trimNum(max)),
                            pvX + barW + 6, barY, 0xFFFFFFFF);
                }
            }
        }

        // preview value slider
        g.text(font, Component.literal("PREVIEW VALUE"), wellX + 8, wellY + 136, PanelStyle.TEXT_DIM);
        int sliderX = wellX + 8, sliderY = wellY + 148, sliderW = wellW - 16;
        g.fill(sliderX, sliderY + 3, sliderX + sliderW, sliderY + 5, PanelStyle.PANEL_DARK);
        int knobX = sliderX + (int) (sliderW * frac);
        g.fill(knobX - 2, sliderY - 2, knobX + 3, sliderY + 10, PanelStyle.ACCENT);
    }

    /** ADVANCED page: the validation report plus a read-only pretty-printed JSON view. */
    private void renderAdvanced(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("ADVANCED"), wellX + 8, wellY + 4, PanelStyle.TEXT);

        g.text(font, Component.literal("FILE"), wellX + 8, wellY + 18, PanelStyle.TEXT_DIM);
        String ns = entry.statId.contains(":") ? entry.statId.split(":", 2)[0] : "?";
        String path = entry.statId.contains(":") ? entry.statId.split(":", 2)[1] : entry.statId;
        g.text(font, Component.literal("datapacks/myrpg_editor/data/" + ns + "/myrpg/stats/" + path + ".json"),
                wellX + 8, wellY + 28, PanelStyle.TEXT);

        issues = StatValidator.validate(entry.statId, entry.json);
        long errors = issues.stream().filter(i -> i.level() == StatValidator.Level.ERROR).count();
        g.text(font, Component.literal("VALIDATION"), wellX + 8, wellY + 46, PanelStyle.TEXT_DIM);
        if (issues.isEmpty()) {
            PanelStyle.chip(g, font, "NO ISSUES", wellX + 70, wellY + 44, PanelStyle.VALID);
        } else {
            PanelStyle.chip(g, font, errors + " ERROR", wellX + 70, wellY + 44, errors > 0 ? PanelStyle.ERROR : PanelStyle.VALID);
            PanelStyle.chip(g, font, (issues.size() - errors) + " WARN", wellX + 130, wellY + 44, PanelStyle.EDITED);
        }
        int iy = wellY + 60;
        for (int i = 0; i < issues.size() && iy < wellY + 108; i++) {
            var issue = issues.get(i);
            int color = issue.level() == StatValidator.Level.ERROR ? PanelStyle.ERROR : PanelStyle.EDITED;
            boolean hovered = PanelStyle.hit(mouseX, mouseY, wellX + 8, iy, wellW - 16, 12);
            g.text(font, Component.literal((hovered ? "> " : "  ") + issue.page() + "  " + issue.message()),
                    wellX + 8, iy, hovered ? PanelStyle.TEXT : color);
            iy += 13;
        }

        int previewLabelY = iy + 4;
        g.text(font, Component.literal("JSON PREVIEW"), wellX + 8, previewLabelY + 6, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, "COPY", wellX + wellW - 60, previewLabelY - 2, 52,
                PanelStyle.hit(mouseX, mouseY, wellX + wellW - 60, previewLabelY - 2, 52, PanelStyle.CONTROL_H), false);
        int jsonY = previewLabelY + 24;
        PanelStyle.inset(g, wellX + 8, jsonY, wellW - 16, wellY + wellH - jsonY - 4);
        String[] lines = PRETTY.toJson(entry.json).split("\n");
        int ly = jsonY + 4;
        for (int i = 0; i < lines.length && ly < wellY + wellH - 12; i++) {
            String line = lines[i].length() > 52 ? lines[i].substring(0, 52) + "..." : lines[i];
            g.text(font, Component.literal(line), wellX + 12, ly, PanelStyle.TEXT_DIM);
            ly += 9;
        }
    }

    /** Labelled cycle button; the click handling lives in {@link #mouseClicked}. */
    private void renderToggle(GuiGraphicsExtractor g, int mouseX, int mouseY, String label, String value, int x, int y) {
        g.text(font, Component.literal(label), x, y, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, value, x, y + 10, 140,
                PanelStyle.hit(mouseX, mouseY, x, y + 10, 140, PanelStyle.CONTROL_H), false);
    }

    /** Labelled checkbox; the click handling lives in {@link #mouseClicked}. */
    private void renderCheckbox(GuiGraphicsExtractor g, int mouseX, int mouseY, String label, boolean checked, int x, int y) {
        PanelStyle.inset(g, x, y, 14, 14);
        if (checked) g.fill(x + 3, y + 3, x + 11, y + 11, PanelStyle.ACCENT);
        g.text(font, Component.literal(label), x + 20, y + 3, PanelStyle.TEXT);
    }

    /** "Discard unsaved changes?" dialog, shown when leaving a dirty entry. */
    private void renderConfirmClose(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int w = 240, h = 100, dx = (width - w) / 2, dy = (height - h) / 2;
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, dx, dy, w, h);
        g.text(font, Component.literal("UNSAVED CHANGES"), dx + PanelStyle.GRID, dy + PanelStyle.GRID, PanelStyle.TEXT);
        g.text(font, Component.literal(entry.displayName() + " has changes that are not saved."),
                dx + PanelStyle.GRID, dy + 26, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, "DISCARD", dx + PanelStyle.GRID, dy + h - 32, 66,
                PanelStyle.hit(mouseX, mouseY, dx + PanelStyle.GRID, dy + h - 32, 66, PanelStyle.CONTROL_H), false);
        PanelStyle.button(g, font, "CANCEL", dx + (w - 66) / 2, dy + h - 32, 66,
                PanelStyle.hit(mouseX, mouseY, dx + (w - 66) / 2, dy + h - 32, 66, PanelStyle.CONTROL_H), false);
        PanelStyle.button(g, font, "SAVE", dx + w - 66 - PanelStyle.GRID, dy + h - 32, 66,
                PanelStyle.hit(mouseX, mouseY, dx + w - 66 - PanelStyle.GRID, dy + h - 32, 66, PanelStyle.CONTROL_H), true);
    }

    // ------------------------------------------------------------ input

    /**
     * All hit-testing for the immediate-mode controls, dispatched per page.
     *
     * <p>Long by nature: the buttons, toggles, timeline handles and list rows are drawn
     * directly rather than as widgets, so every clickable region has to be re-derived here
     * from the same geometry the render methods use.</p>
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();

        if (confirmClose) {
            int w = 240, h = 100, dx = (width - w) / 2, dy = (height - h) / 2;
            if (PanelStyle.hit(mx, my, dx + PanelStyle.GRID, dy + h - 32, 66, PanelStyle.CONTROL_H)) {
                if (entry.pristine != null) {
                    entry.json = entry.pristine.deepCopy();
                }
                entry.dirty = false;
                confirmClose = false;
                Minecraft.getInstance().gui.setScreen(parent);
            } else if (PanelStyle.hit(mx, my, dx + w - 66 - PanelStyle.GRID, dy + h - 32, 66, PanelStyle.CONTROL_H)) {
                save();
                if (!entry.dirty) {
                    confirmClose = false;
                    Minecraft.getInstance().gui.setScreen(parent);
                } else {
                    confirmClose = false;
                }
            } else {
                confirmClose = false;
            }
            return true;
        }

        // header
        if (PanelStyle.hit(mx, my, px + PanelStyle.GRID, py + PanelStyle.GRID, 20, PanelStyle.CONTROL_H)) {
            if (entry.dirty) confirmClose = true;
            else Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        if (PanelStyle.hit(mx, my, px + pw - 64 - PanelStyle.GRID, py + PanelStyle.GRID, 64, PanelStyle.CONTROL_H)) {
            save();
            return true;
        }

        // nav
        int ny = frameTop + 6;
        for (Page p : Page.values()) {
            if (PanelStyle.hit(mx, my, px + PanelStyle.GRID + 2, ny, navW - PanelStyle.GRID - 4, 20)) {
                setPage(p);
                return true;
            }
            ny += 22;
        }

        if (page == Page.VALUE) {
            if (PanelStyle.hit(mx, my, wellX + 8, wellY + 66, 140, PanelStyle.CONTROL_H)) {
                toggleBool("value.decimal", false);
                return true;
            }
            if (PanelStyle.hit(mx, my, wellX + 168, wellY + 66, 14, 14)) {
                toggleBool("value.clamp", true);
                return true;
            }
            if (PanelStyle.hit(mx, my, wellX + 8, wellY + 122, 140, PanelStyle.CONTROL_H)) {
                toggleBool("persistence.keep_on_death", true);
                return true;
            }
            if (PanelStyle.hit(mx, my, wellX + 168, wellY + 122, 140, PanelStyle.CONTROL_H)) {
                toggleBool("persistence.reset_on_respawn", false);
                return true;
            }
        }

        if (page == Page.DISPLAY) {
            if (PanelStyle.hit(mx, my, wellX + 8, wellY + 16, 14, 14)) {
                toggleBool("hud.visible", false);
                return true;
            }
            if (PanelStyle.hit(mx, my, wellX + 168, wellY + 16, 14, 14)) {
                toggleBool("hud.show_value", false);
                return true;
            }
            if (PanelStyle.hit(mx, my, wellX + 8, wellY + 48, 140, PanelStyle.CONTROL_H)) {
                cycleString("hud.type", new String[]{"bar", "number", "percentage", "icons", "hidden"});
                setPage(Page.DISPLAY);                                    // ← ADD
                return true;
            }
            if (PanelStyle.hit(mx, my, wellX + 168, wellY + 48, 140, PanelStyle.CONTROL_H)) {
                cycleString("hud.visibility", new String[]{"always", "never", "when_non_default", "above_value", "below_value"});
                setPage(Page.DISPLAY);                                    // ← ADD
                return true;
            }
            int sliderX = wellX + 8, sliderY = wellY + 148, sliderW = wellW - 16;
            if (PanelStyle.hit(mx, my, sliderX, sliderY - 4, sliderW, 16)) {
                draggingPreview = true;
                updatePreview(mx, sliderX, sliderW);
                return true;
            }
        }

        if (page == Page.STAGES) {
            var arr = stages();

            if (selectedStage >= 0 && selectedStage < arr.size()) {
                // ---- detail view ----
                if (PanelStyle.hit(mx, my, wellX + 8, wellY + 2, font.width("< STAGES"), 12)) {
                    selectedStage = -1;
                    setPage(Page.STAGES);
                    return true;
                }
                if (PanelStyle.hit(mx, my, wellX + wellW - 68, wellY - 2, 60, PanelStyle.CONTROL_H)) {
                    arr.remove(selectedStage);
                    selectedStage = -1;
                    entry.dirty = true;
                    setPage(Page.STAGES);
                    return true;
                }

                // + ADD EFFECT: straight into the picker
                if (PanelStyle.hit(mx, my, wellX + wellW - 100, wellY + 24, 92, PanelStyle.CONTROL_H)) {
                    var selectedStageObj = stage(selectedStage);
                    Minecraft.getInstance().gui.setScreen(new TypedObjectListScreen(this, "EFFECTS",
                            stageArray(selectedStageObj, "effects"),
                            TypedObjectListScreen.Kind.EFFECT, this::markDirtyFromChild, true));
                    return true;
                }

                int frameX = wellX + 128;
                int frameW = wellW - 136;
                int frameY = wellY + 56;
                int eventsRowY = wellY + wellH - 26;
                int frameH = eventsRowY - frameY - 8;

                var effects = stageArray(stage(selectedStage), "effects");
                int rowH = 18;
                int visibleRows = (frameH - 4) / rowH;
                for (int r = 0; r < visibleRows; r++) {
                    int i = effectScroll + r;
                    if (i >= effects.size()) break;
                    int ry = frameY + 2 + r * rowH;
                    int rx = frameX + 2;
                    int rw = frameW - 20;
                    if (PanelStyle.hit(mx, my, rx + rw - 16, ry + 2, 12, 12)) {
                        effects.remove(i);
                        entry.dirty = true;
                        return true;
                    }
                    if (PanelStyle.hit(mx, my, rx, ry, rw - 18, rowH - 2)) {
                        var effect = effects.get(i).getAsJsonObject();
                        String type = effect.has("type") ? effect.get("type").getAsString() : "?";
                        var schema = EffectSchemas.all().get(type);
                        if (schema != null) {
                            Minecraft.getInstance().gui.setScreen(new TypedObjectConfigScreen(
                                    this, effects, schema.typeId(), schema.label(), schema.fields(),
                                    effect, this::markDirtyFromChild));
                        }
                        return true;
                    }
                }

                var selectedStageObj = stage(selectedStage);
                int eventBtnW = (wellW - 24) / 2;
                if (PanelStyle.hit(mx, my, wellX + 8, eventsRowY, eventBtnW, PanelStyle.CONTROL_H)) {
                    Minecraft.getInstance().gui.setScreen(new TypedObjectListScreen(this, "ON ENTER",
                            stageArray(selectedStageObj, "on_enter"),
                            TypedObjectListScreen.Kind.ACTION, this::markDirtyFromChild));
                    return true;
                }
                if (PanelStyle.hit(mx, my, wellX + 16 + eventBtnW, eventsRowY, eventBtnW, PanelStyle.CONTROL_H)) {
                    Minecraft.getInstance().gui.setScreen(new TypedObjectListScreen(this, "ON EXIT",
                            stageArray(selectedStageObj, "on_exit"),
                            TypedObjectListScreen.Kind.ACTION, this::markDirtyFromChild));
                    return true;
                }
                return super.mouseClicked(event, doubleClick);
            }

            // ---- list view ----
            double min = JsonEdit.getDouble(entry.json, "value.min", 0);
            double max = JsonEdit.getDouble(entry.json, "value.max", 100);
            int tlX = wellX + 8, tlY = wellY + 30, tlW = wellW - 16, tlH = 26;

            if (PanelStyle.hit(mx, my, wellX + wellW - 100, wellY, 92, PanelStyle.CONTROL_H)) {
                var stage = new com.google.gson.JsonObject();
                double from = min;
                if (!arr.isEmpty()) {
                    from = JsonEdit.getDouble(stage(arr.size() - 1), "max", min) + 1;
                }
                stage.addProperty("id", "stage_" + (arr.size() + 1));
                stage.addProperty("min", Math.min(from, max));
                stage.addProperty("max", max);
                arr.add(stage);
                selectedStage = arr.size() - 1;
                entry.dirty = true;
                setPage(Page.STAGES);
                return true;
            }

            for (int i = 0; i < arr.size() - 1; i++) {
                double sMax = JsonEdit.getDouble(stage(i), "max", 0);
                int x2 = tlX + 2 + (int) ((sMax + 1 - min) / (max - min) * (tlW - 4));
                if (PanelStyle.hit(mx, my, x2 - 4, tlY, 8, tlH)) {
                    draggingThreshold = i;
                    return true;
                }
            }

            int listTop = wellY + 76;
            int listBottom = wellY + wellH - 4;
            int rows = (listBottom - listTop) / 18;
            for (int r = 0; r < rows; r++) {
                int i = stageScroll + r;
                if (i >= arr.size()) break;
                if (PanelStyle.hit(mx, my, wellX + 8, listTop + r * 18, wellW - 16, 16)) {
                    selectedStage = i;
                    setPage(Page.STAGES);
                    return true;
                }
            }
        }

        if (page == Page.RULES) {
            var rules = rulesArray();

            if (PanelStyle.hit(mx, my, wellX + wellW - 92, wellY, 84, PanelStyle.CONTROL_H)) {
                var rule = new com.google.gson.JsonObject();
                var trigger = new com.google.gson.JsonObject();
                trigger.addProperty("type", "myrpg_core:interval");
                trigger.addProperty("ticks", 200);
                rule.add("trigger", trigger);
                rules.add(rule);
                entry.dirty = true;
                Minecraft.getInstance().gui.setScreen(new RuleEditScreen(this, rule));
                return true;
            }

            int frameY = wellY + 30;
            int frameH = wellH - 34;
            int rowH = 52;
            int visibleRows = (frameH - 4) / rowH;
            for (int r = 0; r < visibleRows; r++) {
                int i = ruleScroll + r;
                if (i >= rules.size()) break;
                int ry = frameY + 2 + r * rowH;
                int rx = wellX + 10;
                int rw = wellW - 28;
                if (PanelStyle.hit(mx, my, rx + rw - 18, ry + 4, 12, 12)) {
                    rules.remove(i);
                    entry.dirty = true;
                    return true;
                }
                if (PanelStyle.hit(mx, my, rx, ry, rw, rowH - 4)) {
                    Minecraft.getInstance().gui.setScreen(
                            new RuleEditScreen(this, rules.get(i).getAsJsonObject()));
                    return true;
                }
            }
        }

        if (page == Page.ADVANCED) {
            int iy = wellY + 60;
            for (int i = 0; i < issues.size() && iy < wellY + 108; i++) {
                if (PanelStyle.hit(mx, my, wellX + 8, iy, wellW - 16, 12)) {
                    try {
                        setPage(Page.valueOf(issues.get(i).page()));
                    } catch (IllegalArgumentException ignored) { }
                    return true;
                }
                iy += 13;
            }
            if (PanelStyle.hit(mx, my, wellX + wellW - 60, iy + 2, 52, PanelStyle.CONTROL_H)) {
                Minecraft.getInstance().keyboardHandler.setClipboard(PRETTY.toJson(entry.json));
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingPreview && page == Page.DISPLAY) {
            updatePreview(event.x(), wellX + 8, wellW - 16);
            return true;
        }
        if (draggingThreshold >= 0 && page == Page.STAGES) {
            double min = JsonEdit.getDouble(entry.json, "value.min", 0);
            double max = JsonEdit.getDouble(entry.json, "value.max", 100);
            int tlX = wellX + 8, tlW = wellW - 16;
            boolean decimal = JsonEdit.getBool(entry.json, "value.decimal", false);
            double t = min + Math.max(0, Math.min(1, (event.x() - tlX) / (double) tlW)) * (max - min);
            if (!decimal) t = Math.rint(t);
            JsonEdit.set(stage(draggingThreshold), "max", decimal ? t : t - 1);
            JsonEdit.set(stage(draggingThreshold + 1), "min", t);
            entry.dirty = true;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingPreview = false;
        if (draggingThreshold >= 0) {
            draggingThreshold = -1;
            setPage(Page.STAGES);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (page == Page.STAGES && selectedStage >= 0) {
            int frameY = wellY + 56;
            int frameH = (wellY + wellH - 26) - frameY - 8;
            int visibleRows = (frameH - 4) / 18;
            var effects = stageArray(stage(selectedStage), "effects");
            int max = Math.max(0, effects.size() - visibleRows);
            effectScroll = Math.max(0, Math.min(max, effectScroll - (int) Math.signum(vertical)));
            return true;
        }
        if (page == Page.STAGES) {
            int visibleRows = (wellH - 80) / 18;
            int max = Math.max(0, stages().size() - visibleRows);
            stageScroll = Math.max(0, Math.min(max, stageScroll - (int) Math.signum(vertical)));
            return true;
        }
        if (page == Page.RULES) {
            int frameH = wellH - 34;
            int visibleRows = (frameH - 4) / 52;
            int max = Math.max(0, rulesArray().size() - visibleRows);
            ruleScroll = Math.max(0, Math.min(max, ruleScroll - (int) Math.signum(vertical)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    // ------------------------------------------------------------ helpers

    /** Maps a mouse x on the preview slider back to a value in the stat's range. */
    private void updatePreview(double mx, int sliderX, int sliderW) {
        double min = JsonEdit.getDouble(entry.json, "value.min", 0);
        double max = JsonEdit.getDouble(entry.json, "value.max", 100);
        double frac = Math.max(0, Math.min(1, (mx - sliderX) / sliderW));
        previewValue = min + frac * (max - min);
    }

    /** Flips a boolean at a dotted path and marks the entry dirty. */
    private void toggleBool(String path, boolean fallback) {
        JsonEdit.set(entry.json, path, !JsonEdit.getBool(entry.json, path, fallback));
        entry.dirty = true;
    }

    /** Advances a string at a dotted path to the next allowed value, wrapping around. */
    private void cycleString(String path, String[] values) {
        String current = JsonEdit.getString(entry.json, path, values[0]);
        int idx = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) idx = i;
        JsonEdit.set(entry.json, path, values[(idx + 1) % values.length]);
        entry.dirty = true;
    }

    /**
     * Validates and, if clean, sends the definition to the server.
     * Errors abort the save and jump to the ADVANCED page, where the issue list is shown;
     * warnings do not block. On success the pristine snapshot is refreshed so the entry
     * stops counting as dirty.
     */
    private void save() {
        issues = StatValidator.validate(entry.statId, entry.json);
        boolean hasErrors = issues.stream().anyMatch(i -> i.level() == StatValidator.Level.ERROR);
        if (hasErrors) {
            setPage(Page.ADVANCED);
            return;
        }
        ClientEditorNet.sendSave(entry.statId, GSON.toJson(entry.json));
        entry.dirty = false;
        entry.pristine = entry.json.deepCopy();
    }

    /** Parses "#RRGGBB"; falls back to white on anything malformed. */
    private static int parseColor(String hex) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    /** Called by child screens (rule, effect, typed-object editors) after they change the JSON. */
    void markDirtyFromChild() { entry.dirty = true; }

    /** Editing must not pause a singleplayer world. */
    @Override
    public boolean isPauseScreen() { return false; }
}