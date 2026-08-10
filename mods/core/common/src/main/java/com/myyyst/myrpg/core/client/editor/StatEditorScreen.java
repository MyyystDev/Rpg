package com.myyyst.myrpg.core.client.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
 * inside the well; every page fits without scrolling.
 */
public class StatEditorScreen extends Screen {

    private static final Gson GSON = new Gson();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    enum Page { GENERAL, VALUE, STAGES, RULES, DISPLAY, ADVANCED }

    private final StatLibraryScreen parent;
    private final StatWorkingSet workingSet;
    private final StatWorkingSet.Entry entry;
    private Page page = Page.GENERAL;

    // layout
    private int px, py, pw, ph, navW;
    private int frameTop, frameBottom;
    private int wellFrameX1, wellFrameX2;
    private int wellX, wellY, wellW, wellH;

    private final List<EditBox> pageBoxes = new ArrayList<>();

    private double previewValue = 60;
    private boolean draggingPreview;

    private int selectedStage = -1;
    private int draggingThreshold = -1;
    private int stageScroll;

    private List<StatValidator.Issue> issues = new ArrayList<>();

    private boolean confirmClose;

    public StatEditorScreen(StatLibraryScreen parent, StatWorkingSet workingSet, StatWorkingSet.Entry entry) {
        super(Component.literal("Stat Editor"));
        this.parent = parent;
        this.workingSet = workingSet;
        this.entry = entry;
        entry.pristine = entry.json == null ? null : entry.json.deepCopy();
    }

    // ------------------------------------------------------------ layout

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

    private void setPage(Page newPage) {
        page = newPage;
        clearWidgets();
        buildPageWidgets();
    }

    private void buildPageWidgets() {
        pageBoxes.clear();
        int half = wellW / 2;
        switch (page) {
            case GENERAL -> {
                addFieldAt("display.name", "", wellX + 8, wellY + 26, half - 16);
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
                    addStageFieldAt(stage, "display.name", wellX + 8, wellY + 28, half - 16);
                    addStageFieldAt(stage, "id", wellX + half + 8, wellY + 28, half - 16);
                    addStageFieldAt(stage, "min", wellX + 8, wellY + 76, 56);
                    addStageFieldAt(stage, "max", wellX + 88, wellY + 76, 56);
                }
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------ json helpers

    private com.google.gson.JsonArray stages() {
        if (!entry.json.has("stages")) entry.json.add("stages", new com.google.gson.JsonArray());
        return entry.json.getAsJsonArray("stages");
    }

    private com.google.gson.JsonObject stage(int index) {
        return stages().get(index).getAsJsonObject();
    }

    private com.google.gson.JsonArray stageArray(com.google.gson.JsonObject stage, String key) {
        if (!stage.has(key)) stage.add(key, new com.google.gson.JsonArray());
        return stage.getAsJsonArray(key);
    }

    private com.google.gson.JsonArray rulesArray() {
        if (!entry.json.has("rules")) entry.json.add("rules", new com.google.gson.JsonArray());
        return entry.json.getAsJsonArray("rules");
    }

    // ------------------------------------------------------------ widget builders

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

    private void addNumberField(String path, double fallback, int y, int w) {
        addNumberField(path, fallback, y, w, 0);
    }

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

    private static String trimNum(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    // ------------------------------------------------------------ render

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

    private void renderStages(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (selectedStage >= 0 && selectedStage < stages().size()) {
            renderStageDetail(g, mouseX, mouseY);
        } else {
            renderStageList(g, mouseX, mouseY);
        }
    }

    private void renderStageList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("STAGES"), wellX + 8, wellY + 4, PanelStyle.TEXT);
        PanelStyle.button(g, font, "+ ADD STAGE", wellX + wellW - 100, wellY, 92,
                PanelStyle.hit(mouseX, mouseY, wellX + wellW - 100, wellY, 92, PanelStyle.CONTROL_H), true);

        double min = JsonEdit.getDouble(entry.json, "value.min", 0);
        double max = JsonEdit.getDouble(entry.json, "value.max", 100);
        int tlX = wellX + 8, tlY = wellY + 30, tlW = wellW - 16, tlH = 26;

        PanelStyle.inset(g, tlX, tlY, tlW, tlH);
        var arr = stages();
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
            if (i < arr.size() - 1) {
                boolean hot = draggingThreshold == i
                        || PanelStyle.hit(mouseX, mouseY, x2 - 3, tlY, 6, tlH);
                g.fill(x2 - 1, tlY, x2 + 1, tlY + tlH, hot ? PanelStyle.ACCENT : PanelStyle.PANEL_LIGHT);
            }
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

    private void renderStageDetail(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        var stage = stage(selectedStage);
        String stageId = JsonEdit.getString(stage, "id", "?").toUpperCase();
        int half = wellW / 2;

        boolean crumbHover = PanelStyle.hit(mouseX, mouseY, wellX + 8, wellY + 2, font.width("< STAGES"), 12);
        g.text(font, Component.literal("< STAGES"), wellX + 8, wellY + 4,
                crumbHover ? PanelStyle.TEXT : PanelStyle.ACCENT);
        g.text(font, Component.literal(" / " + stageId), wellX + 8 + font.width("< STAGES"), wellY + 4, PanelStyle.TEXT);
        PanelStyle.button(g, font, "DELETE", wellX + wellW - 68, wellY, 60,
                PanelStyle.hit(mouseX, mouseY, wellX + wellW - 68, wellY, 60, PanelStyle.CONTROL_H), false);

        g.text(font, Component.literal("NAME"), wellX + 8, wellY + 18, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("STAGE ID"), wellX + half + 8, wellY + 18, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("FROM"), wellX + 8, wellY + 66, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("TO"), wellX + 88, wellY + 66, PanelStyle.TEXT_DIM);

        int effectsY = wellY + 106;
        g.text(font, Component.literal("EFFECTS"), wellX + 8, effectsY, PanelStyle.TEXT);
        PanelStyle.button(g, font, "+ ADD EFFECT", wellX + wellW - 100, effectsY - 4, 92,
                PanelStyle.hit(mouseX, mouseY, wellX + wellW - 100, effectsY - 4, 92, PanelStyle.CONTROL_H), true);
        var effects = stageArray(stage, "effects");
        for (int i = 0; i < effects.size(); i++) {
            var effect = effects.get(i).getAsJsonObject();
            int eRowY = effectsY + 16 + i * 16;
            String type = effect.has("type") ? effect.get("type").getAsString() : "?";
            var schema = EffectSchemas.all().get(type);
            g.text(font, Component.literal(schema != null ? schema.label() : type), wellX + 12, eRowY, PanelStyle.TEXT);
            g.text(font, Component.literal("X"), wellX + wellW - 20, eRowY,
                    PanelStyle.hit(mouseX, mouseY, wellX + wellW - 24, eRowY - 2, 12, 12) ? PanelStyle.ERROR : PanelStyle.TEXT_DIM);
        }

        int eventsY = effectsY + 24 + effects.size() * 16;
        g.text(font, Component.literal("EVENTS"), wellX + 8, eventsY, PanelStyle.TEXT);
        PanelStyle.button(g, font, "ON ENTER (" + stageArray(stage, "on_enter").size() + ")",
                wellX + 8, eventsY + 10, 130,
                PanelStyle.hit(mouseX, mouseY, wellX + 8, eventsY + 10, 130, PanelStyle.CONTROL_H), false);
        PanelStyle.button(g, font, "ON EXIT (" + stageArray(stage, "on_exit").size() + ")",
                wellX + 146, eventsY + 10, 130,
                PanelStyle.hit(mouseX, mouseY, wellX + 146, eventsY + 10, 130, PanelStyle.CONTROL_H), false);
    }

    private void renderRules(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("RULES"), wellX + 8, wellY + 4, PanelStyle.TEXT);
        g.text(font, Component.literal("Automatic ways this stat changes."), wellX + 8, wellY + 16, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, "+ NEW RULE", wellX + wellW - 92, wellY, 84,
                PanelStyle.hit(mouseX, mouseY, wellX + wellW - 92, wellY, 84, PanelStyle.CONTROL_H), true);

        var rules = rulesArray();
        int ry = wellY + 34;
        for (int i = 0; i < rules.size() && ry < wellY + wellH - 24; i++) {
            var rule = rules.get(i).getAsJsonObject();
            int rowH = 52;
            boolean hovered = PanelStyle.hit(mouseX, mouseY, wellX + 8, ry, wellW - 16, rowH - 4);
            g.fill(wellX + 8, ry, wellX + wellW - 8, ry + rowH - 4, hovered ? PanelStyle.ROW_HOVER : PanelStyle.ROW_BG);

            g.text(font, Component.literal(String.format("%02d", i + 1)), wellX + 14, ry + 6, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("TRIGGER  " + summarizeTrigger(rule)), wellX + 36, ry + 6, PanelStyle.TEXT);
            g.text(font, Component.literal("IF  " + summarizeList(rule, "conditions", "always")), wellX + 36, ry + 18, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("DO  " + summarizeList(rule, "actions", "nothing")), wellX + 36, ry + 30, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("X"), wellX + wellW - 22, ry + 6,
                    PanelStyle.hit(mouseX, mouseY, wellX + wellW - 26, ry + 4, 12, 12) ? PanelStyle.ERROR : PanelStyle.TEXT_DIM);
            ry += rowH;
        }
    }

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

    private String summarizeList(com.google.gson.JsonObject rule, String key, String emptyWord) {
        if (!rule.has(key) || rule.getAsJsonArray(key).isEmpty()) return emptyWord;
        var array = rule.getAsJsonArray(key);
        var first = array.get(0).getAsJsonObject();
        String type = first.has("type") ? first.get("type").getAsString() : "?";
        String label = type.substring(type.indexOf(':') + 1).replace('_', ' ');
        return array.size() == 1 ? label : label + " +" + (array.size() - 1) + " more";
    }

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

        g.text(font, Component.literal("LIVE PREVIEW"), wellX + 8, wellY + 78, PanelStyle.TEXT_DIM);
        PanelStyle.inset(g, wellX + 8, wellY + 88, wellW - 16, 40);
        double min = JsonEdit.getDouble(entry.json, "value.min", 0);
        double max = JsonEdit.getDouble(entry.json, "value.max", 100);
        int color = parseColor(JsonEdit.getString(entry.json, "display.color", "#FFFFFF"));
        g.text(font, Component.literal(entry.displayName()), wellX + 16, wellY + 94, PanelStyle.TEXT);
        int barX = wellX + 16, barY = wellY + 108, barW = wellW - 120;
        g.fill(barX, barY, barX + barW, barY + 8, 0xFF101012);
        double frac = Math.max(0, Math.min(1, (previewValue - min) / Math.max(1e-9, max - min)));
        g.fill(barX, barY, barX + (int) (barW * frac), barY + 8, 0xFF000000 | color);
        if (showValue) {
            g.text(font, Component.literal((long) previewValue + " / " + (long) max),
                    barX + barW + 8, barY, PanelStyle.TEXT_DIM);
        }

        g.text(font, Component.literal("PREVIEW VALUE"), wellX + 8, wellY + 136, PanelStyle.TEXT_DIM);
        int sliderX = wellX + 8, sliderY = wellY + 148, sliderW = wellW - 16;
        g.fill(sliderX, sliderY + 3, sliderX + sliderW, sliderY + 5, PanelStyle.PANEL_DARK);
        int knobX = sliderX + (int) (sliderW * frac);
        g.fill(knobX - 2, sliderY - 2, knobX + 3, sliderY + 10, PanelStyle.ACCENT);
    }

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

        g.text(font, Component.literal("JSON PREVIEW"), wellX + 8, iy + 4, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, "COPY", wellX + wellW - 60, iy, 52,
                PanelStyle.hit(mouseX, mouseY, wellX + wellW - 60, iy, 52, PanelStyle.CONTROL_H), false);
        int jsonY = iy + 16;
        PanelStyle.inset(g, wellX + 8, jsonY, wellW - 16, wellY + wellH - jsonY - 4);
        String[] lines = PRETTY.toJson(entry.json).split("\n");
        int ly = jsonY + 4;
        for (int i = 0; i < lines.length && ly < wellY + wellH - 12; i++) {
            String line = lines[i].length() > 52 ? lines[i].substring(0, 52) + "..." : lines[i];
            g.text(font, Component.literal(line), wellX + 12, ly, PanelStyle.TEXT_DIM);
            ly += 9;
        }
    }

    private void renderToggle(GuiGraphicsExtractor g, int mouseX, int mouseY, String label, String value, int x, int y) {
        g.text(font, Component.literal(label), x, y, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, value, x, y + 10, 140,
                PanelStyle.hit(mouseX, mouseY, x, y + 10, 140, PanelStyle.CONTROL_H), false);
    }

    private void renderCheckbox(GuiGraphicsExtractor g, int mouseX, int mouseY, String label, boolean checked, int x, int y) {
        PanelStyle.inset(g, x, y, 14, 14);
        if (checked) g.fill(x + 3, y + 3, x + 11, y + 11, PanelStyle.ACCENT);
        g.text(font, Component.literal(label), x + 20, y + 3, PanelStyle.TEXT);
    }

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
                return true;
            }
            if (PanelStyle.hit(mx, my, wellX + 168, wellY + 48, 140, PanelStyle.CONTROL_H)) {
                cycleString("hud.visibility", new String[]{"always", "never", "when_non_default", "above_value", "below_value"});
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
                if (PanelStyle.hit(mx, my, wellX + wellW - 68, wellY, 60, PanelStyle.CONTROL_H)) {
                    arr.remove(selectedStage);
                    selectedStage = -1;
                    entry.dirty = true;
                    setPage(Page.STAGES);
                    return true;
                }
                int effectsY = wellY + 106;
                if (PanelStyle.hit(mx, my, wellX + wellW - 100, effectsY - 4, 92, PanelStyle.CONTROL_H)) {
                    Minecraft.getInstance().gui.setScreen(new EffectPickerScreen(this, stage(selectedStage)));
                    return true;
                }
                var effects = stageArray(stage(selectedStage), "effects");
                for (int i = 0; i < effects.size(); i++) {
                    if (PanelStyle.hit(mx, my, wellX + wellW - 24, effectsY + 14 + i * 16, 12, 12)) {
                        effects.remove(i);
                        entry.dirty = true;
                        return true;
                    }
                }
                var selectedStageObj = stage(selectedStage);
                int eventsY = effectsY + 24 + effects.size() * 16;
                if (PanelStyle.hit(mx, my, wellX + 8, eventsY + 10, 130, PanelStyle.CONTROL_H)) {
                    Minecraft.getInstance().gui.setScreen(new TypedObjectListScreen(this, "ON ENTER",
                            stageArray(selectedStageObj, "on_enter"),
                            TypedObjectListScreen.Kind.ACTION, this::markDirtyFromChild));
                    return true;
                }
                if (PanelStyle.hit(mx, my, wellX + 146, eventsY + 10, 130, PanelStyle.CONTROL_H)) {
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

            int ruleRowY = wellY + 34;
            for (int i = 0; i < rules.size() && ruleRowY < wellY + wellH - 24; i++) {
                int rowH = 52;
                if (PanelStyle.hit(mx, my, wellX + wellW - 26, ruleRowY + 4, 12, 12)) {
                    rules.remove(i);
                    entry.dirty = true;
                    return true;
                }
                if (PanelStyle.hit(mx, my, wellX + 8, ruleRowY, wellW - 16, rowH - 4)) {
                    Minecraft.getInstance().gui.setScreen(
                            new RuleEditScreen(this, rules.get(i).getAsJsonObject()));
                    return true;
                }
                ruleRowY += rowH;
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
            if (PanelStyle.hit(mx, my, wellX + wellW - 60, iy, 52, PanelStyle.CONTROL_H)) {
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
        if (page == Page.STAGES && selectedStage < 0) {
            int visibleRows = (wellH - 80) / 18;
            int max = Math.max(0, stages().size() - visibleRows);
            stageScroll = Math.max(0, Math.min(max, stageScroll - (int) Math.signum(vertical)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    // ------------------------------------------------------------ helpers

    private void updatePreview(double mx, int sliderX, int sliderW) {
        double min = JsonEdit.getDouble(entry.json, "value.min", 0);
        double max = JsonEdit.getDouble(entry.json, "value.max", 100);
        double frac = Math.max(0, Math.min(1, (mx - sliderX) / sliderW));
        previewValue = min + frac * (max - min);
    }

    private void toggleBool(String path, boolean fallback) {
        JsonEdit.set(entry.json, path, !JsonEdit.getBool(entry.json, path, fallback));
        entry.dirty = true;
    }

    private void cycleString(String path, String[] values) {
        String current = JsonEdit.getString(entry.json, path, values[0]);
        int idx = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) idx = i;
        JsonEdit.set(entry.json, path, values[(idx + 1) % values.length]);
        entry.dirty = true;
    }

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

    private static int parseColor(String hex) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    void markDirtyFromChild() { entry.dirty = true; }

    @Override
    public boolean isPauseScreen() { return false; }
}