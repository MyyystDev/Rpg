package com.myyyst.myrpg.core.client.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.myyyst.myrpg.core.client.EffectHudOverlay;
import com.myyyst.myrpg.core.network.RpgPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The effect definition editor. Same three-zone shell as StatEditorScreen:
 * header strip, nav column, content well.
 *
 * <p>The effect twin of {@code StatEditorScreen}, with pages matching the
 * {@code EffectDefinition} schema: BEHAVIOR covers duration/stacking/restrictions,
 * MODIFIERS the attribute modifiers, EVENTS the five lifecycle action lists.</p>
 *
 * <p>As in the stat editor, every control writes straight into {@code entry.json};
 * {@code entry.pristine} holds the state at open time and saving is explicit.</p>
 */
public class EffectDefEditorScreen extends Screen {

    /** Compact form, used for what goes over the wire. */
    private static final Gson GSON = new Gson();
    /** Indented form, used for the raw-JSON view on the ADVANCED page. */
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    /** The nav column's entries; each maps to one render method and widget set. */
    enum Page { GENERAL, BEHAVIOR, MODIFIERS, RULES, EVENTS, DISPLAY, ADVANCED }

    // Allowed values for the cycle controls; these mirror what the codecs accept.
    private static final String[] CATEGORIES = {"neutral", "beneficial", "harmful"};
    private static final String[] STACK_MODES = {"refresh", "replace", "extend", "stacks"};
    private static final String[] OPERATIONS =
            {"add_value", "add_multiplied_base", "add_multiplied_total"};
    /** The EVENTS page, as {json key, caption, explanation} rows. */
    private static final String[][] EVENT_LISTS = {
            {"on_applied", "ON APPLIED", "When the effect is first applied."},
            {"on_stack_added", "ON STACK ADDED", "When another stack lands (stacks mode)."},
            {"on_max_stacks", "ON MAX STACKS", "When the stack cap is reached."},
            {"on_expired", "ON EXPIRED", "When the duration runs out."},
            {"on_removed", "ON REMOVED", "When removed early (cleanse, death)."},
    };

    private final EffectLibraryScreen parent;
    /** The effect being edited; its {@code json} is mutated directly. */
    private final EffectWorkingSet.Entry entry;
    private Page page = Page.GENERAL;

    // layout — all computed in init(), so the screen re-centres on resize
    private int px, py, pw, ph, navW;
    private int frameTop, frameBottom;
    private int wellFrameX1, wellFrameX2;
    /** Inner content area of the well, where every page draws. */
    private int wellX, wellY, wellW, wellH;

    private int modScroll;
    private int ruleScroll;
    /** Messages from the last {@link #validate()} run, shown on the ADVANCED page. */
    private List<String> errors = new ArrayList<>();
    /** True while the "you have unsaved changes" dialog is up. */
    private boolean confirmClose;

    public EffectDefEditorScreen(EffectLibraryScreen parent, EffectWorkingSet.Entry entry) {
        super(Component.literal("Effect Editor"));
        this.parent = parent;
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
        if (newPage != Page.MODIFIERS) modScroll = 0;
        clearWidgets();
        buildPageWidgets();
    }

    /** Creates the text fields for the current page; button-only pages add nothing. */
    private void buildPageWidgets() {
        int half = wellW / 2;
        switch (page) {
            case GENERAL -> {
                addFieldAt("display.name", "", wellX + 8, wellY + 26, half - 16);
                addFieldAt("__id", entry.effectId, wellX + half + 8, wellY + 26, half - 16);
                addFieldAt("display.description", "", wellX + 8, wellY + 66, wellW - 16);
                addFieldAt("display.icon", "", wellX + 8, wellY + 106, half - 16);
                addFieldAt("display.color", "", wellX + half + 8, wellY + 106, 80);
                addTagsField(wellX + half + 8, wellY + 146, half - 16);
            }
            case BEHAVIOR -> {
                if (!"infinite".equals(str("duration.type", "timed"))) {
                    addIntField("duration.default", 200, wellX + 168, wellY + 26, 56);
                    addIntField("duration.maximum", 0, wellX + 240, wellY + 26, 56);
                }
                if ("stacks".equals(str("stacking.mode", "refresh"))) {
                    addIntField("stacking.max_stacks", 1, wellX + 168, wellY + 92, 56);
                }
            }
            case MODIFIERS -> buildModifierWidgets();
            default -> { }
        }
    }

    /** One row of fields per attribute modifier, for the visible slice of the list. */
    private void buildModifierWidgets() {
        JsonArray mods = attrsArray();
        int frameY = wellY + 30;
        int rowH = 48;
        int visibleRows = modifierVisibleRows();
        for (int r = 0; r < visibleRows; r++) {
            int i = modScroll + r;
            if (i >= mods.size()) break;
            JsonObject mod = mods.get(i).getAsJsonObject();
            int ry = frameY + 14 + r * rowH;

            EditBox attrBox = new EditBox(font, wellX + 14, ry, wellW - 130, 16, Component.empty());
            attrBox.setHint(Component.literal("minecraft:attack_damage"));
            attrBox.setValue(JsonEdit.getString(mod, "attribute", ""));
            attrBox.setResponder(text -> {
                JsonEdit.set(mod, "attribute", text);
                entry.dirty = true;
            });
            addRenderableWidget(attrBox);

            int vy = ry + 20;
            addModNumber(mod, "value", wellX + 118, vy);
            addModNumber(mod, "value_per_stack", wellX + 172, vy);
            addModNumber(mod, "value_per_level", wellX + 226, vy);
        }
    }

    /** Numeric field bound to one key of one modifier object. */
    private void addModNumber(JsonObject mod, String key, int x, int y) {
        EditBox box = new EditBox(font, x, y, 48, 16, Component.empty());
        box.setValue(trimNum(JsonEdit.getDouble(mod, key, 0)));
        box.setResponder(text -> {
            try {
                double v = Double.parseDouble(text.trim());
                if (v == 0) mod.remove(key);
                else JsonEdit.set(mod, key, v);
                entry.dirty = true;
            } catch (NumberFormatException ignored) { }
        });
        addRenderableWidget(box);
    }

    // ------------------------------------------------------------ json helpers

    /** The "attributes" array, created empty on first use. */
    private JsonArray attrsArray() {
        if (!entry.json.has("attributes")) entry.json.add("attributes", new JsonArray());
        return entry.json.getAsJsonArray("attributes");
    }

    /** The "rules" array, created empty on first use. */
    private JsonArray rulesArray() {
        if (!entry.json.has("rules")) entry.json.add("rules", new JsonArray());
        return entry.json.getAsJsonArray("rules");
    }

    /** One of the "events" action lists (on_applied, on_expired, ...), created on first use. */
    private JsonArray eventArray(String key) {
        if (!entry.json.has("events")) entry.json.add("events", new JsonObject());
        JsonObject events = entry.json.getAsJsonObject("events");
        if (!events.has(key)) events.add(key, new JsonArray());
        return events.getAsJsonArray(key);
    }

    private String str(String path, String fallback) {
        return JsonEdit.getString(entry.json, path, fallback);
    }

    private boolean bool(String path, boolean fallback) {
        return JsonEdit.getBool(entry.json, path, fallback);
    }

    // ------------------------------------------------------------ widget builders

    private void addFieldAt(String path, String fallback, int x, int y, int w) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
        String value = path.equals("__id") ? entry.effectId
                : JsonEdit.getString(entry.json, path, fallback);
        box.setValue(value);
        box.setResponder(text -> {
            if (path.equals("__id")) {
                entry.effectId = text;
            } else if (text.isEmpty()) {
                JsonEdit.remove(entry.json, path);
            } else {
                JsonEdit.set(entry.json, path, text);
            }
            entry.dirty = true;
        });
        addRenderableWidget(box);
    }

    /** Comma-separated text field backed by the JSON "tags" array. */
    private void addTagsField(int x, int y, int w) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
        box.setHint(Component.literal("rpg:ice, rpg:crowd_control"));
        StringBuilder joined = new StringBuilder();
        if (entry.json.has("tags")) {
            for (var tag : entry.json.getAsJsonArray("tags")) {
                if (!joined.isEmpty()) joined.append(", ");
                joined.append(tag.getAsString());
            }
        }
        box.setValue(joined.toString());
        box.setResponder(text -> {
            JsonArray tags = new JsonArray();
            for (String part : text.split(",")) {
                String tag = part.trim();
                if (!tag.isEmpty()) tags.add(tag);
            }
            if (tags.isEmpty()) entry.json.remove("tags");
            else entry.json.add("tags", tags);
            entry.dirty = true;
        });
        addRenderableWidget(box);
    }

    /** Integer field (durations, stack caps); unparseable text is ignored. */
    private void addIntField(String path, int fallback, int x, int y, int w) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
        box.setValue(String.valueOf((long) JsonEdit.getDouble(entry.json, path, fallback)));
        box.setResponder(text -> {
            try {
                JsonEdit.set(entry.json, path, (double) Integer.parseInt(text.trim()));
                entry.dirty = true;
            } catch (NumberFormatException ignored) { }
        });
        addRenderableWidget(box);
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

        g.enableScissor(wellFrameX1 + 1, frameTop + 1, wellFrameX2 - 1, frameBottom - 1);
        switch (page) {
            case GENERAL -> renderGeneral(g, mouseX, mouseY);
            case BEHAVIOR -> renderBehavior(g, mouseX, mouseY);
            case MODIFIERS -> renderModifiers(g, mouseX, mouseY);
            case RULES -> renderRules(g, mouseX, mouseY);
            case EVENTS -> renderEvents(g, mouseX, mouseY);
            case DISPLAY -> renderDisplay(g, mouseX, mouseY);
            case ADVANCED -> renderAdvanced(g, mouseX, mouseY);
        }
        g.disableScissor();

        super.extractRenderState(g, mouseX, mouseY, delta);

        if (confirmClose) renderConfirmClose(g, mouseX, mouseY);
    }

    /** Header strip: back arrow, effect name, UNSAVED chip, save button. */
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

    /** GENERAL page: name, id, description, category and tags. */
    private void renderGeneral(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int half = wellW / 2;
        g.text(font, Component.literal("DISPLAY NAME"), wellX + 8, wellY + 16, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("RESOURCE ID"), wellX + half + 8, wellY + 16, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("DESCRIPTION"), wellX + 8, wellY + 56, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("ICON (texture path)"), wellX + 8, wellY + 96, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("COLOR"), wellX + half + 8, wellY + 96, PanelStyle.TEXT_DIM);
        String hex = str("display.color", "");
        if (!hex.isEmpty()) {
            g.fill(wellX + half + 96, wellY + 106, wellX + half + 114, wellY + 124,
                    0xFF000000 | parseColor(hex));
        }
        g.text(font, Component.literal("CATEGORY"), wellX + 8, wellY + 136, PanelStyle.TEXT_DIM);
        String category = str("category", "neutral");
        PanelStyle.button(g, font, category.toUpperCase(), wellX + 8, wellY + 146, 140,
                PanelStyle.hit(mouseX, mouseY, wellX + 8, wellY + 146, 140, PanelStyle.CONTROL_H), false);
        g.text(font, Component.literal("TAGS (comma separated)"), wellX + half + 8, wellY + 136, PanelStyle.TEXT_DIM);
    }

    /** BEHAVIOR page: duration, stacking mode and the four action restrictions. */
    private void renderBehavior(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        boolean infinite = "infinite".equals(str("duration.type", "timed"));
        g.text(font, Component.literal("DURATION"), wellX + 8, wellY + 4, PanelStyle.TEXT);
        PanelStyle.button(g, font, infinite ? "Infinite" : "Timed", wellX + 8, wellY + 24, 140,
                PanelStyle.hit(mouseX, mouseY, wellX + 8, wellY + 24, 140, PanelStyle.CONTROL_H), false);
        if (!infinite) {
            g.text(font, Component.literal("DEFAULT"), wellX + 168, wellY + 16, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("MAX (0=off)"), wellX + 240, wellY + 16, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("ticks"), wellX + 304, wellY + 31, PanelStyle.TEXT_DIM);
        }

        g.text(font, Component.literal("STACKING"), wellX + 8, wellY + 70, PanelStyle.TEXT);
        String mode = str("stacking.mode", "refresh");
        PanelStyle.button(g, font, mode.toUpperCase(), wellX + 8, wellY + 90, 140,
                PanelStyle.hit(mouseX, mouseY, wellX + 8, wellY + 90, 140, PanelStyle.CONTROL_H), false);
        g.text(font, Component.literal(stackModeHint(mode)), wellX + 8, wellY + 118, PanelStyle.TEXT_DIM);
        if ("stacks".equals(mode)) {
            g.text(font, Component.literal("MAX STACKS"), wellX + 168, wellY + 82, PanelStyle.TEXT_DIM);
            boolean refresh = bool("stacking.refresh_duration", true);
            renderCheckbox(g, "Refresh duration on stack", refresh, wellX + 240, wellY + 94);
        }

        g.text(font, Component.literal("RESTRICTIONS (while active)"), wellX + 8, wellY + 136, PanelStyle.TEXT);
        renderCheckbox(g, "Can move", bool("restrictions.can_move", true), wellX + 8, wellY + 150);
        renderCheckbox(g, "Can jump", bool("restrictions.can_jump", true), wellX + 128, wellY + 150);
        renderCheckbox(g, "Can attack", bool("restrictions.can_attack", true), wellX + 8, wellY + 170);
        renderCheckbox(g, "Can use items", bool("restrictions.can_use_items", true), wellX + 128, wellY + 170);

        g.text(font, Component.literal("PERSISTENCE"), wellX + 8, wellY + 196, PanelStyle.TEXT);
        boolean keepDeath = bool("persistence.keep_on_death", false);
        renderCheckbox(g, "Keep on death", keepDeath, wellX + 8, wellY + 210);
        boolean keepLogout = bool("persistence.keep_on_logout", true);
        renderCheckbox(g, "Keep on logout", keepLogout, wellX + 128, wellY + 210);
    }

    /** Plain-English explanation of the selected stacking mode, shown under the control. */
    private static String stackModeHint(String mode) {
        return switch (mode) {
            case "replace" -> "Re-apply overwrites duration, level and stacks.";
            case "extend" -> "Re-apply adds the durations together.";
            case "stacks" -> "Re-apply adds a stack, up to the cap.";
            default -> "Re-apply restarts the duration.";
        };
    }

    /** How many modifier rows fit in the well; shared by the widget builder and the renderer. */
    private int modifierVisibleRows() {
        int frameY = wellY + 30;
        int frameH = wellY + wellH - frameY - 4;
        return Math.max(1, (frameH - 16) / 48);
    }

    /** MODIFIERS page: the attribute-modifier list, one editable row each. */
    private void renderModifiers(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("ATTRIBUTE MODIFIERS"), wellX + 8, wellY + 4, PanelStyle.TEXT);
        PanelStyle.button(g, font, "+ ADD", wellX + wellW - 64, wellY, 56,
                PanelStyle.hit(mouseX, mouseY, wellX + wellW - 64, wellY, 56, PanelStyle.CONTROL_H), true);

        int frameY = wellY + 30;
        int frameH = wellY + wellH - frameY - 4;
        PanelStyle.inset(g, wellX + 8, frameY, wellW - 16, frameH);

        JsonArray mods = attrsArray();
        int rowH = 48;
        int visibleRows = modifierVisibleRows();
        int maxScroll = Math.max(0, mods.size() - visibleRows);
        modScroll = Math.min(modScroll, maxScroll);

        if (mods.isEmpty()) {
            g.text(font, Component.literal("No modifiers. + ADD to boost or weaken an attribute."),
                    wellX + 16, frameY + 8, PanelStyle.TEXT_DIM);
        }

        for (int r = 0; r < visibleRows; r++) {
            int i = modScroll + r;
            if (i >= mods.size()) break;
            JsonObject mod = mods.get(i).getAsJsonObject();
            int ry = frameY + 14 + r * rowH;

            // labels row (drawn once per row, above widgets)
            g.text(font, Component.literal("ATTRIBUTE"), wellX + 14, ry - 10, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("X"), wellX + wellW - 26, ry + 4,
                    PanelStyle.hit(mouseX, mouseY, wellX + wellW - 30, ry, 12, 12)
                            ? PanelStyle.ERROR : PanelStyle.TEXT_DIM);

            String op = JsonEdit.getString(mod, "operation", "add_value");
            PanelStyle.button(g, font, opLabel(op), wellX + 14, ry + 19, 96,
                    PanelStyle.hit(mouseX, mouseY, wellX + 14, ry + 19, 96, PanelStyle.CONTROL_H), false);
            g.text(font, Component.literal("VALUE"), wellX + 118, ry + 12, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("+STACK"), wellX + 172, ry + 12, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("+LEVEL"), wellX + 226, ry + 12, PanelStyle.TEXT_DIM);

            if (r < visibleRows - 1 && i < mods.size() - 1) {
                g.fill(wellX + 12, ry + rowH - 6, wellX + wellW - 12, ry + rowH - 5, PanelStyle.PANEL_DARK);
            }
        }

        PanelStyle.scrollbar(g, wellX + wellW - 14, frameY + 2, frameH - 4,
                mods.size(), visibleRows, modScroll);
    }

    /** Short label for an attribute operation ("+", "x base", "x total"). */
    private static String opLabel(String op) {
        return switch (op) {
            case "add_multiplied_base" -> "x BASE";
            case "add_multiplied_total" -> "x TOTAL";
            default -> "+ VALUE";
        };
    }

    /** RULES page: one row per rule, summarised as trigger + condition/action counts. */
    private void renderRules(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("RULES"), wellX + 8, wellY + 4, PanelStyle.TEXT);
        g.text(font, Component.literal("Things this effect does while active."), wellX + 8, wellY + 16, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, "+ NEW RULE", wellX + wellW - 92, wellY, 84,
                PanelStyle.hit(mouseX, mouseY, wellX + wellW - 92, wellY, 84, PanelStyle.CONTROL_H), true);

        int frameY = wellY + 30;
        int frameH = wellH - 34;
        PanelStyle.inset(g, wellX + 8, frameY, wellW - 16, frameH);

        JsonArray rules = rulesArray();
        int rowH = 52;
        int visibleRows = (frameH - 4) / rowH;
        int maxScroll = Math.max(0, rules.size() - visibleRows);
        ruleScroll = Math.min(ruleScroll, maxScroll);

        for (int r = 0; r < visibleRows; r++) {
            int i = ruleScroll + r;
            if (i >= rules.size()) break;
            JsonObject rule = rules.get(i).getAsJsonObject();
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

    /** Human-readable one-liner for a rule's trigger. */
    private String summarizeTrigger(JsonObject rule) {
        if (!rule.has("trigger")) return "(none)";
        JsonObject trigger = rule.getAsJsonObject("trigger");
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
    private String summarizeList(JsonObject rule, String key, String emptyWord) {
        if (!rule.has(key) || rule.getAsJsonArray(key).isEmpty()) return emptyWord;
        JsonArray array = rule.getAsJsonArray(key);
        JsonObject first = array.get(0).getAsJsonObject();
        String type = first.has("type") ? first.get("type").getAsString() : "?";
        String label = type.substring(type.indexOf(':') + 1).replace('_', ' ');
        return array.size() == 1 ? label : label + " +" + (array.size() - 1) + " more";
    }

    /** EVENTS page: one row per lifecycle hook from {@link #EVENT_LISTS}, opening its action list. */
    private void renderEvents(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("LIFECYCLE EVENTS"), wellX + 8, wellY + 4, PanelStyle.TEXT);
        g.text(font, Component.literal("Actions that run at each moment."), wellX + 8, wellY + 16, PanelStyle.TEXT_DIM);
        int ey = wellY + 32;
        for (String[] spec : EVENT_LISTS) {
            int count = entry.json.has("events")
                    && entry.json.getAsJsonObject("events").has(spec[0])
                    ? entry.json.getAsJsonObject("events").getAsJsonArray(spec[0]).size() : 0;
            PanelStyle.button(g, font, spec[1] + " (" + count + ")", wellX + 8, ey, 170,
                    PanelStyle.hit(mouseX, mouseY, wellX + 8, ey, 170, PanelStyle.CONTROL_H), false);
            g.text(font, Component.literal(spec[2]), wellX + 186, ey + 8, PanelStyle.TEXT_DIM);
            ey += 30;
        }
    }

    /** DISPLAY page: icon/colour and the show_* flags, with a live HUD chip preview. */
    private void renderDisplay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("HUD DISPLAY"), wellX + 8, wellY + 4, PanelStyle.TEXT);
        renderCheckbox(g, "Show icon", bool("display_options.show_icon", true), wellX + 8, wellY + 20);
        renderCheckbox(g, "Show duration", bool("display_options.show_duration", true), wellX + 128, wellY + 20);
        renderCheckbox(g, "Show stacks", bool("display_options.show_stacks", true), wellX + 8, wellY + 40);
        renderCheckbox(g, "Show level", bool("display_options.show_level", false), wellX + 128, wellY + 40);
        renderCheckbox(g, "Hidden (never on HUD)", bool("display_options.hidden", false), wellX + 8, wellY + 60);

        // live chip preview, exactly what the HUD draws
        g.text(font, Component.literal("LIVE PREVIEW"), wellX + 8, wellY + 92, PanelStyle.TEXT_DIM);
        PanelStyle.inset(g, wellX + 8, wellY + 102, wellW - 16, 48);
        if (!bool("display_options.hidden", false)) {
            RpgPayloads.EffectEntry preview = new RpgPayloads.EffectEntry(
                    Identifier.fromNamespaceAndPath("myrpg_core", "preview"),
                    entry.displayName(),
                    str("display.color", categoryFallback(str("category", "neutral"))),
                    str("display.icon", ""),
                    str("category", "neutral"),
                    (int) JsonEdit.getDouble(entry.json, "duration.default", 200),
                    2, 3,
                    bool("display_options.show_icon", true),
                    bool("display_options.show_duration", true),
                    bool("display_options.show_stacks", true),
                    bool("display_options.show_level", false));
            int chipX = wellX + (wellW - 18) / 2;
            EffectHudOverlay.drawChip(g, Minecraft.getInstance(), preview,
                    "infinite".equals(str("duration.type", "timed")) ? -1 : preview.remaining(),
                    chipX, wellY + 110);
        } else {
            g.text(font, Component.literal("(hidden - synced but never drawn)"),
                    wellX + 16, wellY + 120, PanelStyle.TEXT_DIM);
        }
        g.text(font, Component.literal("Shown with level 2 and 3 stacks."),
                wellX + 8, wellY + 156, PanelStyle.TEXT_DIM);
    }

    /** Default colour for a category, matching the server-side fallback in {@code EffectSync}. */
    private static String categoryFallback(String category) {
        return switch (category) {
            case "beneficial" -> "#58C85E";
            case "harmful" -> "#E05555";
            default -> "#A8A8B8";
        };
    }

    /** ADVANCED page: the validation report plus a read-only pretty-printed JSON view. */
    private void renderAdvanced(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("FILE"), wellX + 8, wellY + 4, PanelStyle.TEXT_DIM);
        String ns = entry.effectId.contains(":") ? entry.effectId.split(":", 2)[0] : "?";
        String path = entry.effectId.contains(":") ? entry.effectId.split(":", 2)[1] : entry.effectId;
        g.text(font, Component.literal("datapacks/myrpg_editor/data/" + ns + "/myrpg/effects/" + path + ".json"),
                wellX + 8, wellY + 14, PanelStyle.TEXT);

        errors = validate();
        g.text(font, Component.literal("VALIDATION"), wellX + 8, wellY + 32, PanelStyle.TEXT_DIM);
        if (errors.isEmpty()) {
            PanelStyle.chip(g, font, "NO ISSUES", wellX + 70, wellY + 30, PanelStyle.VALID);
        } else {
            PanelStyle.chip(g, font, errors.size() + " ERROR", wellX + 70, wellY + 30, PanelStyle.ERROR);
        }
        int iy = wellY + 46;
        for (int i = 0; i < errors.size() && iy < wellY + 90; i++) {
            g.text(font, Component.literal(errors.get(i)), wellX + 8, iy, PanelStyle.ERROR);
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

    /** Labelled checkbox; the click handling lives in {@link #mouseClicked}. */
    private void renderCheckbox(GuiGraphicsExtractor g, String label, boolean checked, int x, int y) {
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
     * Hit-testing for the header, nav column and the current page's immediate-mode controls.
     * Page-specific handling is delegated to the {@code *Clicked} helpers below.
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
                confirmClose = false;
                if (!entry.dirty) {
                    Minecraft.getInstance().gui.setScreen(parent);
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

        if (page == Page.GENERAL) {
            if (PanelStyle.hit(mx, my, wellX + 8, wellY + 146, 140, PanelStyle.CONTROL_H)) {
                cycleString("category", CATEGORIES);
                return true;
            }
        }

        if (page == Page.BEHAVIOR && behaviorClicked(mx, my)) return true;

        if (page == Page.MODIFIERS && modifiersClicked(mx, my)) return true;

        if (page == Page.RULES && rulesClicked(mx, my)) return true;

        if (page == Page.EVENTS) {
            int ey = wellY + 32;
            for (String[] spec : EVENT_LISTS) {
                if (PanelStyle.hit(mx, my, wellX + 8, ey, 170, PanelStyle.CONTROL_H)) {
                    Minecraft.getInstance().gui.setScreen(new TypedObjectListScreen(this, spec[1],
                            eventArray(spec[0]), TypedObjectListScreen.Kind.ACTION, this::markDirtyFromChild));
                    return true;
                }
                ey += 30;
            }
        }

        if (page == Page.DISPLAY) {
            if (PanelStyle.hit(mx, my, wellX + 8, wellY + 20, 14, 14)) { toggleBool("display_options.show_icon", true); return true; }
            if (PanelStyle.hit(mx, my, wellX + 128, wellY + 20, 14, 14)) { toggleBool("display_options.show_duration", true); return true; }
            if (PanelStyle.hit(mx, my, wellX + 8, wellY + 40, 14, 14)) { toggleBool("display_options.show_stacks", true); return true; }
            if (PanelStyle.hit(mx, my, wellX + 128, wellY + 40, 14, 14)) { toggleBool("display_options.show_level", false); return true; }
            if (PanelStyle.hit(mx, my, wellX + 8, wellY + 60, 14, 14)) { toggleBool("display_options.hidden", false); return true; }
        }

        if (page == Page.ADVANCED) {
            errors = validate();
            // COPY sits relative to the error block — same math as render:
            // the render loop draws at iy = 46, 59, 72, 85 (max 4 rows)
            int previewLabelY = wellY + 46 + Math.min(errors.size(), 4) * 13 + 4;
            if (PanelStyle.hit(mx, my, wellX + wellW - 60, previewLabelY - 2, 52, PanelStyle.CONTROL_H)) {
                Minecraft.getInstance().keyboardHandler.setClipboard(PRETTY.toJson(entry.json));
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    /** @return true if the click landed on a BEHAVIOR page control. */
    private boolean behaviorClicked(double mx, double my) {
        if (PanelStyle.hit(mx, my, wellX + 8, wellY + 24, 140, PanelStyle.CONTROL_H)) {
            boolean infinite = "infinite".equals(str("duration.type", "timed"));
            if (infinite) JsonEdit.remove(entry.json, "duration.type");
            else JsonEdit.set(entry.json, "duration.type", "infinite");
            entry.dirty = true;
            setPage(Page.BEHAVIOR);
            return true;
        }
        if (PanelStyle.hit(mx, my, wellX + 8, wellY + 90, 140, PanelStyle.CONTROL_H)) {
            cycleString("stacking.mode", STACK_MODES);
            setPage(Page.BEHAVIOR);
            return true;
        }
        if ("stacks".equals(str("stacking.mode", "refresh"))
                && PanelStyle.hit(mx, my, wellX + 240, wellY + 94, 14, 14)) {
            toggleBool("stacking.refresh_duration", true);
            return true;
        }
        if (PanelStyle.hit(mx, my, wellX + 8, wellY + 150, 14, 14)) { toggleRestriction("can_move"); return true; }
        if (PanelStyle.hit(mx, my, wellX + 128, wellY + 150, 14, 14)) { toggleRestriction("can_jump"); return true; }
        if (PanelStyle.hit(mx, my, wellX + 8, wellY + 170, 14, 14)) { toggleRestriction("can_attack"); return true; }
        if (PanelStyle.hit(mx, my, wellX + 128, wellY + 170, 14, 14)) { toggleRestriction("can_use_items"); return true; }
        if (PanelStyle.hit(mx, my, wellX + 8, wellY + 210, 14, 14)) { toggleBool("persistence.keep_on_death", false); return true; }
        if (PanelStyle.hit(mx, my, wellX + 128, wellY + 210, 14, 14)) { toggleBool("persistence.keep_on_logout", true); return true; }
        return false;
    }

    /** @return true if the click landed on a MODIFIERS page control (add, cycle op, delete). */
    private boolean modifiersClicked(double mx, double my) {
        JsonArray mods = attrsArray();
        if (PanelStyle.hit(mx, my, wellX + wellW - 64, wellY, 56, PanelStyle.CONTROL_H)) {
            JsonObject mod = new JsonObject();
            mod.addProperty("attribute", "");
            mod.addProperty("operation", "add_value");
            mods.add(mod);
            entry.dirty = true;
            modScroll = Math.max(0, mods.size() - modifierVisibleRows());
            setPage(Page.MODIFIERS);
            return true;
        }
        int frameY = wellY + 30;
        int rowH = 48;
        int visibleRows = modifierVisibleRows();
        for (int r = 0; r < visibleRows; r++) {
            int i = modScroll + r;
            if (i >= mods.size()) break;
            int ry = frameY + 14 + r * rowH;
            if (PanelStyle.hit(mx, my, wellX + wellW - 30, ry, 12, 12)) {
                mods.remove(i);
                entry.dirty = true;
                setPage(Page.MODIFIERS);
                return true;
            }
            if (PanelStyle.hit(mx, my, wellX + 14, ry + 19, 96, PanelStyle.CONTROL_H)) {
                JsonObject mod = mods.get(i).getAsJsonObject();
                String current = JsonEdit.getString(mod, "operation", "add_value");
                int idx = 0;
                for (int k = 0; k < OPERATIONS.length; k++) if (OPERATIONS[k].equals(current)) idx = k;
                JsonEdit.set(mod, "operation", OPERATIONS[(idx + 1) % OPERATIONS.length]);
                entry.dirty = true;
                return true;
            }
        }
        return false;
    }

    /** @return true if the click landed on a RULES page control (add, open, delete). */
    private boolean rulesClicked(double mx, double my) {
        JsonArray rules = rulesArray();
        if (PanelStyle.hit(mx, my, wellX + wellW - 92, wellY, 84, PanelStyle.CONTROL_H)) {
            JsonObject rule = new JsonObject();
            JsonObject trigger = new JsonObject();
            trigger.addProperty("type", "myrpg_core:interval");
            trigger.addProperty("ticks", 40);
            rule.add("trigger", trigger);
            rules.add(rule);
            entry.dirty = true;
            Minecraft.getInstance().gui.setScreen(
                    new RuleEditScreen(this, rule, this::markDirtyFromChild));
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
                Minecraft.getInstance().gui.setScreen(new RuleEditScreen(
                        this, rules.get(i).getAsJsonObject(), this::markDirtyFromChild));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (page == Page.MODIFIERS) {
            int max = Math.max(0, attrsArray().size() - modifierVisibleRows());
            int next = Math.max(0, Math.min(max, modScroll - (int) Math.signum(vertical)));
            if (next != modScroll) {
                modScroll = next;
                setPage(Page.MODIFIERS);   // rebuild row widgets for the new window
            }
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

    /** Flips a boolean at a dotted path and marks the entry dirty. */
    private void toggleBool(String path, boolean fallback) {
        JsonEdit.set(entry.json, path, !JsonEdit.getBool(entry.json, path, fallback));
        entry.dirty = true;
    }

    /** Restriction checkboxes: default true; drop the object when all true. */
    /**
     * Flips one restriction flag. Restrictions default to "allowed", so this writes the
     * inverse of the current effective value under {@code restrictions.<key>}.
     */
    private void toggleRestriction(String key) {
        boolean current = JsonEdit.getBool(entry.json, "restrictions." + key, true);
        JsonEdit.set(entry.json, "restrictions." + key, !current);
        JsonObject restrictions = entry.json.getAsJsonObject("restrictions");
        boolean allTrue = true;
        for (String k : new String[]{"can_move", "can_jump", "can_attack", "can_use_items"}) {
            if (restrictions.has(k) && !restrictions.get(k).getAsBoolean()) allTrue = false;
        }
        if (allTrue) entry.json.remove("restrictions");
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
     * Client-side sanity checks before saving (id shape, duration/stack sanity, ...).
     * Advisory: the server re-validates through the real codec in {@code OverlaySaver}.
     */
    private List<String> validate() {
        List<String> out = new ArrayList<>();
        Identifier id = Identifier.tryParse(entry.effectId);
        if (id == null || !entry.effectId.contains(":")) {
            out.add("ID must be namespace:path");
        }
        String category = str("category", "neutral");
        if (!category.equals("neutral") && !category.equals("beneficial") && !category.equals("harmful")) {
            out.add("Category must be neutral/beneficial/harmful");
        }
        if (!"infinite".equals(str("duration.type", "timed"))
                && JsonEdit.getDouble(entry.json, "duration.default", 200) < 1) {
            out.add("Timed duration needs default >= 1 tick");
        }
        JsonArray mods = entry.json.has("attributes") ? entry.json.getAsJsonArray("attributes") : new JsonArray();
        for (int i = 0; i < mods.size(); i++) {
            String attr = JsonEdit.getString(mods.get(i).getAsJsonObject(), "attribute", "");
            if (Identifier.tryParse(attr) == null || !attr.contains(":")) {
                out.add("Modifier " + (i + 1) + " needs a valid attribute id");
            }
        }
        return out;
    }

    /**
     * Validates and, if clean, sends the definition to the server.
     * Problems abort the save and jump to the ADVANCED page, where the messages are listed.
     */
    private void save() {
        errors = validate();
        if (!errors.isEmpty()) {
            setPage(Page.ADVANCED);
            return;
        }
        // strip empty scaffolding arrays before sending
        if (entry.json.has("attributes") && entry.json.getAsJsonArray("attributes").isEmpty()) {
            entry.json.remove("attributes");
        }
        if (entry.json.has("rules") && entry.json.getAsJsonArray("rules").isEmpty()) {
            entry.json.remove("rules");
        }
        if (entry.json.has("events")) {
            JsonObject events = entry.json.getAsJsonObject("events");
            for (String[] spec : EVENT_LISTS) {
                if (events.has(spec[0]) && events.getAsJsonArray(spec[0]).isEmpty()) {
                    events.remove(spec[0]);
                }
            }
            if (events.isEmpty()) entry.json.remove("events");
        }
        ClientEditorNet.sendSaveEffect(entry.effectId, GSON.toJson(entry.json));
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

    /** Called by child screens (rule and typed-object editors) after they change the JSON. */
    void markDirtyFromChild() { entry.dirty = true; }

    /** Editing must not pause a singleplayer world. */
    @Override
    public boolean isPauseScreen() { return false; }
}
