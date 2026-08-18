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
 *
 * <p>The richest editor of the three. What sets it apart from the stat and effect editors:</p>
 * <ul>
 *   <li>a live 3D preview - an off-world {@code RpgEntity} rebuilt whenever the appearance
 *       fields change, so the author sees the real model, texture, scale and gear;</li>
 *   <li>a component model: the nav column lists only the sections the definition actually
 *       has, and "+ ADD COMPONENT" adds a new one, matching the JSON's optional blocks.</li>
 * </ul>
 *
 * <p>As in the other editors, every control writes straight into {@code entry.json}
 * and saving is an explicit action.</p>
 */
public class EntityEditorScreen extends Screen {

    /** Compact form, used for what goes over the wire. */
    private static final Gson GSON = new Gson();
    /** Indented form, used for the raw-JSON view on the ADVANCED page. */
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    // Allowed values for the cycle controls; these mirror what the codecs accept.
    private static final String[] MODELS = {
            "myrpg_entities:humanoid", "myrpg_entities:humanoid_slim",
            "myrpg_entities:zombie", "myrpg_entities:skeleton"};
    private static final String[] COMBAT_TYPES = {"none", "melee", "ranged", "hybrid"};
    private static final String[] MOVEMENT_TYPES = {"ground", "stationary"};

    /** One page per optional component of the definition, plus GENERAL and ADVANCED. */
    private enum Page {GENERAL, APPEARANCE, ATTRIBUTES, MOVEMENT, COMBAT, EQUIPMENT, AI, DROPS, ADVANCED}

    private final EntityBrowserScreen parent;
    /** The entity being edited; its {@code json} is mutated directly. */
    private final EntityWorkingSet.Entry entry;
    private Page page = Page.GENERAL;

    // layout — all computed in init(), so the screen re-centres on resize
    private int px, py, pw, ph, navW, previewW;
    private int frameTop, frameBottom, wellX, wellY, wellW, wellH;
    private int prevX, prevY, prevH;
    private int listScroll, advScroll;
    /** Client-side, never-added entity used purely to draw the preview. */
    private RpgEntity previewEntity;
    /** Fingerprint of the appearance fields; a change here means the preview must be rebuilt. */
    private String previewKey = "";
    /** Preview rotation, draggable with the slider under the panel. */
    private float previewYaw;
    private boolean draggingYaw;
    /** Result of the last validation run, shown on the ADVANCED page. */
    private java.util.List<EntityValidator.Issue> issues = new java.util.ArrayList<>();
    /** Ticks until the next validation pass - revalidating every frame would be wasteful. */
    private int validateCooldown;
    private int jsonTopCached;
    private int navScroll;
    /** True while the "add component" picker overlay is open. */
    private boolean addPicking;
    private int attrScroll;
    /** True while the "add attribute" picker overlay is open. */
    private boolean attrPicking;
    private int attrPickScroll;
    private EditBox attrSearchBox;
    private String lastAttrQuery = "";

    public EntityEditorScreen(EntityBrowserScreen parent, EntityWorkingSet.Entry entry) {
        super(Component.literal("Entity Editor"));
        this.parent = parent;
        this.entry = entry;
        if (entry.json == null) entry.json = new JsonObject();
    }

    // ------------------------------------------------------------ layout

    /** Computes the four zones (header, nav, content well, preview) and builds widgets. */
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
        if (attrPicking) {
            String keep = lastAttrQuery;
            openAttrPicker();
            attrSearchBox.setValue(keep);
        }
    }

    /** Switches page: resets scroll state and rebuilds that page's widgets. */
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

    /** Creates the text fields for the current page; button-only pages add nothing. */
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
                addNumberField("appearance.scale", 1.0, wellX + 168, wellY + 14, 64);
                addFieldAt("appearance.texture", "", wellX + 8, wellY + 56, wellW - 16);
                addNumberField("appearance.hitbox_width", 0, wellX + 8, wellY + 108, 64);
                addNumberField("appearance.hitbox_height", 0, wellX + 88, wellY + 108, 64);
            }
            case ATTRIBUTES -> {
                java.util.List<String> keys = attributeKeys();
                int rows = attrRowsVisible();
                for (int r = 0; r < rows; r++) {
                    int idx = attrScroll + r;
                    if (idx >= keys.size()) break;
                    addNumberField("attributes." + keys.get(idx), 0,
                            wellX + 20, attrRowY(r) + 19, 72);
                }
            }
            case COMBAT -> {
                String type = JsonEdit.getString(entry.json, "combat.type", "none");
                int lx = wellX + 8, rx2 = wellX + half + 8;
                switch (type) {
                    case "melee" -> {
                        addNumberField("combat.speed", 1.2, lx, combatRowY(type, 0), 64);
                        addNumberField("combat.cooldown", 20, rx2, combatRowY(type, 0), 64);
                        addNumberField("combat.knockback", 0, lx, combatRowY(type, 1), 64);
                    }
                    case "ranged" -> {
                        addNumberField("combat.range", 15, lx, combatRowY(type, 0), 64);
                        addNumberField("combat.cooldown", 30, rx2, combatRowY(type, 0), 64);
                        addFieldAt("combat.projectile", "minecraft:arrow", lx, combatRowY(type, 1), half - 16);
                        addNumberField("combat.projectile_speed", 1.6, rx2, combatRowY(type, 1), 64);
                        addNumberField("combat.accuracy", 90, lx, combatRowY(type, 2), 64);
                    }
                    case "hybrid" -> {
                        addNumberField("combat.speed", 1.2, lx, combatRowY(type, 0), 64);
                        addNumberField("combat.cooldown", 20, rx2, combatRowY(type, 0), 64);
                        addNumberField("combat.melee_range", 4, lx, combatRowY(type, 1), 64);
                        addNumberField("combat.knockback", 0, rx2, combatRowY(type, 1), 64);
                        addFieldAt("combat.projectile", "minecraft:arrow", lx, combatRowY(type, 2), half - 16);
                        addNumberField("combat.projectile_speed", 1.6, rx2, combatRowY(type, 2), 64);
                        addNumberField("combat.range", 15, lx, combatRowY(type, 3), 64);
                        addNumberField("combat.accuracy", 90, rx2, combatRowY(type, 3), 64);
                    }
                    default -> { }
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

    /**
     * Text field bound to a dotted JSON path, writing on every keystroke.
     * Clearing the box removes the key, keeping optional fields absent from the saved file.
     */
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

    /** Numeric field; unparseable text is ignored so a half-typed number never clobbers the value. */
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

    /** Multi-line description box, taller than the standard field. */
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

    /** Comma-separated text field backed by the JSON "tags" array. */
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

    /** Drops the ".0" from whole numbers so fields show what the author typed. */
    private static String trimNum(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    // ------------------------------------------------------------ render

    /** Draws the shell (header, nav, preview) and then the current page inside the well. */
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

        if (attrPicking) renderAttrPicker(g, mouseX, mouseY);

        super.extractRenderState(g, mouseX, mouseY, delta);   // widgets draw here

        if (addPicking) renderAddPicker(g, mouseX, mouseY);
    }

    /**
     * Live preview panel. Rebuilds the throwaway entity only when the appearance
     * fingerprint changes, then draws it at the current yaw with the configured gear.
     */
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

    /** Maps a mouse x on the rotation slider to a yaw in 0..360. */
    private void setYawFromMouse(double mx) {
        float f = (float) (mx - sliderX()) / (float) (sliderW() - 2);
        float yaw = Math.max(-180.0f, Math.min(180.0f, f * 360.0f - 180.0f));
        // magnetic snap to the 45-degree points
        float nearest = Math.round(yaw / 45.0f) * 45.0f;
        previewYaw = Math.abs(yaw - nearest) <= 5.0f ? nearest : yaw;
    }

    /** Slider-driven variant of InventoryScreen's GUI entity render. */
    /** Draws the preview entity into the GUI at the given size and position. */
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

    /** Puts the definition's gear onto the preview entity so armour and weapons show up. */
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

    /** Equips one preview slot; unknown item ids are silently skipped. */
    private void previewEquip(JsonObject eq, String key, EquipmentSlot slot) {
        if (!eq.has(key)) return;
        Identifier id = Identifier.tryParse(eq.get(key).getAsString());
        Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
        if (item != null && item != Items.AIR) {
            previewEntity.setItemSlot(slot, new ItemStack(item));
        }
    }

    /** Header strip: back arrow, entity name, UNSAVED chip, SPAWN and SAVE buttons. */
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

    private static final int ATTR_ROW = 46;
    private static final int[] ATTR_COLORS = {
            PanelStyle.ERROR, 0xFF5B9BD5, PanelStyle.EDITED,
            0xFF57B3A0, PanelStyle.VALID, PanelStyle.ACCENT};

    /** The "attributes" object, created empty on first use. */
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

    /** Turns "minecraft:movement_speed" into a readable "Movement Speed" label. */
    private static String prettyAttribute(String key) {
        String path = key.contains(":") ? key.split(":", 2)[1] : key;
        return path.replace('_', ' ').replace('.', ' ').toUpperCase();
    }

    /** Registry attributes not yet on this entity (minecraft first, sorted). */
    /** Registered attributes the definition does not set yet - the picker's candidate list. */
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

    // picker layout (shared by render + input)
    private int pickerW() { return Math.min(320, pw - 40); }
    /** Visible picker rows adapt to the screen so the panel always fits. */
    private int pickerRows() {
        return Math.max(4, Math.min(14, (height - 124) / 14));
    }
    private int pickerH() { return 56 + pickerRows() * 14 + 8 + 8; }
    private int pickerX() { return (width - pickerW()) / 2; }
    private int pickerY() { return (height - pickerH()) / 2; }
    private int pickerListY() { return pickerY() + 52; }

    private void openAttrPicker() {
        attrPicking = true;
        attrPickScroll = 0;
        lastAttrQuery = "";
        clearWidgets();
        attrSearchBox = new EditBox(font, pickerX() + 8, pickerY() + 26,
                pickerW() - 16, 18, Component.literal("Search"));
        attrSearchBox.setHint(Component.literal("Search attributes..."));
        addRenderableWidget(attrSearchBox);
        setFocused(attrSearchBox);
    }

    private void closeAttrPicker() {
        attrPicking = false;
        attrSearchBox = null;
        setPageKeepAttr();
    }

    private String attrQuery() {
        return attrSearchBox == null ? "" : attrSearchBox.getValue().trim();
    }

    private java.util.List<Identifier> filteredMissingAttributes() {
        String query = attrQuery().toLowerCase();
        return missingAttributes().stream()
                .filter(id -> query.isEmpty()
                        || id.toString().contains(query)
                        || prettyAttribute(id.toString()).toLowerCase().contains(query))
                .toList();
    }

    /** Adds an attribute to the definition, seeded with its vanilla default value. */
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
    /** JSON key each page edits, which is how "does this component exist" is decided. */
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

    /** True when the definition contains this page's section. */
    private boolean hasComponent(Page p) {
        String key = sectionFor(p);
        if (key == null) return true;
        if (p == Page.AI) return entry.json.has("ai") || entry.json.has("targeting");
        return entry.json.has(key);
    }

    /** Pages shown in the nav column: the ones this definition actually has. */
    private java.util.List<Page> presentPages() {
        java.util.List<Page> pages = new java.util.ArrayList<>();
        for (Page p : Page.values()) if (hasComponent(p)) pages.add(p);
        return pages;
    }

    /** Pages offered by "+ ADD COMPONENT": the ones this definition lacks. */
    private java.util.List<Page> missingPages() {
        java.util.List<Page> pages = new java.util.ArrayList<>();
        for (Page p : Page.values()) if (!hasComponent(p)) pages.add(p);
        return pages;
    }

    /** Creates an empty section for the page and switches to it. */
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

    /** Nav column: present components, then the divider and the add button. */
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

    /** Overlay listing the components this definition does not have yet. */
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

    /** GENERAL page: name, id, description, tags and the name-plate toggle. */
    private void renderGeneral(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("DISPLAY NAME"), wellX + 8, wellY + 4, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("RESOURCE ID"), wellX + 8, wellY + 44, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("DESCRIPTION"), wellX + 8, wellY + 84, PanelStyle.TEXT_DIM);
        boolean visible = JsonEdit.getBool(entry.json, "display.name_visible", true);
        renderCheckbox(g, mouseX, mouseY, "Visible nameplate", visible, wellX + 8, wellY + 156);
    }

    /** APPEARANCE page: model, texture, scale, hitbox and glow - all mirrored in the preview. */
    private void renderAppearance(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("MODEL"), wellX + 8, wellY + 4, PanelStyle.TEXT_DIM);
        String model = JsonEdit.getString(entry.json, "appearance.model", MODELS[0]);
        String shortName = model.contains(":") ? model.split(":", 2)[1] : model;
        PanelStyle.button(g, font, shortName.toUpperCase(), wellX + 8, wellY + 14, 140,
                PanelStyle.hit(mouseX, mouseY, wellX + 8, wellY + 14, 140, PanelStyle.CONTROL_H), false);
        g.text(font, Component.literal("SCALE"), wellX + 168, wellY + 4, PanelStyle.TEXT_DIM);

        g.text(font, Component.literal("TEXTURE (blank = model default)"),
                wellX + 8, wellY + 46, PanelStyle.TEXT_DIM);

        g.text(font, Component.literal("HITBOX (0 = model default)"),
                wellX + 8, wellY + 86, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("WIDTH"), wellX + 8, wellY + 98, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("HEIGHT"), wellX + 88, wellY + 98, PanelStyle.TEXT_DIM);

        boolean glow = JsonEdit.getBool(entry.json, "appearance.glow", false);
        renderCheckbox(g, mouseX, mouseY, "Glow outline", glow, wellX + 8, wellY + 140);
    }

    /** ATTRIBUTES page: one row per configured attribute, plus an add button. */
    private void renderAttributes(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int addW = 104;
        PanelStyle.button(g, font, "+ ADD ATTRIBUTE", wellX + 8, wellY, addW,
                PanelStyle.hit(mouseX, mouseY, wellX + 8, wellY, addW, PanelStyle.CONTROL_H), true);

        java.util.List<String> keys = attributeKeys();
        if (keys.isEmpty()) {
            String l1 = font.plainSubstrByWidth("No attributes set.", wellW - 16);
            String l2 = font.plainSubstrByWidth("The entity keeps its base defaults.", wellW - 16);
            g.text(font, Component.literal(l1), wellX + 8, wellY + 40, PanelStyle.TEXT_DIM);
            g.text(font, Component.literal(l2), wellX + 8, wellY + 52, PanelStyle.TEXT_DIM);
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
            boolean xHover = PanelStyle.hit(mouseX, mouseY, rx + rw - 17, ry + 3, 14, 14);
            g.text(font, Component.literal("X"), rx + rw - 13, ry + 5,
                    xHover ? PanelStyle.ERROR : PanelStyle.TEXT_DIM);
        }
        PanelStyle.scrollbar(g, wellX + wellW - 4, wellY + 30, wellH - 34,
                keys.size(), rows, attrScroll);
    }

    /** Searchable overlay of attributes the definition does not set yet. */
    private void renderAttrPicker(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        java.util.List<Identifier> filtered = filteredMissingAttributes();
        int w = pickerW(), cx = pickerX(), cy = pickerY(), h = pickerH();
        g.fill(0, 0, width, height, PanelStyle.SCREEN_DIM);
        PanelStyle.panel(g, cx, cy, w, h);
        g.text(font, Component.literal("ADD ATTRIBUTE"), cx + 8, cy + 8, PanelStyle.TEXT);
        g.text(font, Component.literal(filtered.size() + " AVAILABLE"),
                cx + w - 8 - font.width(filtered.size() + " AVAILABLE"), cy + 8, PanelStyle.TEXT_DIM);

        int listY = pickerListY();
        // the list lives in its own sunken frame
        PanelStyle.inset(g, cx + 6, listY - 4, w - 12, pickerRows() * 14 + 8);
        for (int i = 0; i < pickerRows(); i++) {
            int idx = attrPickScroll + i;
            if (idx >= filtered.size()) break;
            int iy = listY + i * 14;
            boolean hovered = PanelStyle.hit(mouseX, mouseY, cx + 8, iy, w - 16, 14);
            if (hovered) g.fill(cx + 8, iy, cx + w - 8, iy + 14, PanelStyle.ROW_HOVER);
            Identifier id = filtered.get(idx);
            String name = prettyAttribute(id.toString());
            g.text(font, Component.literal(name), cx + 14, iy + 3,
                    hovered ? PanelStyle.TEXT : PanelStyle.TEXT_DIM);
        }
        if (filtered.isEmpty()) {
            g.text(font, Component.literal("No matches"), cx + 14, listY + 3, PanelStyle.TEXT_DIM);
        }
        PanelStyle.scrollbar(g, cx + w - 12, listY - 2, pickerRows() * 14 + 4,
                filtered.size(), pickerRows(), attrPickScroll);

    }

    /** MOVEMENT page: navigator type and the capability flags. */
    private void renderMovement(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("MOVEMENT TYPE"), wellX + 8, wellY + 4, PanelStyle.TEXT_DIM);
        String type = JsonEdit.getString(entry.json, "movement.type", "ground");
        PanelStyle.button(g, font, type.toUpperCase(), wellX + 8, wellY + 14, 120,
                PanelStyle.hit(mouseX, mouseY, wellX + 8, wellY + 14, 120, PanelStyle.CONTROL_H), false);

        g.text(font, Component.literal("CAPABILITIES"), wellX + 8, wellY + 48, PanelStyle.TEXT_DIM);
        boolean swim = JsonEdit.getBool(entry.json, "movement.can_swim", true);
        renderCheckbox(g, mouseX, mouseY, "Can swim (float in water)", swim, wellX + 8, wellY + 60);
        boolean doors = JsonEdit.getBool(entry.json, "movement.can_open_doors", false);
        renderCheckbox(g, mouseX, mouseY, "Can open doors", doors, wellX + 8, wellY + 76);
        boolean avoidWater = JsonEdit.getBool(entry.json, "movement.avoid_water", false);
        renderCheckbox(g, mouseX, mouseY, "Avoid water when wandering", avoidWater, wellX + 8, wellY + 92);
        boolean jump = JsonEdit.getBool(entry.json, "movement.can_jump", true);
        renderCheckbox(g, mouseX, mouseY, "Can jump", jump, wellX + 8, wellY + 108);
        boolean climb = JsonEdit.getBool(entry.json, "movement.can_climb", true);
        renderCheckbox(g, mouseX, mouseY, "Can climb ladders", climb, wellX + 8, wellY + 124);
        boolean fly = JsonEdit.getBool(entry.json, "movement.can_fly", false);
        renderCheckbox(g, mouseX, mouseY, "Can fly (hovering flight)", fly, wellX + 8, wellY + 140);
    }

    // combat page geometry — adapts to well size so nothing overflows
    private int combatButtonW() {
        return (wellW - 16 - (COMBAT_TYPES.length - 1) * 4) / COMBAT_TYPES.length;
    }
    private int combatButtonX(int i) {
        return wellX + 8 + i * (combatButtonW() + 4);
    }
    private int combatRowCount(String type) {
        return switch (type) {
            case "melee" -> 2;
            case "ranged" -> 3;
            case "hybrid" -> 4;
            default -> 0;
        };
    }
    private int combatRowStep(String type) {
        int n = combatRowCount(type);
        return n == 0 ? 40 : Math.max(30, Math.min(40, (wellH - 56) / n));
    }
    private int combatRowY(String type, int i) {
        return wellY + 46 + i * combatRowStep(type);
    }
    private void combatLabel(GuiGraphicsExtractor g, String text, int x, int y) {
        int max = wellW / 2 - 20;
        if (font.width(text) > max) text = font.plainSubstrByWidth(text, max);
        g.text(font, Component.literal(text), x, y, PanelStyle.TEXT_DIM);
    }

    /**
     * COMBAT page. The visible rows depend on the combat type - ranged shows projectile
     * fields, melee does not - which is why the row helpers above take the type.
     */
    private void renderCombat(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int half = wellW / 2;
        g.text(font, Component.literal("COMBAT TYPE"), wellX + 8, wellY + 4, PanelStyle.TEXT_DIM);
        String type = JsonEdit.getString(entry.json, "combat.type", "none");
        int bw = combatButtonW();
        for (int i = 0; i < COMBAT_TYPES.length; i++) {
            PanelStyle.button(g, font, COMBAT_TYPES[i].toUpperCase(), combatButtonX(i), wellY + 14, bw,
                    PanelStyle.hit(mouseX, mouseY, combatButtonX(i), wellY + 14, bw, PanelStyle.CONTROL_H),
                    COMBAT_TYPES[i].equals(type));
        }

        int lx = wellX + 8, rx2 = wellX + half + 8;
        switch (type) {
            case "melee" -> {
                combatLabel(g, "CHASE SPEED", lx, combatRowY(type, 0) - 10);
                combatLabel(g, "COOLDOWN (ticks)", rx2, combatRowY(type, 0) - 10);
                combatLabel(g, "KNOCKBACK", lx, combatRowY(type, 1) - 10);
            }
            case "ranged" -> {
                combatLabel(g, "RANGE", lx, combatRowY(type, 0) - 10);
                combatLabel(g, "COOLDOWN (ticks)", rx2, combatRowY(type, 0) - 10);
                combatLabel(g, "PROJECTILE", lx, combatRowY(type, 1) - 10);
                combatLabel(g, "PROJ. SPEED", rx2, combatRowY(type, 1) - 10);
                combatLabel(g, "ACCURACY (%)", lx, combatRowY(type, 2) - 10);
            }
            case "hybrid" -> {
                combatLabel(g, "CHASE SPEED", lx, combatRowY(type, 0) - 10);
                combatLabel(g, "COOLDOWN (ticks)", rx2, combatRowY(type, 0) - 10);
                combatLabel(g, "SWITCH RANGE", lx, combatRowY(type, 1) - 10);
                combatLabel(g, "KNOCKBACK", rx2, combatRowY(type, 1) - 10);
                combatLabel(g, "PROJECTILE", lx, combatRowY(type, 2) - 10);
                combatLabel(g, "PROJ. SPEED", rx2, combatRowY(type, 2) - 10);
                combatLabel(g, "BOW RANGE", lx, combatRowY(type, 3) - 10);
                combatLabel(g, "ACCURACY (%)", rx2, combatRowY(type, 3) - 10);
            }
            default -> g.text(font, Component.literal("No combat — this entity never auto-attacks."),
                    wellX + 8, wellY + 50, PanelStyle.TEXT_DIM);
        }
    }

    /** EQUIPMENT page: one text field per slot. */
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

    // ---------------------------------------------------------- AI page (design book 12)

    /** One visual row: an AI goal, the TARGETING header, or a target rule. */
    /**
     * One row of the AI page. The list interleaves both arrays, so {@code kind} says which
     * ("ai" or "targeting") and {@code arrayIndex} the position inside it; a null {@code obj}
     * marks a section header rather than a real entry.
     */
    private record AiRow(String kind, int arrayIndex, @org.jspecify.annotations.Nullable JsonObject obj) {}

    private static final int AI_ROW = 24;

    private JsonArray aiArray(String key) {
        return entry.json.has(key) && entry.json.get(key).isJsonArray()
                ? entry.json.getAsJsonArray(key) : new JsonArray();
    }

    private java.util.List<AiRow> aiRows() {
        java.util.List<AiRow> rows = new java.util.ArrayList<>();
        for (String kind : new String[]{"goal", "target"}) {
            if (kind.equals("target")) rows.add(new AiRow("theader", -1, null));
            JsonArray array = aiArray(kind.equals("goal") ? "ai" : "targeting");
            java.util.List<Integer> order = new java.util.ArrayList<>();
            for (int i = 0; i < array.size(); i++) if (array.get(i).isJsonObject()) order.add(i);
            order.sort(java.util.Comparator.comparingInt(i -> {
                JsonObject o = array.get(i).getAsJsonObject();
                return o.has("priority") ? o.get("priority").getAsInt() : 5;
            }));
            for (int i : order) rows.add(new AiRow(kind, i, array.get(i).getAsJsonObject()));
        }
        return rows;
    }

    private static String aiTypeId(JsonObject obj) {
        return obj.has("type") ? obj.get("type").getAsString() : "?";
    }

    private String aiRowLabel(AiRow row) {
        var schema = row.kind().equals("goal")
                ? GoalSchemas.goals().get(aiTypeId(row.obj()))
                : GoalSchemas.targets().get(aiTypeId(row.obj()));
        if (schema != null) return schema.label().toUpperCase();
        String type = aiTypeId(row.obj());
        return (type.contains(":") ? type.split(":", 2)[1] : type).replace('_', ' ').toUpperCase();
    }

    private String aiRowSummary(AiRow row) {
        StringBuilder sb = new StringBuilder();
        for (var e : row.obj().entrySet()) {
            if (e.getKey().equals("type") || e.getKey().equals("priority")) continue;
            if (!sb.isEmpty()) sb.append("  ");
            sb.append(e.getKey().replace('_', ' ')).append(' ').append(e.getValue().getAsString());
        }
        return sb.toString();
    }

    private int aiVisibleRows() {
        return Math.max(1, (wellH - 34) / AI_ROW);
    }

    /** AI page: goals and targeting rules as one priority-ordered list. */
    private void renderAi(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("AI GOALS"), wellX + 8, wellY + 6, PanelStyle.TEXT_DIM);
        int addW = 72;
        PanelStyle.button(g, font, "+ ADD GOAL", wellX + wellW - addW - 8, wellY, addW,
                PanelStyle.hit(mouseX, mouseY, wellX + wellW - addW - 8, wellY, addW, PanelStyle.CONTROL_H), true);

        java.util.List<AiRow> rows = aiRows();
        int visible = aiVisibleRows();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, rows.size() - visible)));
        for (int r = 0; r < visible; r++) {
            int idx = listScroll + r;
            if (idx >= rows.size()) break;
            AiRow row = rows.get(idx);
            int ry = wellY + 30 + r * AI_ROW;
            int rx = wellX + 8;
            int rw = wellW - 16;

            if (row.kind().equals("theader")) {
                g.text(font, Component.literal("TARGETING"), rx, ry + 8, PanelStyle.TEXT_DIM);
                int tw = 80;
                PanelStyle.button(g, font, "+ ADD RULE", rx + rw - tw, ry + 1, tw,
                        PanelStyle.hit(mouseX, mouseY, rx + rw - tw, ry + 1, tw, 22), true);
                continue;
            }

            boolean hovered = PanelStyle.hit(mouseX, mouseY, rx, ry, rw, AI_ROW - 3);
            g.fill(rx, ry, rx + rw, ry + AI_ROW - 3, hovered ? PanelStyle.ROW_HOVER : PanelStyle.ROW_BG);
            g.fill(rx, ry, rx + 3, ry + AI_ROW - 3, row.kind().equals("goal")
                    ? PanelStyle.ACCENT : PanelStyle.EDITED);

            int priority = row.obj().has("priority") ? row.obj().get("priority").getAsInt() : 5;
            g.fill(rx + 7, ry + 4, rx + 23, ry + AI_ROW - 7, PanelStyle.INSET_BG);
            String ps = String.valueOf(priority);
            g.text(font, Component.literal(ps), rx + 15 - font.width(ps) / 2, ry + 7, PanelStyle.TEXT_DIM);

            String label = aiRowLabel(row);
            g.text(font, Component.literal(label), rx + 30, ry + 7, PanelStyle.TEXT);

            boolean known = (row.kind().equals("goal")
                    ? GoalSchemas.goals() : GoalSchemas.targets()).containsKey(aiTypeId(row.obj()));
            String summary = known ? aiRowSummary(row) : "(addon type)";
            int summaryX = rx + 30 + font.width(label) + 8;
            int summaryMax = rx + rw - 58 - summaryX;
            if (summaryMax > 20 && !summary.isEmpty()) {
                if (font.width(summary) > summaryMax) {
                    summary = font.plainSubstrByWidth(summary, summaryMax - 6) + "…";
                }
                g.text(font, Component.literal(summary), summaryX, ry + 7, PanelStyle.TEXT_DIM);
            }

            if (known) {
                boolean editHover = PanelStyle.hit(mouseX, mouseY, rx + rw - 52, ry + 4, 32, 16);
                g.text(font, Component.literal("EDIT"), rx + rw - 50, ry + 7,
                        editHover ? PanelStyle.TEXT : PanelStyle.TEXT_DIM);
            }
            boolean xHover = PanelStyle.hit(mouseX, mouseY, rx + rw - 16, ry + 4, 12, 16);
            g.text(font, Component.literal("X"), rx + rw - 13, ry + 7,
                    xHover ? PanelStyle.ERROR : PanelStyle.TEXT_DIM);
        }
        if (rows.size() <= 1) {
            g.text(font, Component.literal("No goals — the entity stands idle."),
                    wellX + 8, wellY + 34, PanelStyle.TEXT_DIM);
        }
        PanelStyle.scrollbar(g, wellX + wellW - 4, wellY + 30, wellH - 34,
                rows.size(), visible, listScroll);
    }

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

    /** DROPS page: loot table id and XP reward. */
    private void renderDrops(GuiGraphicsExtractor g) {
        g.text(font, Component.literal("LOOT TABLE (blank = none)"), wellX + 8, wellY + 4, PanelStyle.TEXT_DIM);
        g.text(font, Component.literal("XP"), wellX + 8, wellY + 44, PanelStyle.TEXT_DIM);
    }

    /** ADVANCED page: the validation report plus a read-only pretty-printed JSON view. */
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

    /** Labelled checkbox; the click handling lives in {@link #mouseClicked}. */
    private void renderCheckbox(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                String label, boolean value, int x, int y) {
        PanelStyle.inset(g, x, y, 12, 12);
        if (value) g.fill(x + 3, y + 3, x + 9, y + 9, PanelStyle.VALID);
        g.text(font, Component.literal(label), x + 18, y + 2,
                PanelStyle.hit(mouseX, mouseY, x, y, 12 + 6 + font.width(label), 12)
                        ? PanelStyle.TEXT : PanelStyle.TEXT_DIM);
    }

    // ------------------------------------------------------------ input

    /**
     * All hit-testing for the immediate-mode controls, dispatched per page.
     *
     * <p>Long by nature: buttons, checkboxes, list rows and the two picker overlays are
     * drawn directly rather than as widgets, so every clickable region is re-derived here
     * from the same geometry the render methods use. Overlays are checked first, since
     * they are modal.</p>
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();

        // overlays get first claim on every click
        // attribute picker eats all clicks while open
        if (attrPicking) {
            int w = pickerW(), cx = pickerX(), cy = pickerY(), h = pickerH();
            // clicks on the search box go to the widget
            if (attrSearchBox != null && PanelStyle.hit(mx, my,
                    cx + 8, cy + 26, w - 16, 18)) {
                return super.mouseClicked(event, doubleClick);
            }
            java.util.List<Identifier> filtered = filteredMissingAttributes();
            int listY = pickerListY();
            for (int i = 0; i < pickerRows(); i++) {
                int idx = attrPickScroll + i;
                if (idx >= filtered.size()) break;
                int iy = listY + i * 14;
                if (PanelStyle.hit(mx, my, cx + 8, iy, w - 16, 14)) {
                    Identifier picked = filtered.get(idx);
                    closeAttrPicker();
                    addAttribute(picked);
                    return true;
                }
            }
            if (!PanelStyle.hit(mx, my, cx, cy, w, h)) {
                closeAttrPicker();   // click outside closes; inside panel keeps typing
            }
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
                if (PanelStyle.hit(mx, my, wellX + 8, wellY + 140, 160, 12)) {
                    toggleBool("appearance.glow", false);
                    return true;
                }
            }
            case MOVEMENT -> {
                if (PanelStyle.hit(mx, my, wellX + 8, wellY + 14, 120, PanelStyle.CONTROL_H)) {
                    String type = JsonEdit.getString(entry.json, "movement.type", "ground");
                    int i = 0;
                    for (int t = 0; t < MOVEMENT_TYPES.length; t++) if (MOVEMENT_TYPES[t].equals(type)) i = t;
                    JsonEdit.set(entry.json, "movement.type", MOVEMENT_TYPES[(i + 1) % MOVEMENT_TYPES.length]);
                    entry.dirty = true;
                    return true;
                }
                if (PanelStyle.hit(mx, my, wellX + 8, wellY + 60, 180, 12)) {
                    toggleBool("movement.can_swim", true);
                    return true;
                }
                if (PanelStyle.hit(mx, my, wellX + 8, wellY + 76, 180, 12)) {
                    toggleBool("movement.can_open_doors", false);
                    return true;
                }
                if (PanelStyle.hit(mx, my, wellX + 8, wellY + 92, 180, 12)) {
                    toggleBool("movement.avoid_water", false);
                    return true;
                }
                if (PanelStyle.hit(mx, my, wellX + 8, wellY + 108, 180, 12)) {
                    toggleBool("movement.can_jump", true);
                    return true;
                }
                if (PanelStyle.hit(mx, my, wellX + 8, wellY + 124, 180, 12)) {
                    toggleBool("movement.can_climb", true);
                    return true;
                }
                if (PanelStyle.hit(mx, my, wellX + 8, wellY + 140, 180, 12)) {
                    toggleBool("movement.can_fly", false);
                    return true;
                }
            }
            case COMBAT -> {
                for (int i = 0; i < COMBAT_TYPES.length; i++) {
                    if (PanelStyle.hit(mx, my, combatButtonX(i), wellY + 14,
                            combatButtonW(), PanelStyle.CONTROL_H)) {
                        JsonEdit.set(entry.json, "combat.type", COMBAT_TYPES[i]);
                        entry.dirty = true;
                        setPage(Page.COMBAT);   // rebuild the type-dependent fields
                        return true;
                    }
                }
            }
            case AI -> {
                int addW = 72;
                if (PanelStyle.hit(mx, my, wellX + wellW - addW - 8, wellY, addW, PanelStyle.CONTROL_H)) {
                    Minecraft.getInstance().gui.setScreen(new TypedObjectListScreen(
                            this, "AI GOALS", arr("ai"), GoalSchemas.goals(),
                            () -> entry.dirty = true, true));
                    return true;
                }
                java.util.List<AiRow> aiRowList = aiRows();
                int visible = aiVisibleRows();
                for (int r = 0; r < visible; r++) {
                    int idx = listScroll + r;
                    if (idx >= aiRowList.size()) break;
                    AiRow row = aiRowList.get(idx);
                    int ry = wellY + 30 + r * AI_ROW;
                    int rx = wellX + 8;
                    int rw = wellW - 16;

                    if (row.kind().equals("theader")) {
                        int tw = 80;
                        if (PanelStyle.hit(mx, my, rx + rw - tw, ry + 1, tw, 22)) {
                            Minecraft.getInstance().gui.setScreen(new TypedObjectListScreen(
                                    this, "TARGETING", arr("targeting"), GoalSchemas.targets(),
                                    () -> entry.dirty = true, true));
                            return true;
                        }
                        continue;
                    }

                    JsonArray array = aiArray(row.kind().equals("goal") ? "ai" : "targeting");
                    if (PanelStyle.hit(mx, my, rx + rw - 16, ry + 4, 12, 16)) {
                        array.remove(row.arrayIndex());
                        entry.dirty = true;
                        return true;
                    }
                    var schemas = row.kind().equals("goal")
                            ? GoalSchemas.goals() : GoalSchemas.targets();
                    var schema = schemas.get(aiTypeId(row.obj()));
                    if (schema != null && PanelStyle.hit(mx, my, rx + rw - 52, ry + 4, 32, 16)) {
                        Minecraft.getInstance().gui.setScreen(
                                new com.myyyst.myrpg.core.client.editor.TypedObjectConfigScreen(
                                        this, array, schema.typeId(), schema.label(),
                                        schema.fields(), row.obj(), () -> entry.dirty = true));
                        return true;
                    }
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

    /** Flips a boolean at a dotted path and marks the entry dirty. */
    private void toggleBool(String path, boolean fallback) {
        JsonEdit.set(entry.json, path, !JsonEdit.getBool(entry.json, path, fallback));
        entry.dirty = true;
    }

    /** Re-runs validation on a cooldown rather than every frame, and drives the search box. */
    @Override
    public void tick() {
        if (--validateCooldown <= 0) {
            validateCooldown = 20;
            issues = EntityValidator.validate(entry);
        }
        if (attrPicking) {
            String query = attrQuery();
            if (!query.equals(lastAttrQuery)) {
                lastAttrQuery = query;
                attrPickScroll = 0;
            }
        }
    }

    /**
     * Validates and, if clean, sends the definition to the server.
     * Errors abort the save and jump to the ADVANCED page, where the issue list is shown;
     * warnings do not block.
     */
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

    /** Dragging only matters for the preview rotation slider. */
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

    /** Scrolling is routed to whichever list the cursor is over (nav, attributes, AI, JSON). */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (attrPicking) {
            int max = Math.max(0, filteredMissingAttributes().size() - pickerRows());
            attrPickScroll = Math.max(0, Math.min(max,
                    attrPickScroll - (int) Math.signum(vertical) * 3));
            return true;
        }
        if (page == Page.AI && PanelStyle.hit(mouseX, mouseY,
                wellX, wellY + 28, wellW, wellH - 28)) {
            int max = Math.max(0, aiRows().size() - aiVisibleRows());
            listScroll = Math.max(0, Math.min(max, listScroll - (int) Math.signum(vertical)));
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

    /** Editing must not pause a singleplayer world - the live preview keeps animating. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
