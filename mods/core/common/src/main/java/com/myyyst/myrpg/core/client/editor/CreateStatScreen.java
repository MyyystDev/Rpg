package com.myyyst.myrpg.core.client.editor;

import com.google.gson.Gson;
import com.myyyst.myrpg.core.network.RpgPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Page 4: name (auto-generates ID until manually edited), template, range.
 *
 * <p>The "new stat" dialog. It builds the initial JSON from a {@link StatTemplates} preset,
 * sends it straight to the server, and adds the entry to the library's working set - the
 * full editor is then opened separately for any deeper configuration.</p>
 */
public class CreateStatScreen extends Screen {

    private static final Gson GSON = new Gson();

    private final StatLibraryScreen parent;
    /** Used to reject an id that already exists. */
    private final StatWorkingSet workingSet;
    private EditBox nameBox, idBox, minBox, maxBox, defaultBox;
    /** Once the user types in the id box, the name no longer overwrites it. */
    private boolean idManuallyEdited;
    private int template;   // StatTemplates ordinal
    /** Panel geometry, recomputed in init(). */
    private int px, py, pw, ph;

    public CreateStatScreen(StatLibraryScreen parent, StatWorkingSet workingSet) {
        super(Component.literal("Create Stat"));
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

        nameBox = box(fx, py + 40, fw, "Corruption");
        idBox = box(fx, py + 80, fw, "mypack:corruption");
        minBox = box(fx, py + 150, 56, "0");
        maxBox = box(fx + 64, py + 150, 56, "100");
        defaultBox = box(fx + 128, py + 150, 56, "0");
        applyTemplate();

        // Typing a name derives a legal id ("Corruption" -> "mypack:corruption") until the
        // user edits the id themselves, at which point the derivation stops.
        nameBox.setResponder(name -> {
            if (!idManuallyEdited) {
                idBox.setValue("mypack:" + name.toLowerCase().replaceAll("[^a-z0-9_]", "_"));
            }
        });
        idBox.setResponder(id -> {
            if (idBox.isFocused()) idManuallyEdited = true;
        });
        // NOTE drift: setResponder/isFocused spellings per old dialogs.
    }

    /** Creates a hinted text field and registers it as a widget. */
    private EditBox box(int x, int y, int w, String hint) {
        EditBox b = new EditBox(font, x, y, w, 18, Component.empty());
        b.setHint(Component.literal(hint));
        addRenderableWidget(b);
        return b;
    }

    /** Copies the selected template's range into the three number fields. */
    private void applyTemplate() {
        StatTemplates t = StatTemplates.values()[template];
        minBox.setValue(trim(t.min));
        maxBox.setValue(trim(t.max));
        defaultBox.setValue(trim(t.defaultValue));
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, px, py, pw, ph);
        g.text(font, Component.literal("CREATE STAT"), px + PanelStyle.GRID * 2, py + PanelStyle.GRID, PanelStyle.TEXT);
        g.text(font, Component.literal("Start simple. Every value can be changed later."),
                px + PanelStyle.GRID * 2, py + PanelStyle.GRID + 12, PanelStyle.TEXT_DIM);

        int fx = px + PanelStyle.GRID * 2;
        g.text(font, Component.literal("NAME"), fx, py + 30, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("RESOURCE ID"), fx, py + 70, PanelStyle.TEXT_DIM);
        if (!idManuallyEdited) {
            g.text(font, Component.literal("Generated from name"), fx, py + 102, PanelStyle.TEXT_DIM);
        }
        g.text(font, Component.literal("TEMPLATE"), fx, py + 116, PanelStyle.TEXT_DIM);
        StatTemplates t = StatTemplates.values()[template];
        PanelStyle.button(g, font, t.label + "  \u25BC", fx, py + 126, pw / 2 - PanelStyle.GRID * 3,
                PanelStyle.hit(mouseX, mouseY, fx, py + 126, pw / 2 - PanelStyle.GRID * 3, PanelStyle.CONTROL_H), false);
        g.text(font, Component.literal("MIN"), fx, py + 140 + 42, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("MAX"), fx + 64, py + 140 + 42, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("DEFAULT"), fx + 128, py + 140 + 42, PanelStyle.TEXT_DIM);

        // template preview panel (right half, per the book)
        int tx = px + pw / 2 + PanelStyle.GRID;
        int tw = pw / 2 - PanelStyle.GRID * 3;
        PanelStyle.inset(g, tx, py + 30, tw, 150);
        g.text(font, Component.literal(t.label.toUpperCase()), tx + PanelStyle.GRID, py + 38, PanelStyle.ACCENT);
        drawWrapped(g, t.description, tx + PanelStyle.GRID, py + 52, tw - PanelStyle.GRID * 2);
        g.text(font, Component.literal("RANGE   " + trim(t.min) + " - " + trim(t.max)), tx + PanelStyle.GRID, py + 100, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("HUD     " + (t.hudBar ? "BAR" : "HIDDEN")), tx + PanelStyle.GRID, py + 114, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("PRESET ONLY - NOT A LOCKED TYPE"), tx + PanelStyle.GRID, py + 164, PanelStyle.TEXT_DIM);

        PanelStyle.button(g, font, "CANCEL", px + PanelStyle.GRID * 2, py + ph - 32, 96,
                PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID * 2, py + ph - 32, 96, PanelStyle.CONTROL_H), false);
        PanelStyle.button(g, font, "CREATE STAT", px + pw - 120 - PanelStyle.GRID * 2, py + ph - 32, 120,
                PanelStyle.hit(mouseX, mouseY, px + pw - 120 - PanelStyle.GRID * 2, py + ph - 32, 120, PanelStyle.CONTROL_H), true);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    /** Greedy word wrap for the template description; good enough for a fixed-width panel. */
    private void drawWrapped(GuiGraphicsExtractor g, String text, int x, int y, int w) {
        // crude wrap: split on spaces into lines fitting w
        StringBuilder line = new StringBuilder();
        int ly = y;
        for (String word : text.split(" ")) {
            if (font.width(line + " " + word) > w) {
                g.text(font, Component.literal(line.toString()), x, ly, PanelStyle.TEXT_DIM);
                ly += 10;
                line = new StringBuilder(word);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        g.text(font, Component.literal(line.toString()), x, ly, PanelStyle.TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int fx = px + PanelStyle.GRID * 2;

        // The template control is a cycle button: each click advances to the next preset
        // and refills the range fields.
        if (PanelStyle.hit(mx, my, fx, py + 126, pw / 2 - PanelStyle.GRID * 3, PanelStyle.CONTROL_H)) {
            template = (template + 1) % StatTemplates.values().length;
            applyTemplate();
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
     * Builds the stat from the template, saves it, and adds it to the library.
     * The save goes to the server immediately, so the new stat exists as a real datapack
     * file even if the editor is closed straight afterwards.
     */
    private void create() {
        String statId = idBox.getValue().trim();
        if (!statId.contains(":") || workingSet.entries.stream().anyMatch(e -> e.statId.equals(statId))) {
            return;   // invalid or duplicate — slice 6 adds visible validation
        }
        double min = parse(minBox.getValue(), 0), max = parse(maxBox.getValue(), 100),
                def = parse(defaultBox.getValue(), 0);
        StatTemplates t = StatTemplates.values()[template];
        var json = t.build(nameBox.getValue().isEmpty() ? statId : nameBox.getValue(), min, max, def);

        ClientEditorNet.sendSave(statId, GSON.toJson(json));
        StatWorkingSet.Entry entry = new StatWorkingSet.Entry(statId, json);
        parent.onCreated(entry);
        Minecraft.getInstance().gui.setScreen(parent);
    }

    /** Lenient number parse: anything unreadable falls back to the template's value. */
    private static double parse(String s, double fallback) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return fallback; }
    }

    /** Editing must not pause a singleplayer world. */
    @Override
    public boolean isPauseScreen() { return false; }
}