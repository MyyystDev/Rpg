package com.myyyst.myrpg.entities.client.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.myyyst.myrpg.core.client.editor.JsonEdit;
import com.myyyst.myrpg.core.client.editor.PanelStyle;
import com.myyyst.myrpg.core.client.editor.TypedObjectListScreen;
import com.myyyst.myrpg.core.platform.Services;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import com.myyyst.myrpg.entities.network.EntitiesPayloads;
import com.myyyst.myrpg.entities.registry.RpgEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Component editor — design book pages 05-11. Left component nav, center
 * property forms bound straight to the working JsonObject, header with
 * UNSAVED / SPAWN / SAVE. AI shows a read-only priority list for now
 * (goal dialogs are the next slice); the live 3D preview panel follows.
 */
public class EntityEditorScreen extends Screen {

    private static final Gson GSON = new Gson();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] MODELS = {
            "myrpg_entities:humanoid", "myrpg_entities:humanoid_slim",
            "myrpg_entities:zombie", "myrpg_entities:skeleton"};
    private static final String[] COMBAT_TYPES = {"none", "melee", "ranged"};

    private enum Page {GENERAL, APPEARANCE, ATTRIBUTES, MOVEMENT, COMBAT, EQUIPMENT, AI, DROPS, ADVANCED}

    private final EntityBrowserScreen parent;
    private final EntityWorkingSet.Entry entry;
    private Page page = Page.GENERAL;

    private int px, py, pw, ph, navW, previewW;
    private int frameTop, frameBottom, wellX, wellY, wellW, wellH;
    private int prevX, prevY, prevH;
    private int listScroll, advScroll;
    private RpgEntity previewEntity;
    private String previewKey = "";
    private float previewYaw;
    private boolean draggingYaw;
    private java.util.List<EntityValidator.Issue> issues = new java.util.ArrayList<>();
    private int validateCooldown;
    private int jsonTopCached;
    private int navScroll;
    private boolean addPicking;
    private int attrScroll;
    private boolean attrPicking;
    private int attrPickScroll;

    public EntityEditorScreen(EntityBrowserScreen parent, EntityWorkingSet.Entry entry) {
        super(Component.literal("Entity Editor"));
        this.parent = parent;
        this.entry = entry;
        if (entry.json == null) entry.json = new JsonObject();
    }

    // ------------------------------------------------------------ layout

    @Override
    protected void init() {
        pw = Math.min(width - 2 * PanelStyle.GRID, 660);
        ph = Math.min(height - 2 * PanelStyle.GRID, 340);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        navW = 110;
        previewW = 104;

        frameTop = py + PanelStyle.GRID * 4 + 8;
        frameBottom = py + ph - PanelStyle.GRID;

        // frame is drawn 6px around the content, so pull the content in
        // 6px: the frame's right edge lands exactly on px + pw - GRID,
        // flush with the SAVE button and the header divider line
        prevX = px + pw - PanelStyle.GRID - previewW - 6;
        prevY = frameTop;
        prevH = frameBottom - frameTop;

        // page content sits symmetrically inside the well frame: fields are
        // placed at wellX + 8 with width wellW - 16, so these offsets give
        // equal 12px margins on both sides
        int wellFrameLeft = px + navW + PanelStyle.GRID;
        int wellFrameW = (prevX - PanelStyle.GRID) - wellFrameLeft - 4;
        wellX = wellFrameLeft + 4;
        wellY = frameTop + 8;
        wellW = wellFrameW - 8;
        wellH = frameBottom - frameTop - 16;

        buildPageWidgets();
    }

    private void setPage(Page newPage) {
        page = newPage;
        listScroll = 0;
        advScroll = 0;
        attrScroll = 0;
        attrPicking = false;
        attrPickScroll = 0;
        clearWidgets();
        buildPageWidgets();
    }

    private void buildPageWidgets() {
        int half = wellW / 2;
        switch (page) {
            case GENERAL -> {
                addFieldAt("display.name", "", wellX + 8, wellY + 14, wellW - 16);
                addFieldAt("__id", entry.entityId, wellX + 8, wellY + 54, wellW - 16);
                addDescriptionField(wellX + 8, wellY + 94, wellW - 16, 52);
            }
            case ADVANCED -> addTagsField(wellX + 8, wellY + 14, wellW - 16);
            case APPEARANCE -> {
                addFieldAt("appearance.texture", "", wellX + 8, wellY + 54, wellW - 16);
                addNumberField("appearance.scale", 1.0, wellX + 8, wellY + 94, 64);
            }
            case ATTRIBUTES -> {
                java.util.List<String> keys = attributeKeys();
                int rows = attrRowsVisible();
                for (int r = 0; r < rows; r++) {
                    int idx = attrScroll + r;
                    if (idx >= keys.size()) break;
                    addNumberField("attributes." + keys.get(idx), 0,
                            wellX + 20, attrRowY(r) + 17, 72);
                }
            }
            case COMBAT -> {
                String type = JsonEdit.getString(entry.json, "combat.type", "none");
                if (!type.equals("none")) {
                    addNumberField("combat.range", type.equals("ranged") ? 15 : 2.0,
                            wellX + 8, wellY + 54, 64);
                    addNumberField("combat.cooldown", type.equals("ranged") ? 30 : 20,
                            wellX + half + 8, wellY + 54, 64);
                }
                if (type.equals("ranged")) {
                    addFieldAt("combat.projectile", "minecraft:arrow", wellX + 8, wellY + 94, half - 16);
                    addNumberField("combat.projectile_speed", 1.6, wellX + half + 8, wellY + 94, 64);
                }
            }
            case EQUIPMENT -> {
                addFieldAt("equipment.mainhand", "", wellX + 8, wellY + 14, half - 22);
                addFieldAt("equipment.offhand", "", wellX + half + 8, wellY + 14, half - 22);
                addFieldAt("equipment.head", "", wellX + 8, wellY + 54, half - 22);
                addFieldAt("equipment.chest", "", wellX + half + 8, wellY + 54, half - 22);
                addFieldAt("equipment.legs", "", wellX + 8, wellY + 94, half - 22);
                addFieldAt("equipment.feet", "", wellX + half + 8, wellY + 94, half - 22);
            }
            case DROPS -> {
                addFieldAt("loot.loot_table", "", wellX + 8, wellY + 14, wellW - 16);
                addNumberField("loot.xp", 0, wellX + 8, wellY + 54, 64);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------ field factories

    private void addFieldAt(String path, String fallback, int x, int y, int w) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
        box.setValue(path.equals("__id") ? entry.entityId
                : JsonEdit.getString(entry.json, path, fallback));
        box.setResponder(text -> {
            if (path.equals("__id")) {
                entry.entityId = text;
            } else if (text.isEmpty()) {
                JsonEdit.remove(entry.json, path);
            } else {
                JsonEdit.set(entry.json, path, text);
            }
            entry.dirty = true;
        });
        addRenderableWidget(box);
    }

    private void addNumberField(String path, double fallback, int x, int y, int w) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
        box.setValue(trimNum(JsonEdit.getDouble(entry.json, path, fallback)));
        box.setResponder(text -> {
            try {
                JsonEdit.set(entry.json, path, Double.parseDouble(text.trim()));
                entry.dirty = true;
            } catch (NumberFormatException ignored) { }
        });
        addRenderableWidget(box);
    }

    private void addDescriptionField(int x, int y, int w, int h) {
        MultiLineEditBox box = MultiLineEditBox.builder()
                .setX(x).setY(y)
                .setPlaceholder(Component.literal("Description..."))
                .build(font, w, h, Component.literal("Description"));
        box.setValue(JsonEdit.getString(entry.json, "display.description", ""));
        box.setValueListener(text -> {
            if (text.isBlank()) {
                JsonEdit.remove(entry.json, "display.description");
            } else {
                JsonEdit.set(entry.json, "display.description", text);
            }
            entry.dirty = true;
        });
        addRenderableWidget(box);
    }

    private void addTagsField(int x, int y, int w) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
        StringBuilder sb = new StringBuilder();
        if (entry.json.has("tags")) {
            for (var t : entry.json.getAsJsonArray("tags")) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(t.getAsString());
            }
        }
        box.setValue(sb.toString());
        box.setResponder(text -> {
            if (text.isBlank()) {
                entry.json.remove("tags");
            } else {
                JsonArray tags = new JsonArray();
                for (String tag : text.split(",")) {
                    if (!tag.isBlank()) tags.add(tag.trim());
                }
                entry.json.add("tags", tags);
            }
            entry.dirty = true;
        });
        addRenderableWidget(box);
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

        int wellFrameW = (prevX - PanelStyle.GRID) - (px + navW + PanelStyle.GRID) - 4;
        PanelStyle.inset(g, px + navW + PanelStyle.GRID, frameTop, wellFrameW, frameBottom - frameTop);
        g.enableScissor(px + navW + PanelStyle.GRID + 1, frameTop + 1,
                px + navW + PanelStyle.GRID + wellFrameW - 1, frameBottom - 1);
        switch (page) {
            case GENERAL -> renderGeneral(g, mouseX, mouseY);
            case APPEARANCE -> renderAppearance(g, mouseX, mouseY);
            case ATTRIBUTES -> renderAttributes(g, mouseX, mouseY);
            case MOVEMENT -> renderMovement(g, mouseX, mouseY);
            case COMBAT -> renderCombat(g, mouseX, mouseY);
            case EQUIPMENT -> renderEquipment(g);
            case AI -> renderAi(g, mouseX, mouseY);
            case DROPS -> renderDrops(g);
            case ADVANCED -> renderAdvanced(g, mouseX, mouseY);
        }
        g.disableScissor();

        renderPreview(g, mouseX, mouseY);

        super.extractRenderState(g, mouseX, mouseY, delta);

        if (addPicking) renderAddPicker(g, mouseX, mouseY);
        if (attrPicking) renderAttrPicker(g, mouseX, mouseY);
    }

    private void renderPreview(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // the preview lives in its own frame, separate from the page well
        PanelStyle.inset(g, prevX - 6, frameTop, previewW + 12, frameBottom - frameTop);

        g.text(font, Component.literal("LIVE PREVIEW"), prevX + 4, prevY + 6, PanelStyle.EDITED);
        String model = JsonEdit.getString(entry.json, "appearance.model", MODELS[0]);

        // viewport
        int vx = prevX, vy = prevY + 20;
        int vw = previewW, vh = prevH - 20 - 40;
        PanelStyle.inset(g, vx, vy, vw, vh);

        if (minecraft == null || minecraft.level == null) {
            g.text(font, Component.literal("(no world)"), vx + 8, vy + 8, PanelStyle.TEXT_DIM);
            return;
        }
        if (previewEntity == null) {
            previewEntity = RpgEntityTypes.rpg_entity()
                    .create(minecraft.level, EntitySpawnReason.LOAD);
            if (previewEntity == null) return;
            // never added to a world, so no ID is ever assigned — the GUI
            // render path calls getId(), which throws at id 0.
            previewEntity.setId(-424242);
        }

        String texture = JsonEdit.getString(entry.json, "appearance.texture", "");
        double scale = JsonEdit.getDouble(entry.json, "appearance.scale", 1.0);
        String equipKey = entry.json.has("equipment") ? entry.json.get("equipment").toString() : "";
        String key = model + "|" + texture + "|" + scale + "|" + equipKey;
        if (!key.equals(previewKey)) {
            previewKey = key;
            previewEntity.applyPreview(model, texture, scale);
            applyPreviewEquipment(equipKey);
        }

        int size = (int) (Math.min((vw - 12) / 1.2, (vh - 12) / 2.4) / Math.max(0.6, scale));
        renderPreviewEntity(g, size, vx + 2, vy + 2, vx + vw - 2, vy + vh - 2);

        g.text(font, Component.literal("SCALE " + (int) Math.rint(scale * 100) + "%"),
                vx + 6, vy + vh - 12, PanelStyle.TEXT_DIM);

        // rotation slider
        int tx = sliderX(), tw = sliderW(), ty = sliderY();

        // beveled track with center groove
        PanelStyle.inset(g, tx, ty, tw, 6);
        // tick marks every 90 degrees
        for (int a = -180; a <= 180; a += 90) {
            int tickX = tx + 1 + (int) ((a + 180.0f) / 360.0f * (tw - 2));
            g.fill(tickX, ty - 3, tickX + 1, ty, a == 0 ? PanelStyle.TEXT_DIM : PanelStyle.PANEL_LIGHT);
        }
        // filled portion from center (0 deg) to the thumb
        int zeroX = tx + 1 + (tw - 2) / 2;
        int fillX = tx + 1 + (int) ((previewYaw + 180.0f) / 360.0f * (tw - 2));
        g.fill(Math.min(zeroX, fillX), ty + 2, Math.max(zeroX, fillX), ty + 4, PanelStyle.ACCENT);
        // raised thumb (mini button bevel)
        int thumbX = fillX - 4;
        boolean thumbHover = draggingYaw
                || PanelStyle.hit(mouseX, mouseY, thumbX, ty - 4, 8, 14);
        g.fill(thumbX, ty - 4, thumbX + 8, ty + 10, thumbHover ? PanelStyle.ROW_HOVER : PanelStyle.PANEL_BG);
        g.fill(thumbX, ty - 4, thumbX + 8, ty - 3, PanelStyle.PANEL_LIGHT);
        g.fill(thumbX, ty - 4, thumbX + 1, ty + 10, PanelStyle.PANEL_LIGHT);
        g.fill(thumbX, ty + 9, thumbX + 8, ty + 10, PanelStyle.PANEL_DARK);
        g.fill(thumbX + 7, ty - 4, thumbX + 8, ty + 10, PanelStyle.PANEL_DARK);

        // floating value above the thumb, clamped to the track
        String deg = (int) previewYaw + "\u00B0";
        int degX = Math.max(tx, Math.min(tx + tw - font.width(deg),
                fillX - font.width(deg) / 2));
        g.text(font, Component.literal(deg), degX, ty - 15,
                thumbHover ? PanelStyle.TEXT : PanelStyle.TEXT_DIM);
    }

    private int sliderX() { return prevX + 8; }
    private int sliderW() { return previewW - 16; }
    private int sliderY() { return prevY + prevH - 22; }

    private void setYawFromMouse(double mx) {
        float f = (float) (mx - sliderX()) / (float) (sliderW() - 2);
        float yaw = Math.max(-180.0f, Math.min(180.0f, f * 360.0f - 180.0f));
        // magnetic snap to the 45-degree points
        float nearest = Math.round(yaw / 45.0f) * 45.0f;
        previewYaw = Math.abs(yaw - nearest) <= 5.0f ? nearest : yaw;
    }

    /** Slider-driven variant of InventoryScreen's GUI entity render. */
    private void renderPreviewEntity(GuiGraphicsExtractor g, int size,
                                     int x0, int y0, int x1, int y1) {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super RpgEntity, ?> renderer = dispatcher.getRenderer(previewEntity);
        EntityRenderState state = renderer.createRenderState(previewEntity, 1.0f);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180.0f - previewYaw;
            living.yRot = 0.0f;   // head yaw is RELATIVE to body — nonzero doubles the spin
            living.xRot = 0.0f;
            living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
            living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
            living.scale = 1.0f;
        }
        Vector3f translation = new Vector3f(0.0f, state.boundingBoxHeight / 2.0f + 0.0625f, 0.0f);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        g.entity(state, size, translation, pose, null, x0, y0, x1, y1);
    }

    private void applyPreviewEquipment(String equipKey) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            previewEntity.setItemSlot(slot, ItemStack.EMPTY);
        }
        if (equipKey.isEmpty() || !entry.json.has("equipment")) return;
        JsonObject eq = entry.json.getAsJsonObject("equipment");
        previewEquip(eq, "mainhand", EquipmentSlot.MAINHAND);
        previewEquip(eq, "offhand", EquipmentSlot.OFFHAND);
        previewEquip(eq, "head", EquipmentSlot.HEAD);
        previewEquip(eq, "chest", EquipmentSlot.CHEST);
        previewEquip(eq, "legs", EquipmentSlot.LEGS);
        previewEquip(eq, "feet", EquipmentSlot.FEET);
    }

    private void previewEquip(JsonObject eq, String key, EquipmentSlot slot) {
        if (!eq.has(key)) return;
        Identifier id = Identifier.tryParse(eq.get(key).getAsString());
        Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
        if (item != null && item != Items.AIR) {
            previewEntity.setItemSlot(slot, new ItemStack(item));
        }
    }

    private void renderHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int hy = py + PanelStyle.GRID;
        PanelStyle.button(g, font, "<", px + PanelStyle.GRID, hy, 20,
                PanelStyle.hit(mouseX, mouseY, px + PanelStyle.GRID, hy, 20, PanelStyle.CONTROL_H), false);
        String name = entry.displayName().toUpperCase();
        g.text(font, Component.literal(name), px + PanelStyle.GRID + 28, hy + 8, PanelStyle.TEXT);
        int chipX = px + PanelStyle.GRID + 32 + font.width(name);
        if (entry.dirty) {
            PanelStyle.chip(g, font, "UNSAVED", chipX, hy + 6, PanelStyle.EDITED);
            chipX += font.width("UNSAVED") + 12;
        }
        if (!issues.isEmpty()) {
            boolean errors = EntityValidator.hasErrors(issues);
            PanelStyle.chip(g, font, issues.size() + (errors ? " ERROR" : " WARN"),
                    chipX, hy + 6, errors ? PanelStyle.ERROR : PanelStyle.EDITED);
        }
        PanelStyle.button(g, font, "SPAWN", px + pw - 64 * 2 - PanelStyle.GRID * 2, hy, 64,
                PanelStyle.hit(mouseX, mouseY, px + pw - 64 * 2 - PanelStyle.GRID * 2, hy, 64, PanelStyle.CONTROL_H), false);
        PanelStyle.button(g, font, "SAVE", px + pw - 64 - PanelStyle.GRID, hy, 64,
                PanelStyle.hit(mouseX, mouseY, px + pw - 64 - PanelStyle.GRID, hy, 64, PanelStyle.CONTROL_H), true);

        g.fill(px + PanelStyle.GRID, py + PanelStyle.GRID * 4 + 2,
                px + pw - PanelStyle.GRID, py + PanelStyle.GRID * 4 + 3, PanelStyle.PANEL_DARK);
        g.fill(px + PanelStyle.GRID, py + PanelStyle.GRID * 4 + 3,
                px + pw - PanelStyle.GRID, py + PanelStyle.GRID * 4 + 4, PanelStyle.PANEL_LIGHT);
    }

    // ---------------------------------------------------------- attributes page

    private static final int ATTR_ROW = 42;
    private static final int[] ATTR_COLORS = {
            PanelStyle.ERROR, 0xFF5B9BD5, PanelStyle.EDITED,
            0xFF57B3A0, PanelStyle.VALID, PanelStyle.ACCENT};

    private JsonObject attributesObj() {
        if (!entry.json.has("attributes") || !entry.json.get("attributes").isJsonObject()) {
            entry.json.add("attributes", new JsonObject());
        }
        return entry.json.getAsJsonObject("attributes");
    }

    private java.util.List<String> attributeKeys() {
        return new java.util.ArrayList<>(attributesObj().keySet());
    }

    private int attrRowsVisible() {
        return Math.max(1, (wellH - 34) / ATTR_ROW);
    }

    private int attrRowY(int visibleIndex) {
        return wellY + 30 + visibleIndex * ATTR_ROW;
    }

    private static String prettyAttribute(String key) {
        String path = key.contains(":") ? key.split(":", 2)[1] : key;
        return path.replace('_', ' ').replace('.', ' ').toUpperCase();
    }

    /** Registry attributes not yet on this entity (minecraft first, sorted). */
    private java.util.List<Identifier> missingAttributes() {
        java.util.Set<String> present = attributesObj().keySet();
        return BuiltInRegistries.ATTRIBUTE.keySet().stream()
                .filter(id -> !present.contains(id.toString()))
                .sorted((a, b) -> {
                    boolean ma = a.getNamespace().equals("minecraft");
                    boolean mb = b.getNamespace().equals("minecraft");
                    if (ma != mb) return ma ? -1 : 1;
                    return a.toString().compareTo(b.toString());
                })
                .toList();
    }

    private void addAttribute(Identifier id) {
        double fallback = BuiltInRegistries.ATTRIBUTE.get(id)
                .map(h -> h.value().getDefaultValue()).orElse(1.0);
        attributesObj().addProperty(id.toString(), fallback);
        entry.dirty = true;
        attrScroll = Math.max(0, attributeKeys().size() - attrRowsVisible());
        setPageKeepAttr();
    }

    /** Rebuild widgets without resetting attribute scroll. */
    private void setPageKeepAttr() {
        int keep = attrScroll;
        clearWidgets();
        buildPageWidgets();
        attrScroll = keep;
    }

    // ---------------------------------------------------------- components

    /** JSON section backing each optional component page; null = always shown. */
    private static String sectionFor(Page p) {
        return switch (p) {
            case APPEARANCE -> "appearance";
            case ATTRIBUTES -> "attributes";
            case MOVEMENT -> "movement";
            case COMBAT -> "combat";
            case EQUIPMENT -> "equipment";
            case AI -> "ai";
            case DROPS -> "loot";
            default -> null;
        };
    }

    private boolean hasComponent(Page p) {
        String key = sectionFor(p);
        if (key == null) return true;
        if (p == Page.AI) return entry.json.has("ai") || entry.json.has("targeting");
        return entry.json.has(key);
    }

    private java.util.List<Page> presentPages() {
        java.util.List<Page> pages = new java.util.ArrayList<>();
        for (Page p : Page.values()) if (hasComponent(p)) pages.add(p);
        return pages;
    }

    private java.util.List<Page> missingPages() {
        java.util.List<Page> pages = new java.util.ArrayList<>();
        for (Page p : Page.values()) if (!hasComponent(p)) pages.add(p);
        return pages;
    }

    private void addComponent(Page p) {
        String key = sectionFor(p);
        if (key != null && !entry.json.has(key)) {
            if (p == Page.AI) {
                entry.json.add("ai", new JsonArray());
            } else {
                entry.json.add(key, new JsonObject());
            }
            entry.dirty = true;
        }
        setPage(p);
    }

    // nav card geometry (shared by render + input)
    private static final int NAV_ROW = 26;

    private int navX() { return px + PanelStyle.GRID; }
    private int navTop() { return frameTop + 6; }
    private int navDividerY() { return frameBottom - PanelStyle.CONTROL_H - 14; }
    private int navAddY() { return frameBottom - PanelStyle.CONTROL_H - 6; }
    private int navVisibleRows() { return Math.max(1, (navDividerY() - 4 - navTop()) / NAV_ROW); }

    private void renderNav(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        java.util.List<Page> pages = presentPages();
        int rows = navVisibleRows();
        int maxScroll = Math.max(0, pages.size() - rows);
        navScroll = Math.min(navScroll, maxScroll);

        PanelStyle.inset(g, navX(), frameTop, navW - PanelStyle.GRID, frameBottom - frameTop);

        for (int r = 0; r < rows; r++) {
            int idx = navScroll + r;
            if (idx >= pages.size()) break;
            Page p = pages.get(idx);
            int ny = navTop() + r * NAV_ROW;
            boolean selected = p == page;
            PanelStyle.button(g, font, p.name(),
                    navX() + 4, ny, navW - PanelStyle.GRID - 14,
                    PanelStyle.hit(mouseX, mouseY, navX() + 4, ny,
                            navW - PanelStyle.GRID - 14, PanelStyle.CONTROL_H),
                    selected);
        }
        PanelStyle.scrollbar(g, navX() + navW - PanelStyle.GRID - 6, navTop(),
                navDividerY() - 4 - navTop(), pages.size(), rows, navScroll);

        // divider + pinned add button
        g.fill(navX() + 4, navDividerY(), navX() + navW - PanelStyle.GRID - 4,
                navDividerY() + 1, PanelStyle.PANEL_LIGHT);
        PanelStyle.button(g, font, "+ ADD", navX() + 4, navAddY(),
                navW - PanelStyle.GRID - 8,
                PanelStyle.hit(mouseX, mouseY, navX() + 4, navAddY(),
                        navW - PanelStyle.GRID - 8, PanelStyle.CONTROL_H),
                0xFF57B36A, 0xFF6FCB82, 0xFF9FE0AC);
    }

    private void renderAddPicker(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        java.util.List<Page> missing = missingPages();
        int w = 160, h = 30 + Math.max(1, missing.size()) * 18 + 8;
        int cx = (width - w) / 2, cy = (height - h) / 2;
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, cx, cy, w, h);
        g.text(font, Component.literal("ADD COMPONENT"), cx + 8, cy + 8, PanelStyle.TEXT);
        if (missing.isEmpty()) {
            g.text(font, Component.literal("All components added"), cx + 8, cy + 28, PanelStyle.TEXT_DIM);
        }
        for (int i = 0; i < missing.size(); i++) {
            int iy = cy + 26 + i * 18;
            boolean hovered = PanelStyle.hit(mouseX, mouseY, cx + 4, iy, w - 8, 18);
            if (hovered) g.fill(cx + 4, iy, cx + w - 4, iy + 18, PanelStyle.ROW_HOVER);
            g.fill(cx + 8, iy + 7, cx + 12, iy + 11, PanelStyle.VALID);
            g.text(font, Component.literal(missing.get(i).name()), cx + 18, iy + 5, PanelStyle.TEXT);
        }
    }

    private void renderGeneral(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("DISPLAY NAME"), wellX + 8, wellY + 4, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("RESOURCE ID"), wellX + 8, wellY + 44, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("DESCRIPTION"), wellX + 8, wellY + 84, PanelStyle.TEXT_DIM);
        boolean visible = JsonEdit.getBool(entry.json, "display.name_visible", true);
        renderCheckbox(g, mouseX, mouseY, "Visible nameplate", visible, wellX + 8, wellY + 156);
    }

    private void renderAppearance(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("MODEL"), wellX + 8, wellY + 4, PanelStyle.TEXT_DIM);
        String model = JsonEdit.getString(entry.json, "appearance.model", MODELS[0]);
        String shortName = model.contains(":") ? model.split(":", 2)[1] : model;
        PanelStyle.button(g, font, shortName.toUpperCase(), wellX + 8, wellY + 14, 140,
                PanelStyle.hit(mouseX, mouseY, wellX + 8, wellY + 14, 140, PanelStyle.CONTROL_H), false);
        g.text(font, Component.literal("TEXTURE (blank = model default)"),
                wellX + 8, wellY + 44, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("SCALE"), wellX + 8, wellY + 84, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("Scale also grows the hitbox (vanilla scale attribute)."),
                wellX + 88, wellY + 99, PanelStyle.TEXT_DIM);
    }

    private void renderAttributes(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("ATTRIBUTES"), wellX + 8, wellY + 6, PanelStyle.TEXT_DIM);
        int addW = 104;
        PanelStyle.button(g, font, "+ ADD ATTRIBUTE", wellX + wellW - addW - 8, wellY, addW,
                PanelStyle.hit(mouseX, mouseY, wellX + wellW - addW - 8, wellY, addW, PanelStyle.CONTROL_H), true);

        java.util.List<String> keys = attributeKeys();
        if (keys.isEmpty()) {
            g.text(font, Component.literal("No attributes set — base defaults apply."),
                    wellX + 8, wellY + 40, PanelStyle.TEXT_DIM);
            return;
        }
        int rows = attrRowsVisible();
        for (int r = 0; r < rows; r++) {
            int idx = attrScroll + r;
            if (idx >= keys.size()) break;
            String key = keys.get(idx);
            int ry = attrRowY(r);
            int rx = wellX + 8;
            int rw = wellW - 16;
            int rh = ATTR_ROW - 6;
            boolean hovered = PanelStyle.hit(mouseX, mouseY, rx, ry, rw, rh);

            // raised beveled card, same language as panels/buttons
            g.fill(rx, ry, rx + rw, ry + rh, hovered ? PanelStyle.ROW_HOVER : PanelStyle.ROW_BG);
            g.fill(rx, ry, rx + rw, ry + 1, PanelStyle.PANEL_LIGHT);
            g.fill(rx, ry, rx + 1, ry + rh, PanelStyle.PANEL_LIGHT);
            g.fill(rx, ry + rh - 1, rx + rw, ry + rh, PanelStyle.PANEL_DARK);
            g.fill(rx + rw - 1, ry, rx + rw, ry + rh, PanelStyle.PANEL_DARK);
            // accent bar inset within the bevel
            g.fill(rx + 1, ry + 1, rx + 4, ry + rh - 1, ATTR_COLORS[idx % ATTR_COLORS.length]);

            // line 1: full-width name (only the X shares this line)
            String label = prettyAttribute(key);
            if (font.width(label) > rw - 40) label = font.plainSubstrByWidth(label, rw - 48) + "…";
            g.text(font, Component.literal(label), wellX + 20, ry + 6, PanelStyle.TEXT);
            // line 2 holds the value EditBox (widget); X sits top-right
            boolean xHover = PanelStyle.hit(mouseX, mouseY, rx + rw - 26, ry + 4, 14, 14);
            g.text(font, Component.literal("X"), rx + rw - 22, ry + 6,
                    xHover ? PanelStyle.ERROR : PanelStyle.TEXT_DIM);
        }
        PanelStyle.scrollbar(g, wellX + wellW - 4, wellY + 30, wellH - 34,
                keys.size(), rows, attrScroll);
    }

    private void renderAttrPicker(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        java.util.List<Identifier> missing = missingAttributes();
        int w = 220, maxRows = 10;
        int shown = Math.min(missing.size(), maxRows);
        int h = 30 + Math.max(1, shown) * 14 + 8;
        int cx = (width - w) / 2, cy = (height - h) / 2;
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, cx, cy, w, h);
        g.text(font, Component.literal("ADD ATTRIBUTE"), cx + 8, cy + 8, PanelStyle.TEXT);
        for (int i = 0; i < shown; i++) {
            int idx = attrPickScroll + i;
            if (idx >= missing.size()) break;
            int iy = cy + 26 + i * 14;
            boolean hovered = PanelStyle.hit(mouseX, mouseY, cx + 4, iy, w - 8, 14);
            if (hovered) g.fill(cx + 4, iy, cx + w - 4, iy + 14, PanelStyle.ROW_HOVER);
            g.text(font, Component.literal(missing.get(idx).toString()), cx + 10, iy + 3,
                    hovered ? PanelStyle.TEXT : PanelStyle.TEXT_DIM);
        }
        PanelStyle.scrollbar(g, cx + w - 8, cy + 26, shown * 14,
                missing.size(), maxRows, attrPickScroll);
    }

    private void renderMovement(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        boolean swim = JsonEdit.getBool(entry.json, "movement.can_swim", true);
        renderCheckbox(g, mouseX, mouseY, "Can swim (float in water)", swim, wellX + 8, wellY + 14);
        boolean doors = JsonEdit.getBool(entry.json, "movement.can_open_doors", false);
        renderCheckbox(g, mouseX, mouseY, "Can open doors", doors, wellX + 8, wellY + 34);
    }

    private void renderCombat(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int half = wellW / 2;
        g.text(font, Component.literal("TYPE"), wellX + 8, wellY + 4, PanelStyle.TEXT_DIM);
        String type = JsonEdit.getString(entry.json, "combat.type", "none");
        PanelStyle.button(g, font, type.toUpperCase(), wellX + 8, wellY + 14, 100,
                PanelStyle.hit(mouseX, mouseY, wellX + 8, wellY + 14, 100, PanelStyle.CONTROL_H), false);
        if (!type.equals("none")) {
            g.text(font, Component.literal("RANGE"), wellX + 8, wellY + 44, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("COOLDOWN (ticks)"), wellX + half + 8, wellY + 44, PanelStyle.TEXT_DIM);
        }
        if (type.equals("ranged")) {
            g.text(font, Component.literal("PROJECTILE"), wellX + 8, wellY + 84, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal("PROJECTILE SPEED"), wellX + half + 8, wellY + 84, PanelStyle.TEXT_DIM);
        }
        if (type.equals("none")) {
            g.text(font, Component.literal("No combat — this entity never auto-attacks."),
                    wellX + 8, wellY + 48, PanelStyle.TEXT_DIM);
        }
    }

    private void renderEquipment(GuiGraphicsExtractor g) {
        int half = wellW / 2;
        String[][] slots = {
                {"MAIN HAND", "mainhand"}, {"OFF HAND", "offhand"},
                {"HEAD", "head"}, {"CHEST", "chest"},
                {"LEGS", "legs"}, {"FEET", "feet"}};
        for (int i = 0; i < slots.length; i++) {
            int col = i % 2, row = i / 2;
            int x = wellX + 8 + col * half;
            int labelY = wellY + 4 + row * 40;
            g.text(font, Component.literal(slots[i][0]), x, labelY, PanelStyle.TEXT_DIM);
            String value = JsonEdit.getString(entry.json, "equipment." + slots[i][1], "");
            if (!value.isEmpty()) {
                int dot = EntityValidator.itemExists(value) ? PanelStyle.VALID : PanelStyle.ERROR;
                g.fill(x + half - 18, labelY + 14, x + half - 12, labelY + 20, dot);
            }
        }
        g.text(font, Component.literal("Item IDs, e.g. minecraft:iron_sword — live in the preview."),
                wellX + 8, wellY + 134, PanelStyle.TEXT_DIM);
    }

    private void renderAi(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("AI GOALS"), wellX + 8, wellY + 4, PanelStyle.TEXT);
        PanelStyle.button(g, font, "EDIT GOALS", wellX + wellW - 96, wellY, 88,
                PanelStyle.hit(mouseX, mouseY, wellX + wellW - 96, wellY, 88, PanelStyle.CONTROL_H), true);
        int y = wellY + 28;
        y = renderGoalList(g, entry.json, "ai", y);
        g.text(font, Component.literal("TARGETING"), wellX + 8, y + 10, PanelStyle.TEXT);
        PanelStyle.button(g, font, "EDIT TARGETING", wellX + wellW - 108, y + 4, 100,
                PanelStyle.hit(mouseX, mouseY, wellX + wellW - 108, y + 4, 100, PanelStyle.CONTROL_H), true);
        targetingButtonY = y + 4;
        renderGoalList(g, entry.json, "targeting", y + 32);
    }

    private int targetingButtonY = -1;

    private com.google.gson.JsonArray arr(String key) {
        if (!entry.json.has(key) || !entry.json.get(key).isJsonArray()) {
            entry.json.add(key, new JsonArray());
        }
        return entry.json.getAsJsonArray(key);
    }

    private int renderGoalList(GuiGraphicsExtractor g, JsonObject json, String key, int y) {
        if (!json.has(key) || !json.get(key).isJsonArray() || json.getAsJsonArray(key).isEmpty()) {
            g.text(font, Component.literal("  (none)"), wellX + 8, y, PanelStyle.TEXT_DIM);
            return y + 12;
        }
        List<JsonObject> goals = new ArrayList<>();
        for (var e : json.getAsJsonArray(key)) {
            if (e.isJsonObject()) goals.add(e.getAsJsonObject());
        }
        goals.sort((a, b) -> Integer.compare(
                a.has("priority") ? a.get("priority").getAsInt() : 5,
                b.has("priority") ? b.get("priority").getAsInt() : 5));
        for (JsonObject goal : goals) {
            String type = goal.has("type") ? goal.get("type").getAsString() : "?";
            type = type.contains(":") ? type.split(":", 2)[1] : type;
            int priority = goal.has("priority") ? goal.get("priority").getAsInt() : 5;
            g.fill(wellX + 8, y, wellX + 22, y + 10, PanelStyle.INSET_BG);
            g.text(font, Component.literal(String.valueOf(priority)), wellX + 12, y + 1, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal(type.toUpperCase().replace('_', ' ')),
                    wellX + 28, y + 1, PanelStyle.TEXT);
            y += 13;
        }
        return y;
    }

    private void renderDrops(GuiGraphicsExtractor g) {
        g.text(font, Component.literal("LOOT TABLE (blank = none)"), wellX + 8, wellY + 4, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("XP"), wellX + 8, wellY + 44, PanelStyle.TEXT_DIM);
    }

    private void renderAdvanced(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("TAGS (comma-separated)"), wellX + 8, wellY + 4, PanelStyle.TEXT_DIM);
        boolean despawn = JsonEdit.getBool(entry.json, "persistence.despawn", false);
        renderCheckbox(g, mouseX, mouseY, "Can despawn when far away", despawn, wellX + 8, wellY + 40);

        int vy = wellY + 60;
        g.text(font, Component.literal("VALIDATION"), wellX + 8, vy, PanelStyle.TEXT_DIM);
        vy += 12;
        if (issues.isEmpty()) {
            g.text(font, Component.literal("  No problems found"), wellX + 8, vy, PanelStyle.VALID);
            vy += 12;
        } else {
            int shown = Math.min(issues.size(), 4);
            for (int i = 0; i < shown; i++) {
                EntityValidator.Issue issue = issues.get(i);
                int color = issue.level() == EntityValidator.Level.ERROR
                        ? PanelStyle.ERROR : PanelStyle.EDITED;
                g.fill(wellX + 8, vy + 2, wellX + 12, vy + 6, color);
                String msg = issue.message();
                if (font.width(msg) > wellW - 32) msg = font.plainSubstrByWidth(msg, wellW - 40) + "…";
                g.text(font, Component.literal(msg), wellX + 16, vy, color);
                vy += 11;
            }
            if (issues.size() > 4) {
                g.text(font, Component.literal("  +" + (issues.size() - 4) + " more"),
                        wellX + 8, vy, PanelStyle.TEXT_DIM);
                vy += 11;
            }
        }

        g.text(font, Component.literal("JSON"), wellX + 8, vy + 4, PanelStyle.TEXT_DIM);
        String[] lines = PRETTY.toJson(entry.json).split("\n");
        int top = vy + 16;
        jsonTopCached = top;
        int maxLines = (wellY + wellH - top - 4) / 10;
        for (int i = 0; i < maxLines; i++) {
            int li = advScroll + i;
            if (li >= lines.length) break;
            String line = lines[li];
            if (font.width(line) > wellW - 24) {
                line = font.plainSubstrByWidth(line, wellW - 32) + "…";
            }
            g.text(font, Component.literal(line), wellX + 8, top + i * 10, PanelStyle.TEXT_DIM);
        }
        PanelStyle.scrollbar(g, wellX + wellW - 8, top, wellY + wellH - top - 4,
                lines.length, maxLines, advScroll);
    }

    private void renderCheckbox(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                String label, boolean value, int x, int y) {
        PanelStyle.inset(g, x, y, 12, 12);
        if (value) g.fill(x + 3, y + 3, x + 9, y + 9, PanelStyle.VALID);
        g.text(font, Component.literal(label), x + 18, y + 2,
                PanelStyle.hit(mouseX, mouseY, x, y, 12 + 6 + font.width(label), 12)
                        ? PanelStyle.TEXT : PanelStyle.TEXT_DIM);
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int hy = py + PanelStyle.GRID;

        if (PanelStyle.hit(mx, my, px + PanelStyle.GRID, hy, 20, PanelStyle.CONTROL_H)) {
            parent.refresh();
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        if (PanelStyle.hit(mx, my, px + pw - 64 * 2 - PanelStyle.GRID * 2, hy, 64, PanelStyle.CONTROL_H)) {
            Services.NETWORK.sendToServer(new EntitiesPayloads.SpawnEntity(entry.entityId));
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal(entry.dirty
                        ? "Spawning last SAVED version of " + entry.entityId
                        : "Spawning " + entry.entityId));
            }
            return true;
        }
        if (PanelStyle.hit(mx, my, px + pw - 64 - PanelStyle.GRID, hy, 64, PanelStyle.CONTROL_H)) {
            save();
            return true;
        }

        // rotation slider (double-click resets to front-facing)
        if (PanelStyle.hit(mx, my, sliderX() - 2, sliderY() - 8, sliderW() + 4, 20)) {
            if (doubleClick) {
                previewYaw = 0.0f;
            } else {
                draggingYaw = true;
                setYawFromMouse(mx);
            }
            return true;
        }

        // attribute picker eats all clicks while open
        if (attrPicking) {
            java.util.List<Identifier> missing = missingAttributes();
            int w = 220, maxRows = 10;
            int shown = Math.min(missing.size(), maxRows);
            int h = 30 + Math.max(1, shown) * 14 + 8;
            int cx = (width - w) / 2, cy = (height - h) / 2;
            for (int i = 0; i < shown; i++) {
                int idx = attrPickScroll + i;
                if (idx >= missing.size()) break;
                int iy = cy + 26 + i * 14;
                if (PanelStyle.hit(mx, my, cx + 4, iy, w - 8, 14)) {
                    attrPicking = false;
                    addAttribute(missing.get(idx));
                    return true;
                }
            }
            attrPicking = false;
            return true;
        }

        // add-component picker eats all clicks while open
        if (addPicking) {
            java.util.List<Page> missing = missingPages();
            int w = 160, h = 30 + Math.max(1, missing.size()) * 18 + 8;
            int cx = (width - w) / 2, cy = (height - h) / 2;
            for (int i = 0; i < missing.size(); i++) {
                int iy = cy + 26 + i * 18;
                if (PanelStyle.hit(mx, my, cx + 4, iy, w - 8, 18)) {
                    addPicking = false;
                    addComponent(missing.get(i));
                    return true;
                }
            }
            addPicking = false;   // click anywhere else closes
            return true;
        }

        // nav cards
        java.util.List<Page> pages = presentPages();
        int rows = navVisibleRows();
        for (int r = 0; r < rows; r++) {
            int idx = navScroll + r;
            if (idx >= pages.size()) break;
            int ny = navTop() + r * NAV_ROW;
            if (PanelStyle.hit(mx, my, navX() + 4, ny,
                    navW - PanelStyle.GRID - 14, PanelStyle.CONTROL_H)) {
                setPage(pages.get(idx));
                return true;
            }
        }
        if (PanelStyle.hit(mx, my, navX() + 4, navAddY(),
                navW - PanelStyle.GRID - 8, PanelStyle.CONTROL_H)) {
            addPicking = true;
            return true;
        }

        // page-specific toggles
        switch (page) {
            case GENERAL -> {
                if (PanelStyle.hit(mx, my, wellX + 8, wellY + 156, 160, 12)) {
                    toggleBool("display.name_visible", true);
                    return true;
                }
            }
            case APPEARANCE -> {
                if (PanelStyle.hit(mx, my, wellX + 8, wellY + 14, 140, PanelStyle.CONTROL_H)) {
                    String model = JsonEdit.getString(entry.json, "appearance.model", MODELS[0]);
                    int i = 0;
                    for (int m = 0; m < MODELS.length; m++) if (MODELS[m].equals(model)) i = m;
                    JsonEdit.set(entry.json, "appearance.model", MODELS[(i + 1) % MODELS.length]);
                    entry.dirty = true;
                    return true;
                }
            }
            case MOVEMENT -> {
                if (PanelStyle.hit(mx, my, wellX + 8, wellY + 14, 180, 12)) {
                    toggleBool("movement.can_swim", true);
                    return true;
                }
                if (PanelStyle.hit(mx, my, wellX + 8, wellY + 34, 180, 12)) {
                    toggleBool("movement.can_open_doors", false);
                    return true;
                }
            }
            case COMBAT -> {
                if (PanelStyle.hit(mx, my, wellX + 8, wellY + 14, 100, PanelStyle.CONTROL_H)) {
                    String type = JsonEdit.getString(entry.json, "combat.type", "none");
                    int i = 0;
                    for (int t = 0; t < COMBAT_TYPES.length; t++) if (COMBAT_TYPES[t].equals(type)) i = t;
                    JsonEdit.set(entry.json, "combat.type", COMBAT_TYPES[(i + 1) % COMBAT_TYPES.length]);
                    entry.dirty = true;
                    setPage(Page.COMBAT);   // rebuild the type-dependent fields
                    return true;
                }
            }
            case AI -> {
                if (PanelStyle.hit(mx, my, wellX + wellW - 96, wellY, 88, PanelStyle.CONTROL_H)) {
                    Minecraft.getInstance().gui.setScreen(new TypedObjectListScreen(
                            this, "AI GOALS", arr("ai"), GoalSchemas.goals(),
                            () -> entry.dirty = true));
                    return true;
                }
                if (targetingButtonY >= 0 && PanelStyle.hit(mx, my,
                        wellX + wellW - 108, targetingButtonY, 100, PanelStyle.CONTROL_H)) {
                    Minecraft.getInstance().gui.setScreen(new TypedObjectListScreen(
                            this, "TARGETING", arr("targeting"), GoalSchemas.targets(),
                            () -> entry.dirty = true));
                    return true;
                }
            }
            case ADVANCED -> {
                if (PanelStyle.hit(mx, my, wellX + 8, wellY + 40, 200, 12)) {
                    toggleBool("persistence.despawn", false);
                    return true;
                }
            }
            default -> { }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void toggleBool(String path, boolean fallback) {
        JsonEdit.set(entry.json, path, !JsonEdit.getBool(entry.json, path, fallback));
        entry.dirty = true;
    }

    @Override
    public void tick() {
        if (--validateCooldown <= 0) {
            validateCooldown = 20;
            issues = EntityValidator.validate(entry);
        }
    }

    private void save() {
        issues = EntityValidator.validate(entry);
        if (EntityValidator.hasErrors(issues)) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("[editor] Fix errors first: "
                        + issues.stream().filter(i -> i.level() == EntityValidator.Level.ERROR)
                                .findFirst().map(EntityValidator.Issue::message).orElse("")));
            }
            setPage(Page.ADVANCED);   // show the validation list
            return;
        }
        Identifier id = Identifier.tryParse(entry.entityId);
        if (id == null || !entry.entityId.contains(":")) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal(
                        "[editor] Invalid resource ID: " + entry.entityId));
            }
            return;
        }
        Services.NETWORK.sendToServer(new EntitiesPayloads.SaveEntity(
                entry.entityId, GSON.toJson(entry.json)));
        entry.dirty = false;   // server replies with saved/failed feedback in chat
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingYaw) {
            setYawFromMouse(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingYaw) {
            draggingYaw = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (attrPicking) {
            int max = Math.max(0, missingAttributes().size() - 10);
            attrPickScroll = Math.max(0, Math.min(max,
                    attrPickScroll - (int) Math.signum(vertical)));
            return true;
        }
        if (page == Page.ATTRIBUTES && PanelStyle.hit(mouseX, mouseY,
                wellX, wellY + 28, wellW, wellH - 28)) {
            int max = Math.max(0, attributeKeys().size() - attrRowsVisible());
            attrScroll = Math.max(0, Math.min(max, attrScroll - (int) Math.signum(vertical)));
            setPageKeepAttr();
            return true;
        }
        if (PanelStyle.hit(mouseX, mouseY, prevX - 6, frameTop, previewW + 12, frameBottom - frameTop)) {
            previewYaw += (float) Math.signum(vertical) * 15.0f;
            if (previewYaw > 180.0f) previewYaw -= 360.0f;
            if (previewYaw < -180.0f) previewYaw += 360.0f;
            return true;
        }
        if (PanelStyle.hit(mouseX, mouseY, navX(), frameTop, navW - PanelStyle.GRID, frameBottom - frameTop)) {
            int max = Math.max(0, presentPages().size() - navVisibleRows());
            navScroll = Math.max(0, Math.min(max, navScroll - (int) Math.signum(vertical)));
            return true;
        }
        if (page == Page.ADVANCED) {
            String[] lines = PRETTY.toJson(entry.json).split("\n");
            int top = jsonTopCached > 0 ? jsonTopCached : wellY + 44;
            int maxLines = (wellY + wellH - top - 4) / 10;
            int max = Math.max(0, lines.length - maxLines);
            advScroll = Math.max(0, Math.min(max, advScroll - (int) Math.signum(vertical) * 3));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
