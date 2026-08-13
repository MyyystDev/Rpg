package com.myyyst.myrpg.entities.client.editor;

import com.myyyst.myrpg.core.client.editor.PanelStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Create Custom Entity dialog — design book page 04. Name drives the ID
 * until the ID is edited by hand; templates add ordinary components.
 * All coordinates are computed once in init() and shared by render and
 * input, so the layout can never drift apart at small GUI heights.
 */
public class CreateEntityScreen extends Screen {

    private static final String[] MODELS = {"humanoid", "humanoid_slim", "zombie", "skeleton"};

    private final EntityBrowserScreen parent;
    private final EntityWorkingSet workingSet;

    private EditBox nameBox;
    private EditBox idBox;
    private boolean idEdited;
    private int templateIndex;
    private int modelIndex;
    private String error = "";

    // layout (single source of truth)
    private int px, py, pw, ph, leftW, fieldW;
    private int yName, yId, yHint, yTemplateLabel, yTemplate, yModelLabel, yModel;
    private int previewX, previewY, previewW, previewH;
    private int buttonY, cancelX, createX, cancelW, createW;

    public CreateEntityScreen(EntityBrowserScreen parent, EntityWorkingSet workingSet) {
        super(Component.literal("Create Custom Entity"));
        this.parent = parent;
        this.workingSet = workingSet;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 2 * PanelStyle.GRID, 520);
        ph = Math.min(height - 2 * PanelStyle.GRID, 320);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        leftW = pw / 2;
        fieldW = leftW - PanelStyle.GRID * 3;

        // compact left column — fits even at ph ~250
        yName = py + 50;
        yId = yName + 38;
        yHint = yId + 20;
        yTemplateLabel = yHint + 12;
        yTemplate = yTemplateLabel + 10;
        yModelLabel = yTemplate + PanelStyle.CONTROL_H + 8;
        yModel = yModelLabel + 10;

        // bottom action row (under everything, right-aligned)
        createW = 120;
        buttonY = py + ph - PanelStyle.CONTROL_H - PanelStyle.GRID;
        createX = px + pw - createW - PanelStyle.GRID * 2;

        // preview frame ends above the action row; CANCEL starts flush
        // with the frame's left edge
        previewX = px + leftW + PanelStyle.GRID;
        cancelX = previewX;
        cancelW = createX - PanelStyle.GRID - cancelX;
        previewY = py + 42;
        previewW = pw - leftW - PanelStyle.GRID * 3;
        previewH = buttonY - PanelStyle.GRID - previewY;

        String prevName = nameBox == null ? "" : nameBox.getValue();
        String prevId = idBox == null ? "" : idBox.getValue();

        nameBox = new EditBox(font, px + PanelStyle.GRID * 2, yName, fieldW, 18,
                Component.literal("Name"));
        nameBox.setValue(prevName);
        nameBox.setResponder(text -> {
            if (!idEdited) idBox.setValue("mypack:" + slug(text));
        });
        addRenderableWidget(nameBox);

        idBox = new EditBox(font, px + PanelStyle.GRID * 2, yId, fieldW, 18,
                Component.literal("Resource ID"));
        idBox.setValue(prevId);
        addRenderableWidget(idBox);
    }

    private static String slug(String name) {
        return name.toLowerCase().trim().replace(' ', '_').replaceAll("[^a-z0-9_/.-]", "");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, px, py, pw, ph);

        g.text(font, Component.literal("CREATE CUSTOM ENTITY"),
                px + PanelStyle.GRID * 2, py + PanelStyle.GRID, PanelStyle.TEXT);
        g.text(font, Component.literal("Choose a starting point. Everything remains editable."),
                px + PanelStyle.GRID * 2, py + PanelStyle.GRID + 12, PanelStyle.TEXT_DIM);

        int lx = px + PanelStyle.GRID * 2;
        g.text(font, Component.literal("NAME"), lx, yName - 10, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("RESOURCE ID"), lx, yId - 10, PanelStyle.TEXT_DIM);
        if (!idEdited) {
            g.text(font, Component.literal("Generated from name"), lx, yHint, PanelStyle.TEXT_DIM);
        }

        g.text(font, Component.literal("TEMPLATE"), lx, yTemplateLabel, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, EntityTemplates.NAMES[templateIndex], lx, yTemplate, fieldW,
                PanelStyle.hit(mouseX, mouseY, lx, yTemplate, fieldW, PanelStyle.CONTROL_H), false);

        g.text(font, Component.literal("MODEL"), lx, yModelLabel, PanelStyle.TEXT_DIM);
        PanelStyle.button(g, font, MODELS[modelIndex].toUpperCase(), lx, yModel, fieldW,
                PanelStyle.hit(mouseX, mouseY, lx, yModel, fieldW, PanelStyle.CONTROL_H), false);

        renderTemplatePreview(g);

        if (!error.isEmpty()) {
            g.text(font, Component.literal(error), lx,
                    buttonY + (PanelStyle.CONTROL_H - 8) / 2, PanelStyle.ERROR);
        }

        PanelStyle.button(g, font, "CANCEL", cancelX, buttonY, cancelW,
                PanelStyle.hit(mouseX, mouseY, cancelX, buttonY, cancelW, PanelStyle.CONTROL_H), false);
        PanelStyle.button(g, font, "CREATE ENTITY", createX, buttonY, createW,
                PanelStyle.hit(mouseX, mouseY, createX, buttonY, createW, PanelStyle.CONTROL_H), true);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void renderTemplatePreview(GuiGraphicsExtractor g) {
        PanelStyle.inset(g, previewX, previewY, previewW, previewH);
        g.text(font, Component.literal(EntityTemplates.NAMES[templateIndex] + " TEMPLATE"),
                previewX + 8, previewY + 8, PanelStyle.VALID);

        // mini humanoid
        int hx = previewX + previewW / 2 - 10, hy = previewY + 22;
        g.fill(hx + 4, hy, hx + 16, hy + 10, 0xFFB98A5F);
        g.fill(hx + 2, hy + 11, hx + 18, hy + 24, PanelStyle.ACCENT);
        g.fill(hx + 4, hy + 25, hx + 9, hy + 33, PanelStyle.PANEL_DARK);
        g.fill(hx + 11, hy + 25, hx + 16, hy + 33, PanelStyle.PANEL_DARK);

        // component list — clamped to the frame, overflow becomes "+N more"
        String[] components = EntityTemplates.components(EntityTemplates.NAMES[templateIndex]);
        int listTop = hy + 40;
        int available = previewY + previewH - 6 - listTop;
        int fit = Math.max(0, available / 11);
        int shown = components.length > fit ? Math.max(0, fit - 1) : components.length;
        int cy = listTop;
        for (int i = 0; i < shown; i++) {
            g.fill(previewX + 10, cy + 2, previewX + 14, cy + 6, PanelStyle.VALID);
            g.text(font, Component.literal(components[i]), previewX + 20, cy, PanelStyle.TEXT_DIM);
            cy += 11;
        }
        if (shown < components.length) {
            g.text(font, Component.literal("+" + (components.length - shown) + " more"),
                    previewX + 10, cy, PanelStyle.TEXT_DIM);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int lx = px + PanelStyle.GRID * 2;

        if (PanelStyle.hit(mx, my, lx, yId, fieldW, 18)) {
            idEdited = true;   // touching the ID field detaches it from the name
        }
        if (PanelStyle.hit(mx, my, lx, yTemplate, fieldW, PanelStyle.CONTROL_H)) {
            templateIndex = (templateIndex + 1) % EntityTemplates.NAMES.length;
            return true;
        }
        if (PanelStyle.hit(mx, my, lx, yModel, fieldW, PanelStyle.CONTROL_H)) {
            modelIndex = (modelIndex + 1) % MODELS.length;
            return true;
        }
        if (PanelStyle.hit(mx, my, cancelX, buttonY, cancelW, PanelStyle.CONTROL_H)) {
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        if (PanelStyle.hit(mx, my, createX, buttonY, createW, PanelStyle.CONTROL_H)) {
            create();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void create() {
        String id = idBox.getValue().trim();
        if (!id.contains(":") || Identifier.tryParse(id) == null) {
            error = "Invalid resource ID (use namespace:path)";
            return;
        }
        for (EntityWorkingSet.Entry existing : workingSet.entries) {
            if (existing.entityId.equals(id)) {
                error = "That ID already exists";
                return;
            }
        }
        String name = nameBox.getValue().isBlank() ? "New Entity" : nameBox.getValue().trim();
        EntityWorkingSet.Entry entry = new EntityWorkingSet.Entry(id,
                EntityTemplates.build(EntityTemplates.NAMES[templateIndex], name,
                        "myrpg_entities:" + MODELS[modelIndex]));
        entry.dirty = true;
        workingSet.entries.add(entry);
        parent.refresh();
        Minecraft.getInstance().gui.setScreen(new EntityEditorScreen(parent, entry));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
