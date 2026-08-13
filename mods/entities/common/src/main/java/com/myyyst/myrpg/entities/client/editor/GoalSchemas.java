package com.myyyst.myrpg.entities.client.editor;

import com.myyyst.myrpg.core.client.editor.EffectSchemas.EffectSchema;
import com.myyyst.myrpg.core.client.editor.EffectSchemas.FieldSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side form schemas for AI goal and targeting types (design book
 * pages 12-13). Reuses core's generic schema records so the shared
 * TypedObjectListScreen/-ConfigScreen can edit the "ai" and "targeting"
 * arrays. Addons register alongside AiGoalDef.REGISTRY registration.
 */
public final class GoalSchemas {

    private static final Map<String, EffectSchema> GOALS = new LinkedHashMap<>();
    private static final Map<String, EffectSchema> TARGETS = new LinkedHashMap<>();

    public static void registerGoal(EffectSchema schema) { GOALS.put(schema.typeId(), schema); }
    public static void registerTarget(EffectSchema schema) { TARGETS.put(schema.typeId(), schema); }

    public static Map<String, EffectSchema> goals() { return GOALS; }
    public static Map<String, EffectSchema> targets() { return TARGETS; }

    private static String id(String path) { return "myrpg_entities:" + path; }

    static {
        registerGoal(new EffectSchema(id("melee_attack"), "Melee Attack", "COMBAT", List.of(
                FieldSpec.number("PRIORITY", "priority", "2"),
                FieldSpec.number("SPEED", "speed", "1.0"))));
        registerGoal(new EffectSchema(id("ranged_attack"), "Ranged Attack", "COMBAT", List.of(
                FieldSpec.number("PRIORITY", "priority", "2"),
                FieldSpec.number("SPEED", "speed", "1.0"),
                FieldSpec.number("INTERVAL (ticks)", "interval", "30"),
                FieldSpec.number("RANGE", "range", "15"))));
        registerGoal(new EffectSchema(id("flee_low_health"), "Flee At Low Health", "COMBAT", List.of(
                FieldSpec.number("PRIORITY", "priority", "1"),
                FieldSpec.number("SPEED", "speed", "1.25"),
                FieldSpec.number("THRESHOLD (0-1)", "threshold", "0.3"))));
        registerGoal(new EffectSchema(id("guard_position"), "Guard Position", "MOVEMENT", List.of(
                FieldSpec.number("PRIORITY", "priority", "3"),
                FieldSpec.number("SPEED", "speed", "1.0"),
                FieldSpec.number("RADIUS", "radius", "16"))));
        registerGoal(new EffectSchema(id("follow_player"), "Follow Player", "MOVEMENT", List.of(
                FieldSpec.number("PRIORITY", "priority", "4"),
                FieldSpec.number("SPEED", "speed", "1.0"),
                FieldSpec.number("STOP DISTANCE", "stop_distance", "3"),
                FieldSpec.number("RANGE", "range", "32"))));
        registerGoal(new EffectSchema(id("avoid_entity"), "Avoid Entity", "MOVEMENT", List.of(
                FieldSpec.number("PRIORITY", "priority", "4"),
                FieldSpec.text("ENTITY", "entity", "minecraft:creeper"),
                FieldSpec.number("DISTANCE", "distance", "8"),
                FieldSpec.number("SPEED", "speed", "1.2"))));
        registerGoal(new EffectSchema(id("random_walk"), "Random Wander", "IDLE", List.of(
                FieldSpec.number("PRIORITY", "priority", "5"),
                FieldSpec.number("SPEED", "speed", "1.0"))));
        registerGoal(new EffectSchema(id("look_at_player"), "Look At Player", "IDLE", List.of(
                FieldSpec.number("PRIORITY", "priority", "7"),
                FieldSpec.number("RANGE", "range", "8"))));
        registerGoal(new EffectSchema(id("look_around"), "Random Look Around", "IDLE", List.of(
                FieldSpec.number("PRIORITY", "priority", "8"))));

        registerTarget(new EffectSchema(id("retaliate"), "Retaliate When Attacked", "TARGETING", List.of(
                FieldSpec.number("PRIORITY", "priority", "1"))));
        registerTarget(new EffectSchema(id("player"), "Attack Players", "TARGETING", List.of(
                FieldSpec.number("PRIORITY", "priority", "2"))));
        registerTarget(new EffectSchema(id("entity_type"), "Attack Entity Type", "TARGETING", List.of(
                FieldSpec.number("PRIORITY", "priority", "3"),
                FieldSpec.text("ENTITY", "entity", "minecraft:zombie"))));
    }

    private GoalSchemas() {}
}
