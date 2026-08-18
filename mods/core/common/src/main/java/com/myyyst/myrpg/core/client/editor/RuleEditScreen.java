package com.myyyst.myrpg.core.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * One rule: trigger config + entry points into its conditions and
 * actions lists. Trigger types cycle between interval and event.
 *
 * <p>Edits the rule object in place; the {@code onDirty} callback tells the host screen
 * that its definition changed. Conditions and actions are not edited here - the two buttons
 * hand their arrays to the generic {@link TypedObjectListScreen}.</p>
 */
public class RuleEditScreen extends Screen {

    /** The two trigger types the UI can build; hand-written JSON may use others. */
    private static final String[] TRIGGER_TYPES = {"myrpg_core:interval", "myrpg_core:event"};
    /** Event ids offered by the cycle button; mirrors the constants in {@code RpgEvents}. */
    private static final String[] EVENTS = {
            "myrpg_core:player_join", "myrpg_core:player_respawn",
            "myrpg_core:player_death", "myrpg_core:player_kill"};

    private final Screen parent;
    /** The rule being edited, mutated in place. */
    private final JsonObject rule;
    /** Notifies the host screen that the definition changed. */
    private final Runnable onDirty;
    private EditBox ticksBox, offsetBox;
    private int px, py, pw, ph;

    /** Convenience constructor for the stat editor, which owns its own dirty flag. */
    public RuleEditScreen(StatEditorScreen parent, JsonObject rule) {
        this(parent, rule, parent::markDirtyFromChild);
    }

    /** Generic form — any host screen with rules (stat editor, effect editor). */
    public RuleEditScreen(Screen parent, JsonObject rule, Runnable onDirty) {
        super(Component.literal("Edit Rule"));
        this.parent = parent;
        this.rule = rule;
        this.onDirty = onDirty;
    }

    /** The rule's trigger, created as a 200-tick interval if the rule has none yet. */
    private JsonObject trigger() {
        if (!rule.has("trigger")) {
            JsonObject trigger = new JsonObject();
            trigger.addProperty("type", "myrpg_core:interval");
            trigger.addProperty("ticks", 200);
            rule.add("trigger", trigger);
        }
        return rule.getAsJsonObject("trigger");
    }

    /** The rule's "conditions" or "actions" array, created empty on first use. */
    private JsonArray list(String key) {
        if (!rule.has(key)) rule.add(key, new JsonArray());
        return rule.getAsJsonArray(key);
    }

    /** Current trigger type id, defaulting to interval. */
    private String triggerType() {
        return trigger().has("type") ? trigger().get("type").getAsString() : TRIGGER_TYPES[0];
    }

    /**
     * Builds the controls for the current trigger type. Only interval triggers need text
     * fields; the event type is edited entirely through a cycle button, so it adds no widgets.
     */
    @Override
    protected void init() {
        pw = 300;
        ph = 220;
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        if (triggerType().endsWith(":interval")) {
            // Responders write straight into the JSON, so there is no separate "apply" step.
            ticksBox = new EditBox(font, px + PanelStyle.GRID, py + 78, 80, 18, Component.empty());
            ticksBox.setValue(String.valueOf(trigger().has("ticks") ? trigger().get("ticks").getAsInt() : 200));
            ticksBox.setResponder(text -> {
                try {
                    trigger().addProperty("ticks", Integer.parseInt(text.trim()));
                    onDirty.run();
                } catch (NumberFormatException ignored) { }
            });
            addRenderableWidget(ticksBox);

            offsetBox = new EditBox(font, px + PanelStyle.GRID + 96, py + 78, 80, 18, Component.empty());
            offsetBox.setValue(String.valueOf(trigger().has("offset") ? trigger().get("offset").getAsInt() : 0));
            offsetBox.setResponder(text -> {
                try {
                    trigger().addProperty("offset", Integer.parseInt(text.trim()));
                    onDirty.run();
                } catch (NumberFormatException ignored) { }
            });
            addRenderableWidget(offsetBox);
        }
    }

    /** Re-runs init() after the trigger type changed, so the right controls exist. */
    private void rebuild() {
        clearWidgets();   // NOTE drift: same spelling as StatEditorScreen.setPage
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, px, py, pw, ph);
        g.text(font, Component.literal("EDIT RULE"), px + PanelStyle.GRID, py + PanelStyle.GRID, PanelStyle.TEXT);

        boolean interval = triggerType().endsWith(":interval");
        g.text(font, Component.literal("TRIGGER"), px + PanelStyle.GRID, py + 30, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, interval ? "Every X ticks" : "On event",
                px + PanelStyle.GRID, py + 40, 140,
                PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID, py + 40, 140, PanelStyle.CONTROL_H), false);

        if (interval) {
            g.text(font, Component.literal("TICKS"), px + PanelStyle.GRID, py + 68, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("OFFSET"), px + PanelStyle.GRID + 96, py + 68, PanelStyle.TEXT_DIM);
        } else {
            g.text(font, Component.literal("EVENT"), px + PanelStyle.GRID, py + 68, PanelStyle.TEXT_DIM);
            String event = trigger().has("event") ? trigger().get("event").getAsString() : EVENTS[0];
            String shortName = event.substring(event.indexOf(':') + 1);
            PanelStyle.button(g, font, shortName, px + PanelStyle.GRID, py + 78, 180,
                    PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID, py + 78, 180, PanelStyle.CONTROL_H), false);
        }

        PanelStyle.button(g, font, "CONDITIONS (" + list("conditions").size() + ")",
                px + PanelStyle.GRID, py + 120, pw - PanelStyle.GRID * 2,
                PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID, py + 120, pw - PanelStyle.GRID * 2, PanelStyle.CONTROL_H), false);
        PanelStyle.button(g, font, "ACTIONS (" + list("actions").size() + ")",
                px + PanelStyle.GRID, py + 150, pw - PanelStyle.GRID * 2,
                PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID, py + 150, pw - PanelStyle.GRID * 2, PanelStyle.CONTROL_H), false);

        PanelStyle.button(g, font, "DONE", px + pw - 80 - PanelStyle.GRID, py + ph - 32, 80,
                PanelStyle.hit(mouseX, mouseY, px + pw - 80 - PanelStyle.GRID, py + ph - 32, 80, PanelStyle.CONTROL_H), true);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();

        // trigger type cycle — the trigger is rebuilt from scratch rather than edited,
        // because the two types share no fields.
        if (PanelStyle.hit(mx, my, px + PanelStyle.GRID, py + 40, 140, PanelStyle.CONTROL_H)) {
            boolean interval = triggerType().endsWith(":interval");
            JsonObject trigger = new JsonObject();
            if (interval) {
                trigger.addProperty("type", "myrpg_core:event");
                trigger.addProperty("event", EVENTS[0]);
            } else {
                trigger.addProperty("type", "myrpg_core:interval");
                trigger.addProperty("ticks", 200);
            }
            rule.add("trigger", trigger);
            onDirty.run();
            rebuild();
            return true;
        }

        // event cycle — steps through the known event ids
        if (!triggerType().endsWith(":interval")
                && PanelStyle.hit(mx, my, px + PanelStyle.GRID, py + 78, 180, PanelStyle.CONTROL_H)) {
            String current = trigger().has("event") ? trigger().get("event").getAsString() : EVENTS[0];
            int idx = 0;
            for (int i = 0; i < EVENTS.length; i++) if (EVENTS[i].equals(current)) idx = i;
            trigger().addProperty("event", EVENTS[(idx + 1) % EVENTS.length]);
            onDirty.run();
            return true;
        }

        if (PanelStyle.hit(mx, my, px + PanelStyle.GRID, py + 120, pw - PanelStyle.GRID * 2, PanelStyle.CONTROL_H)) {
            Minecraft.getInstance().gui.setScreen(new TypedObjectListScreen(this, "CONDITIONS",
                    list("conditions"), TypedObjectListScreen.Kind.CONDITION, onDirty));
            return true;
        }
        if (PanelStyle.hit(mx, my, px + PanelStyle.GRID, py + 150, pw - PanelStyle.GRID * 2, PanelStyle.CONTROL_H)) {
            Minecraft.getInstance().gui.setScreen(new TypedObjectListScreen(this, "ACTIONS",
                    list("actions"), TypedObjectListScreen.Kind.ACTION, onDirty));
            return true;
        }
        if (PanelStyle.hit(mx, my, px + pw - 80 - PanelStyle.GRID, py + ph - 32, 80, PanelStyle.CONTROL_H)) {
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Editing must not pause a singleplayer world. */
    @Override
    public boolean isPauseScreen() { return false; }
}