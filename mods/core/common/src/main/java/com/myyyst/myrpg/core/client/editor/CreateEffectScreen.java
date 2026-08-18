package com.myyyst.myrpg.core.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Create dialog: name (auto-generates ID until edited) + starting template.
 *
 * <p>Effect-side twin of {@code CreateStatScreen}. The templates are complete little
 * effects rather than bare skeletons - picking "Damage over time" already produces a
 * stacking effect with an interval rule that deals damage - so a new effect works in game
 * before any further editing.</p>
 */
public class CreateEffectScreen extends Screen {

    /**
     * The starting shapes offered by the dialog. {@link Template#build} writes the JSON
     * for each; only the fields a template actually needs are emitted, so the file stays
     * readable and everything else falls back to the codec defaults.
     */
    private enum Template {
        DOT("Damage over time", "Harmful. Deals damage on an interval and stacks up.",
                "harmful"),
        BUFF("Buff", "Beneficial. Boosts an attribute for a while.",
                "beneficial"),
        DEBUFF("Debuff", "Harmful. Weakens an attribute for a while.",
                "harmful"),
        CROWD_CONTROL("Crowd control", "Harmful. Locks movement and actions briefly.",
                "harmful"),
        EMPTY("Empty", "Only the minimum required configuration.",
                "neutral");

        final String label, description, category;
        Template(String label, String description, String category) {
            this.label = label; this.description = description; this.category = category;
        }

        /** Builds the starting JSON for this template. */
        JsonObject build(String displayName) {
            JsonObject root = new JsonObject();
            if (this != EMPTY) {   // EMPTY deliberately omits display and category
                JsonObject display = new JsonObject();
                display.addProperty("name", displayName);
                root.add("display", display);
                root.addProperty("category", category);
            }
            switch (this) {
                // 10s, stacks to 5, and an interval rule that deals 1 damage every 2s.
                case DOT -> {
                    JsonObject duration = new JsonObject();
                    duration.addProperty("default", 200);
                    root.add("duration", duration);
                    JsonObject stacking = new JsonObject();
                    stacking.addProperty("mode", "stacks");
                    stacking.addProperty("max_stacks", 5);
                    root.add("stacking", stacking);
                    JsonObject rule = new JsonObject();
                    JsonObject trigger = new JsonObject();
                    trigger.addProperty("type", "myrpg_core:interval");
                    trigger.addProperty("ticks", 40);
                    rule.add("trigger", trigger);
                    JsonArray actions = new JsonArray();
                    JsonObject damage = new JsonObject();
                    damage.addProperty("type", "myrpg_core:damage");
                    damage.addProperty("amount", 1.0);
                    actions.add(damage);
                    rule.add("actions", actions);
                    JsonArray rules = new JsonArray();
                    rules.add(rule);
                    root.add("rules", rules);
                }
                // 30s movement-speed modifier; same shape either way, sign flipped.
                case BUFF, DEBUFF -> {
                    JsonObject duration = new JsonObject();
                    duration.addProperty("default", 600);
                    root.add("duration", duration);
                    JsonArray attributes = new JsonArray();
                    JsonObject mod = new JsonObject();
                    mod.addProperty("attribute", "minecraft:movement_speed");
                    mod.addProperty("operation", "add_multiplied_total");
                    mod.addProperty("value", this == BUFF ? 0.2 : -0.2);
                    attributes.add(mod);
                    root.add("attributes", attributes);
                }
                // 3s full lock, capped at 10s so stacking cannot chain-stun forever.
                case CROWD_CONTROL -> {
                    JsonObject duration = new JsonObject();
                    duration.addProperty("default", 60);
                    duration.addProperty("maximum", 200);
                    root.add("duration", duration);
                    JsonObject restrictions = new JsonObject();
                    restrictions.addProperty("can_move", false);
                    restrictions.addProperty("can_jump", false);
                    restrictions.addProperty("can_attack", false);
                    restrictions.addProperty("can_use_items", false);
                    root.add("restrictions", restrictions);
                }
                case EMPTY -> { }
            }
            return root;
        }
    }

    private final EffectLibraryScreen parent;
    /** Used to reject an id that already exists. */
    private final EffectWorkingSet workingSet;
    private EditBox nameBox, idBox;
    /** Once the user types in the id box, the name no longer overwrites it. */
    private boolean idManuallyEdited;
    /** Ordinal into {@link Template}. */
    private int template;
    /** Validation message shown in the dialog, empty when there is none. */
    private String error = "";
    private int px, py, pw, ph;

    public CreateEffectScreen(EffectLibraryScreen parent, EffectWorkingSet workingSet) {
        super(Component.literal("Create Effect"));
        this.parent = parent;
        this.workingSet = workingSet;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 4 * PanelStyle.GRID, 420);
        ph = 240;
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int fx = px + PanelStyle.GRID * 2;
        int fw = pw / 2 - PanelStyle.GRID * 3;

        nameBox = box(fx, py + 40, fw, "Bleeding");
        idBox = box(fx, py + 80, fw, "mypack:bleeding");

        nameBox.setResponder(name -> {
            if (!idManuallyEdited) {
                idBox.setValue("mypack:" + name.toLowerCase().replaceAll("[^a-z0-9_]", "_"));
            }
        });
        idBox.setResponder(id -> {
            if (idBox.isFocused()) idManuallyEdited = true;
        });
    }

    /** Creates a hinted text field and registers it as a widget. */
    private EditBox box(int x, int y, int w, String hint) {
        EditBox b = new EditBox(font, x, y, w, 18, Component.empty());
        b.setHint(Component.literal(hint));
        addRenderableWidget(b);
        return b;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, px, py, pw, ph);
        g.text(font, Component.literal("CREATE EFFECT"), px + PanelStyle.GRID * 2, py + PanelStyle.GRID, PanelStyle.TEXT);
        g.text(font, Component.literal("Start simple. Every value can be changed later."),
                px + PanelStyle.GRID * 2, py + PanelStyle.GRID + 12, PanelStyle.TEXT_DIM);

        int fx = px + PanelStyle.GRID * 2;
        g.text(font, Component.literal("NAME"), fx, py + 30, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("RESOURCE ID"), fx, py + 70, PanelStyle.TEXT_DIM);
        if (!idManuallyEdited) {
            g.text(font, Component.literal("Generated from name"), fx, py + 102, PanelStyle.TEXT_DIM);
        }
        g.text(font, Component.literal("TEMPLATE"), fx, py + 116, PanelStyle.TEXT_DIM);
        Template t = Template.values()[template];
        PanelStyle.button(g, font, t.label + "  ▼", fx, py + 126, pw / 2 - PanelStyle.GRID * 3,
                PanelStyle.hit(mouseX, mouseY, fx, py + 126, pw / 2 - PanelStyle.GRID * 3, PanelStyle.CONTROL_H), false);

        // template preview panel (right half)
        int tx = px + pw / 2 + PanelStyle.GRID;
        int tw = pw / 2 - PanelStyle.GRID * 3;
        PanelStyle.inset(g, tx, py + 30, tw, 150);
        g.text(font, Component.literal(t.label.toUpperCase()), tx + PanelStyle.GRID, py + 38, PanelStyle.ACCENT);
        drawWrapped(g, t.description, tx + PanelStyle.GRID, py + 52, tw - PanelStyle.GRID * 2);
        g.text(font, Component.literal("CATEGORY  " + t.category.toUpperCase()),
                tx + PanelStyle.GRID, py + 114, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("PRESET ONLY - NOT A LOCKED TYPE"), tx + PanelStyle.GRID, py + 164, PanelStyle.TEXT_DIM);

        if (!error.isEmpty()) {
            g.text(font, Component.literal(error),
                    px + (pw - font.width(error)) / 2, py + ph - 46, PanelStyle.ERROR);
        }

        PanelStyle.button(g, font, "CANCEL", px + PanelStyle.GRID * 2, py + ph - 32, 96,
                PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID * 2, py + ph - 32, 96, PanelStyle.CONTROL_H), false);
        PanelStyle.button(g, font, "CREATE EFFECT", px + pw - 120 - PanelStyle.GRID * 2, py + ph - 32, 120,
                PanelStyle.hit(mouseX, mouseY, px + pw - 120 - PanelStyle.GRID * 2, py + ph - 32, 120, PanelStyle.CONTROL_H), true);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    /** Greedy word wrap for the template description. */
    private void drawWrapped(GuiGraphicsExtractor g, String text, int x, int y, int w) {
        StringBuilder line = new StringBuilder();
        int ly = y;
        for (String word : text.split(" ")) {
            if (font.width(line + " " + word) > w) {
                g.text(font, Component.literal(line.toString()), x, ly, PanelStyle.TEXT_DIM);
                line = new StringBuilder(word);
                ly += 10;
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty()) g.text(font, Component.literal(line.toString()), x, ly, PanelStyle.TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int fx = px + PanelStyle.GRID * 2;

        if (PanelStyle.hit(mx, my, fx, py + 126, pw / 2 - PanelStyle.GRID * 3, PanelStyle.CONTROL_H)) {
            template = (template + 1) % Template.values().length;
            return true;
        }
        if (PanelStyle.hit(mx, my, px + PanelStyle.GRID * 2, py + ph - 32, 96, PanelStyle.CONTROL_H)) {
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        if (PanelStyle.hit(mx, my, px + pw - 120 - PanelStyle.GRID * 2, py + ph - 32, 120, PanelStyle.CONTROL_H)) {
            create();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Builds the effect from the template, saves it, and adds it to the library.
     * The save goes to the server immediately, so the effect exists as a real datapack file
     * even if the editor is closed straight afterwards.
     */
    private void create() {
        String id = idBox.getValue().trim();
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null || !id.contains(":")) {
            error = "ID must be namespace:path, e.g. mypack:bleeding";
            return;
        }
        for (EffectWorkingSet.Entry existing : workingSet.entries) {
            if (existing.effectId.equals(id)) {
                error = "That ID already exists";
                return;
            }
        }
        String name = nameBox.getValue().isEmpty() ? parsed.getPath() : nameBox.getValue();
        EffectWorkingSet.Entry entry = new EffectWorkingSet.Entry(
                id, Template.values()[template].build(name));
        entry.dirty = true;
        parent.onCreated(entry);
        Minecraft.getInstance().gui.setScreen(new EffectDefEditorScreen(parent, entry));
    }

    /** Editing must not pause a singleplayer world. */
    @Override
    public boolean isPauseScreen() { return false; }
}
