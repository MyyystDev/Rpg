package com.myyyst.myrpg.entities.client.editor;

import com.google.gson.JsonObject;
import com.myyyst.myrpg.entities.client.RpgModels;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side semantic validation (design book "Validation" page).
 * ERRORs would produce broken references or fail the server codec;
 * WARNs are legal-but-probably-not-what-you-meant.
 */
public final class EntityValidator {

    public enum Level { ERROR, WARN }

    public record Issue(Level level, String message) {}

    public static List<Issue> validate(EntityWorkingSet.Entry entry) {
        List<Issue> issues = new ArrayList<>();
        JsonObject json = entry.json;
        if (json == null) {
            issues.add(new Issue(Level.ERROR, "Unparseable JSON"));
            return issues;
        }

        // id
        if (!entry.entityId.contains(":") || Identifier.tryParse(entry.entityId) == null) {
            issues.add(new Issue(Level.ERROR, "Invalid resource ID (namespace:path)"));
        }

        // equipment items
        if (json.has("equipment") && json.get("equipment").isJsonObject()) {
            JsonObject eq = json.getAsJsonObject("equipment");
            for (String slot : new String[]{"mainhand", "offhand", "head", "chest", "legs", "feet"}) {
                if (!eq.has(slot)) continue;
                if (!itemExists(eq.get(slot).getAsString())) {
                    issues.add(new Issue(Level.ERROR, "Unknown item in " + slot
                            + ": " + eq.get(slot).getAsString()));
                }
            }
        }

        // attributes
        if (json.has("attributes") && json.get("attributes").isJsonObject()) {
            for (String key : json.getAsJsonObject("attributes").keySet()) {
                Identifier id = Identifier.tryParse(key);
                if (id == null || BuiltInRegistries.ATTRIBUTE.get(id).isEmpty()) {
                    issues.add(new Issue(Level.ERROR, "Unknown attribute: " + key));
                }
            }
        }

        // appearance
        if (json.has("appearance") && json.get("appearance").isJsonObject()) {
            JsonObject appearance = json.getAsJsonObject("appearance");
            if (appearance.has("model")) {
                RpgModels.bootstrap();
                Identifier model = Identifier.tryParse(appearance.get("model").getAsString());
                if (model == null || !RpgModels.all().containsKey(model)) {
                    issues.add(new Issue(Level.WARN, "Unknown model (addon provider?): "
                            + appearance.get("model").getAsString()));
                }
            }
            if (appearance.has("texture")
                    && Identifier.tryParse(appearance.get("texture").getAsString()) == null) {
                issues.add(new Issue(Level.ERROR, "Invalid texture path"));
            }
        }

        // combat vs targeting
        String combatType = json.has("combat") && json.getAsJsonObject("combat").has("type")
                ? json.getAsJsonObject("combat").get("type").getAsString() : "none";
        boolean hasTargeting = json.has("targeting") && json.get("targeting").isJsonArray()
                && !json.getAsJsonArray("targeting").isEmpty();
        if (!combatType.equals("none") && !hasTargeting) {
            issues.add(new Issue(Level.WARN, combatType + " combat but no targeting rules"));
        }
        if (combatType.equals("ranged")) {
            JsonObject combat = json.getAsJsonObject("combat");
            if (combat.has("projectile") && !itemExists(combat.get("projectile").getAsString())) {
                issues.add(new Issue(Level.WARN, "Unknown projectile item"));
            }
        }

        // ai
        boolean hasAi = json.has("ai") && json.get("ai").isJsonArray()
                && !json.getAsJsonArray("ai").isEmpty();
        if (!hasAi) {
            issues.add(new Issue(Level.WARN, "No AI goals — entity will stand still"));
        } else {
            for (var e : json.getAsJsonArray("ai")) {
                if (!e.isJsonObject() || !e.getAsJsonObject().has("type")) continue;
                String type = e.getAsJsonObject().get("type").getAsString();
                if (!GoalSchemas.goals().containsKey(type)) {
                    issues.add(new Issue(Level.WARN, "Unknown goal type (addon?): " + type));
                }
            }
        }

        // loot
        if (json.has("loot") && json.getAsJsonObject("loot").has("loot_table")
                && Identifier.tryParse(json.getAsJsonObject("loot")
                        .get("loot_table").getAsString()) == null) {
            issues.add(new Issue(Level.ERROR, "Invalid loot table id"));
        }

        return issues;
    }

    public static boolean itemExists(String rawId) {
        Identifier id = Identifier.tryParse(rawId);
        if (id == null) return false;
        var item = BuiltInRegistries.ITEM.getValue(id);
        return item != null && item != Items.AIR;
    }

    public static boolean hasErrors(List<Issue> issues) {
        return issues.stream().anyMatch(i -> i.level() == Level.ERROR);
    }

    private EntityValidator() {}
}
