package com.myyyst.myrpg.entities.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Starter JSON per template — values only, never different Java classes. */
public final class EntityTemplates {

    public static final String[] NAMES = {"BLANK", "NPC", "GUARD", "HOSTILE", "BOSS"};

    /** Human-readable component list shown in the create dialog preview. */
    public static String[] components(String template) {
        return switch (template) {
            case "NPC" -> new String[]{"APPEARANCE", "ATTRIBUTES", "AI", "PERSISTENCE"};
            case "GUARD" -> new String[]{"APPEARANCE", "ATTRIBUTES", "AI", "TARGETING",
                    "COMBAT", "EQUIPMENT", "PERSISTENCE"};
            case "HOSTILE" -> new String[]{"APPEARANCE", "ATTRIBUTES", "AI", "TARGETING",
                    "COMBAT", "LOOT"};
            case "BOSS" -> new String[]{"APPEARANCE", "ATTRIBUTES", "AI", "TARGETING",
                    "COMBAT", "RULES", "PERSISTENCE"};
            default -> new String[]{};
        };
    }

    public static JsonObject build(String template, String displayName, String model) {
        JsonObject json = new JsonObject();

        JsonObject display = new JsonObject();
        display.addProperty("name", displayName);
        display.addProperty("name_visible", true);
        json.add("display", display);

        JsonObject appearance = new JsonObject();
        appearance.addProperty("model", model);
        json.add("appearance", appearance);

        if (template.equals("BLANK")) return json;

        JsonObject attributes = new JsonObject();
        switch (template) {
            case "NPC" -> {
                attributes.addProperty("minecraft:max_health", 20);
                attributes.addProperty("minecraft:movement_speed", 0.25);
            }
            case "GUARD" -> {
                attributes.addProperty("minecraft:max_health", 40);
                attributes.addProperty("minecraft:movement_speed", 0.3);
                attributes.addProperty("minecraft:attack_damage", 6);
            }
            case "HOSTILE" -> {
                attributes.addProperty("minecraft:max_health", 24);
                attributes.addProperty("minecraft:movement_speed", 0.27);
                attributes.addProperty("minecraft:attack_damage", 5);
            }
            case "BOSS" -> {
                attributes.addProperty("minecraft:max_health", 300);
                attributes.addProperty("minecraft:movement_speed", 0.28);
                attributes.addProperty("minecraft:attack_damage", 10);
                attributes.addProperty("minecraft:armor", 8);
            }
        }
        json.add("attributes", attributes);

        JsonArray ai = new JsonArray();
        ai.add(goal("myrpg_entities:random_walk", 5));
        ai.add(goal("myrpg_entities:look_at_player", 7));
        ai.add(goal("myrpg_entities:look_around", 8));
        if (template.equals("GUARD")) {
            JsonObject guard = goal("myrpg_entities:guard_position", 3);
            guard.addProperty("radius", 16);
            ai.add(guard);
        }
        json.add("ai", ai);

        if (!template.equals("NPC")) {
            JsonArray targeting = new JsonArray();
            targeting.add(goal("myrpg_entities:retaliate", 1));
            if (template.equals("HOSTILE") || template.equals("BOSS")) {
                targeting.add(goal("myrpg_entities:player", 2));
            } else if (template.equals("GUARD")) {
                JsonObject t = goal("myrpg_entities:entity_type", 2);
                t.addProperty("entity", "minecraft:zombie");
                targeting.add(t);
            }
            json.add("targeting", targeting);

            JsonObject combat = new JsonObject();
            combat.addProperty("type", "melee");
            json.add("combat", combat);
        }

        if (template.equals("GUARD")) {
            JsonObject equipment = new JsonObject();
            equipment.addProperty("mainhand", "minecraft:iron_sword");
            json.add("equipment", equipment);
        }
        if (template.equals("HOSTILE")) {
            JsonObject loot = new JsonObject();
            loot.addProperty("xp", 5);
            json.add("loot", loot);
            JsonObject persistence = new JsonObject();
            persistence.addProperty("despawn", true);
            json.add("persistence", persistence);
        } else {
            JsonObject persistence = new JsonObject();
            persistence.addProperty("despawn", false);
            json.add("persistence", persistence);
        }
        if (template.equals("BOSS")) {
            JsonObject a = json.getAsJsonObject("appearance");
            a.addProperty("scale", 1.5);
            json.add("rules", new JsonArray());
        }
        return json;
    }

    private static JsonObject goal(String type, int priority) {
        return JsonParser.parseString(
                "{\"type\":\"" + type + "\",\"priority\":" + priority + "}").getAsJsonObject();
    }

    private EntityTemplates() {}
}
