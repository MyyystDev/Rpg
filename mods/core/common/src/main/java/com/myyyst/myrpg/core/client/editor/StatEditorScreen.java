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
 * The object editor: header with save state, left nav, page content.
 * All six pages live. Errors block save (bounce to ADVANCED); warnings
 * don't. DISCARD reverts to the pristine snapshot taken on open.
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
    private int px, py, pw, ph, navW, cx, cw;

    // page widgets (rebuilt on page switch)
    private final List<EditBox> pageBoxes = new ArrayList<>();

    // display-page preview state
    private double previewValue = 60;
    private boolean draggingPreview;

    // stages page state
    private int selectedStage = -1;
    private int draggingThreshold = -1;

    // validation
    private List<StatValidator.Issue> issues = new ArrayList<>();

    // unsaved-close dialog
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
        cx = px + navW + PanelStyle.GRID;
        cw = pw - navW - PanelStyle.GRID * 3;
        buildPageWidgets();
    }

    private void setPage(Page newPage) {
        page = newPage;
        clearWidgets();
        buildPageWidgets();
    }

    private void buildPageWidgets() {
        pageBoxes.clear();
        int fy = py + 56;
        switch (page) {
            case GENERAL -> {
                addField("display.name", "", fy, cw / 2 - 8);
                addField("__id", entry.statId, fy + 40, cw / 2 - 8);
                addField("display.description", "", fy + 80, cw - 16);
                addField("display.icon", "", fy + 120, cw - 16);
                addField("display.color", "#FFFFFF", fy + 160, 100);
            }
            case VALUE -> {
                addNumberField("value.default", 0, fy + 20, 64);
                addNumberField("value.min", 0, fy + 20, 64, 80);
                addNumberField("value.max", 100, fy + 20, 64, 160);
            }
            case STAGES -> {
                if (selectedStage >= 0 && selectedStage < stages().size()) {
                    var stage = stage(selectedStage);
                    int panelY = py + 150;
                    addStageField(stage, "display.name", panelY, 110, false);
                    addStageField(stage, "id", panelY, 90, true);
                    addStageField(stage, "min", panelY + 36, 48, false);
                    addStageField(stage, "max", panelY + 36, 48, true);
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

    private void addField(String path, String fallback, int y, int w) {
        EditBox box = new EditBox(font, cx + 8, y, w, 18, Component.empty());
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
        EditBox box = new EditBox(font, cx + 8 + xOffset, y, w, 18, Component.empty());
        double value = JsonEdit.getDouble(entry.json, path, fallback);
        box.setValue(trimNum(value));
        box.setResponder(text -> {
            try {
                JsonEdit.set(entry.json, path, Double.parseDouble(text.trim()));
                entry.dirty = true;
            } catch (NumberFormatException ignored) { }
        });
        addRenderableWidget(box);
        pageBoxes.add(box);
    }

    private void addStageField(com.google.gson.JsonObject stage, String path, int y, int w, boolean second) {
        int x = cx + 8 + (second ? 130 : 0);
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
        renderNav(g, mouseX, mouseY);

        switch (page) {
            case GENERAL -> renderGeneral(g);
            case VALUE -> renderValue(g, mouseX, mouseY);
            case STAGES -> renderStages(g, mouseX, mouseY);
            case RULES -> renderRules(g, mouseX, mouseY);
            case DISPLAY -> renderDisplay(g, mouseX, mouseY);
            case ADVANCED -> renderAdvanced(g, mouseX, mouseY);
        }

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
    }

    private void renderNav(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int ny = py + 48;
        for (Page p : Page.values()) {
            boolean selected = p == page;
            boolean hovered = PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID, ny, navW - PanelStyle.GRID, 20);
            if (selected) {
                g.fill(px + PanelStyle.GRID, ny, px + navW, ny + 20, PanelStyle.ROW_SELECT);
            } else if (hovered) {
                g.fill(px + PanelStyle.GRID, ny, px + navW, ny + 20, PanelStyle.ROW_HOVER);
            }
            g.text(font, Component.literal(p.name()), px + PanelStyle.GRID * 2, ny + 6,
                    selected ? PanelStyle.TEXT : PanelStyle.TEXT_DIM);
            ny += 22;
        }
    }

    private void renderGeneral(GuiGraphicsExtractor g) {
        int fy = py + 56;
        g.text(font, Component.literal("GENERAL"), cx + 8, py + 44, PanelStyle.TEXT);
        g.text(font, Component.literal("DISPLAY NAME"), cx + 8, fy - 10, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("RESOURCE ID"), cx + 8, fy + 30, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("DESCRIPTION"), cx + 8, fy + 70, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("ICON (texture path)"), cx + 8, fy + 110, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("COLOR"), cx + 8, fy + 150, PanelStyle.TEXT_DIM);
        String hex = JsonEdit.getString(entry.json, "display.color", "#FFFFFF");
        int color = parseColor(hex);
        g.fill(cx + 116, py + 56 + 160, cx + 134, py + 56 + 178, 0xFF000000 | color);
    }

    private void renderValue(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int fy = py + 56;
        g.text(font, Component.literal("VALUE"), cx + 8, py + 44, PanelStyle.TEXT);
        g.text(font, Component.literal("DEFAULT"), cx + 8, fy + 10, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("MIN"), cx + 88, fy + 10, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("MAX"), cx + 168, fy + 10, PanelStyle.TEXT_DIM);

        boolean decimal = JsonEdit.getBool(entry.json, "value.decimal", false);
        renderToggle(g, mouseX, mouseY, "NUMBER TYPE", decimal ? "Decimal" : "Integer", cx + 8, fy + 60);
        boolean clamp = JsonEdit.getBool(entry.json, "value.clamp", true);
        renderCheckbox(g, mouseX, mouseY, "Clamp value to range", clamp, cx + 8, fy + 100);

        g.text(font, Component.literal("PERSISTENCE"), cx + 8, fy + 130, PanelStyle.TEXT);
        boolean keep = JsonEdit.getBool(entry.json, "persistence.keep_on_death", true);
        renderToggle(g, mouseX, mouseY, "ON DEATH", keep ? "Keep value" : "Reset", cx + 8, fy + 145);
        boolean respawnReset = JsonEdit.getBool(entry.json, "persistence.reset_on_respawn", false);
        renderToggle(g, mouseX, mouseY, "ON RESPAWN", respawnReset ? "Reset" : "Keep value", cx + 168, fy + 145);
    }

    private void renderStages(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("STAGES"), cx + 8, py + 44, PanelStyle.TEXT);
        PanelStyle.button(g, font, "+ ADD STAGE", cx + cw - 100, py + 40, 92,
                PanelStyle.hit(mouseX, mouseY, cx + cw - 100, py + 40, 92, PanelStyle.CONTROL_H), true);

        double min = JsonEdit.getDouble(entry.json, "value.min", 0);
        double max = JsonEdit.getDouble(entry.json, "value.max", 100);
        int tlX = cx + 8, tlY = py + 66, tlW = cw - 16, tlH = 26;

        PanelStyle.inset(g, tlX, tlY, tlW, tlH);
        var arr = stages();
        for (int i = 0; i < arr.size(); i++) {
            var stage = stage(i);
            double sMin = JsonEdit.getDouble(stage, "min", 0);
            double sMax = JsonEdit.getDouble(stage, "max", 0);
            int x1 = tlX + 2 + (int) ((sMin - min) / (max - min) * (tlW - 4));
            int x2 = tlX + 2 + (int) ((sMax + 1 - min) / (max - min) * (tlW - 4));
            x2 = Math.min(x2, tlX + tlW - 2);
            int color = i == selectedStage ? PanelStyle.ROW_SELECT
                    : (i % 2 == 0 ? 0xFF3A3A40 : 0xFF44444A);
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

        int stageRowY = py + 108;
        for (int i = 0; i < arr.size() && stageRowY < py + 146; i++) {
            var stage = stage(i);
            boolean hovered = PanelStyle.hit(mouseX, mouseY, cx + 8, stageRowY, cw - 16, 16);
            if (i == selectedStage) g.fill(cx + 8, stageRowY, cx + cw - 8, stageRowY + 16, PanelStyle.ROW_SELECT);
            else if (hovered) g.fill(cx + 8, stageRowY, cx + cw - 8, stageRowY + 16, PanelStyle.ROW_HOVER);
            g.text(font, Component.literal(JsonEdit.getString(stage, "id", "?").toUpperCase()), cx + 12, stageRowY + 4, PanelStyle.TEXT);
            String range = trimNum(JsonEdit.getDouble(stage, "min", 0)) + " - " + trimNum(JsonEdit.getDouble(stage, "max", 0));
            g.text(font, Component.literal(range), cx + cw - 16 - font.width(range), stageRowY + 4, PanelStyle.TEXT_DIM);
            stageRowY += 18;
        }

        if (selectedStage >= 0 && selectedStage < arr.size()) {
            var stage = stage(selectedStage);
            int panelY = py + 150;
            int effectsY = panelY + 64;
            g.text(font, Component.literal("NAME"), cx + 8, panelY - 10, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("STAGE ID"), cx + 138, panelY - 10, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("FROM"), cx + 8, panelY + 26, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("TO"), cx + 138, panelY + 26, PanelStyle.TEXT_DIM);
            PanelStyle.button(g, font, "DELETE STAGE", cx + cw - 100, panelY + 30, 92,
                    PanelStyle.hit(mouseX, mouseY, cx + cw - 100, panelY + 30, 92, PanelStyle.CONTROL_H), false);

            g.text(font, Component.literal("EFFECTS"), cx + 8, effectsY, PanelStyle.TEXT);
            PanelStyle.button(g, font, "+ ADD EFFECT", cx + cw - 100, effectsY - 4, 92,
                    PanelStyle.hit(mouseX, mouseY, cx + cw - 100, effectsY - 4, 92, PanelStyle.CONTROL_H), true);
            var effects = stageArray(stage, "effects");
            for (int i = 0; i < effects.size(); i++) {
                var effect = effects.get(i).getAsJsonObject();
                int eRowY = effectsY + 16 + i * 16;
                String type = effect.has("type") ? effect.get("type").getAsString() : "?";
                var schema = EffectSchemas.all().get(type);
                g.text(font, Component.literal(schema != null ? schema.label() : type), cx + 12, eRowY, PanelStyle.TEXT);
                g.text(font, Component.literal("X"), cx + cw - 20, eRowY,
                        PanelStyle.hit(mouseX, mouseY, cx + cw - 24, eRowY - 2, 12, 12) ? PanelStyle.ERROR : PanelStyle.TEXT_DIM);
            }
            int eventsY = effectsY + 20 + effects.size() * 16;
            g.text(font, Component.literal("EVENTS"), cx + 8, eventsY, PanelStyle.TEXT);
            PanelStyle.button(g, font, "ON ENTER (" + stageArray(stage, "on_enter").size() + ")",
                    cx + 8, eventsY + 10, 130,
                    PanelStyle.hit(mouseX, mouseY, cx + 8, eventsY + 10, 130, PanelStyle.CONTROL_H), false);
            PanelStyle.button(g, font, "ON EXIT (" + stageArray(stage, "on_exit").size() + ")",
                    cx + 146, eventsY + 10, 130,
                    PanelStyle.hit(mouseX, mouseY, cx + 146, eventsY + 10, 130, PanelStyle.CONTROL_H), false);
        }
    }

    private void renderRules(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("RULES"), cx + 8, py + 44, PanelStyle.TEXT);
        g.text(font, Component.literal("Automatic ways this stat changes."), cx + 8, py + 56, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, "+ NEW RULE", cx + cw - 92, py + 40, 84,
                PanelStyle.hit(mouseX, mouseY, cx + cw - 92, py + 40, 84, PanelStyle.CONTROL_H), true);

        var rules = rulesArray();
        int ry = py + 74;
        for (int i = 0; i < rules.size() && ry < py + ph - 40; i++) {
            var rule = rules.get(i).getAsJsonObject();
            int rowH = 52;
            boolean hovered = PanelStyle.hit(mouseX, mouseY, cx + 8, ry, cw - 16, rowH - 4);
            g.fill(cx + 8, ry, cx + cw - 8, ry + rowH - 4, hovered ? PanelStyle.ROW_HOVER : PanelStyle.ROW_BG);

            g.text(font, Component.literal(String.format("%02d", i + 1)), cx + 14, ry + 6, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("TRIGGER  " + summarizeTrigger(rule)), cx + 36, ry + 6, PanelStyle.TEXT);
            g.text(font, Component.literal("IF  " + summarizeList(rule, "conditions", "always")), cx + 36, ry + 18, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("DO  " + summarizeList(rule, "actions", "nothing")), cx + 36, ry + 30, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("X"), cx + cw - 22, ry + 6,
                    PanelStyle.hit(mouseX, mouseY, cx + cw - 26, ry + 4, 12, 12) ? PanelStyle.ERROR : PanelStyle.TEXT_DIM);
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
        int fy = py + 56;
        g.text(font, Component.literal("DISPLAY"), cx + 8, py + 44, PanelStyle.TEXT);
        boolean visible = JsonEdit.getBool(entry.json, "hud.visible", false);
        renderCheckbox(g, mouseX, mouseY, "Show on HUD", visible, cx + 8, fy);
        boolean showValue = JsonEdit.getBool(entry.json, "hud.show_value", false);
        renderCheckbox(g, mouseX, mouseY, "Show numeric value", showValue, cx + 168, fy);

        String type = JsonEdit.getString(entry.json, "hud.type", "bar");
        renderToggle(g, mouseX, mouseY, "DISPLAY TYPE", type, cx + 8, fy + 30);
        String vis = JsonEdit.getString(entry.json, "hud.visibility", "always");
        renderToggle(g, mouseX, mouseY, "VISIBILITY", vis, cx + 168, fy + 30);

        g.text(font, Component.literal("LIVE PREVIEW"), cx + 8, fy + 70, PanelStyle.TEXT_DIM);
        PanelStyle.inset(g, cx + 8, fy + 82, cw - 16, 48);
        double min = JsonEdit.getDouble(entry.json, "value.min", 0);
        double max = JsonEdit.getDouble(entry.json, "value.max", 100);
        String name = entry.displayName();
        int color = parseColor(JsonEdit.getString(entry.json, "display.color", "#FFFFFF"));
        g.text(font, Component.literal(name), cx + 16, fy + 90, PanelStyle.TEXT);
        int barX = cx + 16, barY = fy + 104, barW = cw - 120;
        g.fill(barX, barY, barX + barW, barY + 8, 0xFF101012);
        double frac = (previewValue - min) / Math.max(1e-9, max - min);
        frac = Math.max(0, Math.min(1, frac));
        g.fill(barX, barY, barX + (int) (barW * frac), barY + 8, 0xFF000000 | color);
        if (showValue) {
            g.text(font, Component.literal((long) previewValue + " / " + (long) max),
                    barX + barW + 8, barY, PanelStyle.TEXT_DIM);
        }

        g.text(font, Component.literal("PREVIEW VALUE"), cx + 8, fy + 140, PanelStyle.TEXT_DIM);
        int sliderX = cx + 8, sliderY = fy + 154, sliderW = cw - 16;
        g.fill(sliderX, sliderY + 3, sliderX + sliderW, sliderY + 5, PanelStyle.PANEL_DARK);
        int knobX = sliderX + (int) (sliderW * frac);
        g.fill(knobX - 2, sliderY - 2, knobX + 3, sliderY + 10, PanelStyle.ACCENT);
    }

    private void renderAdvanced(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("ADVANCED"), cx + 8, py + 44, PanelStyle.TEXT);

        g.text(font, Component.literal("FILE"), cx + 8, py + 58, PanelStyle.TEXT_DIM);
        String ns = entry.statId.contains(":") ? entry.statId.split(":", 2)[0] : "?";
        String path = entry.statId.contains(":") ? entry.statId.split(":", 2)[1] : entry.statId;
        g.text(font, Component.literal("datapacks/myrpg_editor/data/" + ns + "/myrpg/stats/" + path + ".json"),
                cx + 8, py + 68, PanelStyle.TEXT);

        issues = StatValidator.validate(entry.statId, entry.json);
        long errors = issues.stream().filter(i -> i.level() == StatValidator.Level.ERROR).count();
        g.text(font, Component.literal("VALIDATION"), cx + 8, py + 86, PanelStyle.TEXT_DIM);
        if (issues.isEmpty()) {
            PanelStyle.chip(g, font, "NO ISSUES", cx + 70, py + 84, PanelStyle.VALID);
        } else {
            PanelStyle.chip(g, font, errors + " ERROR", cx + 70, py + 84, errors > 0 ? PanelStyle.ERROR : PanelStyle.VALID);
            PanelStyle.chip(g, font, (issues.size() - errors) + " WARN", cx + 130, py + 84, PanelStyle.EDITED);
        }
        int iy = py + 100;
        for (int i = 0; i < issues.size() && iy < py + 160; i++) {
            var issue = issues.get(i);
            int color = issue.level() == StatValidator.Level.ERROR ? PanelStyle.ERROR : PanelStyle.EDITED;
            boolean hovered = PanelStyle.hit(mouseX, mouseY, cx + 8, iy, cw - 16, 12);
            g.text(font, Component.literal((hovered ? "> " : "  ") + issue.page() + "  " + issue.message()),
                    cx + 8, iy, hovered ? PanelStyle.TEXT : color);
            iy += 13;
        }

        g.text(font, Component.literal("JSON PREVIEW"), cx + 8, iy + 4, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, "COPY", cx + cw - 60, iy, 52,
                PanelStyle.hit(mouseX, mouseY, cx + cw - 60, iy, 52, PanelStyle.CONTROL_H), false);
        int wellY = iy + 16;
        PanelStyle.inset(g, cx + 8, wellY, cw - 16, py + ph - wellY - PanelStyle.GRID);
        String[] lines = PRETTY.toJson(entry.json).split("\n");
        int ly = wellY + 4;
        for (int i = 0; i < lines.length && ly < py + ph - PanelStyle.GRID - 10; i++) {
            String line = lines[i].length() > 52 ? lines[i].substring(0, 52) + "..." : lines[i];
            g.text(font, Component.literal(line), cx + 12, ly, PanelStyle.TEXT_DIM);
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
                if (!entry.dirty) {   // save succeeded (no errors)
                    confirmClose = false;
                    Minecraft.getInstance().gui.setScreen(parent);
                } else {
                    confirmClose = false;   // save bounced to ADVANCED with errors
                }
            } else {
                confirmClose = false;
            }
            return true;
        }

        // header: back
        if (PanelStyle.hit(mx, my, px + PanelStyle.GRID, py + PanelStyle.GRID, 20, PanelStyle.CONTROL_H)) {
            if (entry.dirty) confirmClose = true;
            else Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        // header: save
        if (PanelStyle.hit(mx, my, px + pw - 64 - PanelStyle.GRID, py + PanelStyle.GRID, 64, PanelStyle.CONTROL_H)) {
            save();
            return true;
        }

        // nav
        int ny = py + 48;
        for (Page p : Page.values()) {
            if (PanelStyle.hit(mx, my, px + PanelStyle.GRID, ny, navW - PanelStyle.GRID, 20)) {
                setPage(p);
                return true;
            }
            ny += 22;
        }

        int fy = py + 56;

        if (page == Page.VALUE) {
            if (PanelStyle.hit(mx, my, cx + 8, fy + 70, 140, PanelStyle.CONTROL_H)) {
                toggleBool("value.decimal", false);
                return true;
            }
            if (PanelStyle.hit(mx, my, cx + 8, fy + 100, 14, 14)) {
                toggleBool("value.clamp", true);
                return true;
            }
            if (PanelStyle.hit(mx, my, cx + 8, fy + 155, 140, PanelStyle.CONTROL_H)) {
                toggleBool("persistence.keep_on_death", true);
                return true;
            }
            if (PanelStyle.hit(mx, my, cx + 168, fy + 155, 140, PanelStyle.CONTROL_H)) {
                toggleBool("persistence.reset_on_respawn", false);
                return true;
            }
        }

        if (page == Page.DISPLAY) {
            if (PanelStyle.hit(mx, my, cx + 8, fy, 14, 14)) {
                toggleBool("hud.visible", false);
                return true;
            }
            if (PanelStyle.hit(mx, my, cx + 168, fy, 14, 14)) {
                toggleBool("hud.show_value", false);
                return true;
            }
            if (PanelStyle.hit(mx, my, cx + 8, fy + 40, 140, PanelStyle.CONTROL_H)) {
                cycleString("hud.type", new String[]{"bar", "number", "percentage", "icons", "hidden"});
                return true;
            }
            if (PanelStyle.hit(mx, my, cx + 168, fy + 40, 140, PanelStyle.CONTROL_H)) {
                cycleString("hud.visibility", new String[]{"always", "never", "when_non_default", "above_value", "below_value"});
                return true;
            }
            int sliderX = cx + 8, sliderY = fy + 154, sliderW = cw - 16;
            if (PanelStyle.hit(mx, my, sliderX, sliderY - 4, sliderW, 16)) {
                draggingPreview = true;
                updatePreview(mx, sliderX, sliderW);
                return true;
            }
        }

        if (page == Page.STAGES) {
            double min = JsonEdit.getDouble(entry.json, "value.min", 0);
            double max = JsonEdit.getDouble(entry.json, "value.max", 100);
            int tlX = cx + 8, tlY = py + 66, tlW = cw - 16, tlH = 26;
            var arr = stages();

            if (PanelStyle.hit(mx, my, cx + cw - 100, py + 40, 92, PanelStyle.CONTROL_H)) {
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

            int stageRowY = py + 108;
            for (int i = 0; i < arr.size() && stageRowY < py + 146; i++) {
                if (PanelStyle.hit(mx, my, cx + 8, stageRowY, cw - 16, 16)) {
                    selectedStage = i;
                    setPage(Page.STAGES);
                    return true;
                }
                stageRowY += 18;
            }

            if (selectedStage >= 0 && selectedStage < arr.size()) {
                int panelY = py + 150;
                int effectsY = panelY + 64;

                if (PanelStyle.hit(mx, my, cx + cw - 100, panelY + 30, 92, PanelStyle.CONTROL_H)) {
                    arr.remove(selectedStage);
                    selectedStage = -1;
                    entry.dirty = true;
                    setPage(Page.STAGES);
                    return true;
                }
                if (PanelStyle.hit(mx, my, cx + cw - 100, effectsY - 4, 92, PanelStyle.CONTROL_H)) {
                    Minecraft.getInstance().gui.setScreen(new EffectPickerScreen(this, stage(selectedStage)));
                    return true;
                }
                var effects = stageArray(stage(selectedStage), "effects");
                for (int i = 0; i < effects.size(); i++) {
                    if (PanelStyle.hit(mx, my, cx + cw - 24, effectsY + 14 + i * 16, 12, 12)) {
                        effects.remove(i);
                        entry.dirty = true;
                        return true;
                    }
                }

                var selectedStageObj = stage(selectedStage);
                int eventsY = effectsY + 20 + stageArray(selectedStageObj, "effects").size() * 16;
                if (PanelStyle.hit(mx, my, cx + 8, eventsY + 10, 130, PanelStyle.CONTROL_H)) {
                    Minecraft.getInstance().gui.setScreen(new TypedObjectListScreen(this, "ON ENTER",
                            stageArray(selectedStageObj, "on_enter"),
                            TypedObjectListScreen.Kind.ACTION, this::markDirtyFromChild));
                    return true;
                }
                if (PanelStyle.hit(mx, my, cx + 146, eventsY + 10, 130, PanelStyle.CONTROL_H)) {
                    Minecraft.getInstance().gui.setScreen(new TypedObjectListScreen(this, "ON EXIT",
                            stageArray(selectedStageObj, "on_exit"),
                            TypedObjectListScreen.Kind.ACTION, this::markDirtyFromChild));
                    return true;
                }
            }
        }

        if (page == Page.RULES) {
            var rules = rulesArray();

            if (PanelStyle.hit(mx, my, cx + cw - 92, py + 40, 84, PanelStyle.CONTROL_H)) {
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

            int ruleRowY = py + 74;
            for (int i = 0; i < rules.size() && ruleRowY < py + ph - 40; i++) {
                int rowH = 52;
                if (PanelStyle.hit(mx, my, cx + cw - 26, ruleRowY + 4, 12, 12)) {
                    rules.remove(i);
                    entry.dirty = true;
                    return true;
                }
                if (PanelStyle.hit(mx, my, cx + 8, ruleRowY, cw - 16, rowH - 4)) {
                    Minecraft.getInstance().gui.setScreen(
                            new RuleEditScreen(this, rules.get(i).getAsJsonObject()));
                    return true;
                }
                ruleRowY += rowH;
            }
        }

        if (page == Page.ADVANCED) {
            int iy = py + 100;
            for (int i = 0; i < issues.size() && iy < py + 160; i++) {
                if (PanelStyle.hit(mx, my, cx + 8, iy, cw - 16, 12)) {
                    try {
                        setPage(Page.valueOf(issues.get(i).page()));
                    } catch (IllegalArgumentException ignored) { }
                    return true;
                }
                iy += 13;
            }
            // COPY — iy has advanced past the issue rows, matching render's flow
            if (PanelStyle.hit(mx, my, cx + cw - 60, iy + 4, 52, PanelStyle.CONTROL_H)) {
                Minecraft.getInstance().keyboardHandler.setClipboard(PRETTY.toJson(entry.json));
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingPreview && page == Page.DISPLAY) {
            updatePreview(event.x(), cx + 8, cw - 16);
            return true;
        }
        if (draggingThreshold >= 0 && page == Page.STAGES) {
            double min = JsonEdit.getDouble(entry.json, "value.min", 0);
            double max = JsonEdit.getDouble(entry.json, "value.max", 100);
            int tlX = cx + 8, tlW = cw - 16;
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