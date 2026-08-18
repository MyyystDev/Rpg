package com.myyyst.myrpg.core.client.editor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.myyyst.myrpg.core.client.editor.EffectSchemas.FieldSpec;

/**
 * Client-side form schemas for registered condition types - the condition counterpart of
 * {@link EffectSchemas} (whose {@code FieldSpec} it reuses).
 *
 * <p>Only flat, field-based conditions appear here. The combinators (and/or/not/...) nest
 * other conditions and would need a recursive editor, so they are intentionally absent -
 * see the note at the end of the initialiser.</p>
 */
public final class ConditionSchemas {

    /**
     * The editor form for one condition type.
     * @param typeId must match the id used in {@code RpgCondition.REGISTRY}
     */
    public record ConditionSchema(String typeId, String label, String category, List<FieldSpec> fields) {}

    /** Insertion-ordered so the picker groups conditions in a stable order. */
    private static final Map<String, ConditionSchema> SCHEMAS = new LinkedHashMap<>();

    /** Addons call this alongside their {@code RpgCondition.REGISTRY.register}. */
    public static void register(ConditionSchema schema) { SCHEMAS.put(schema.typeId(), schema); }
    /** Every known schema, keyed by type id. */
    public static Map<String, ConditionSchema> all() { return SCHEMAS; }

    // Schemas for core's own conditions, mirroring CoreConditions.bootstrap().
    static {
        register(new ConditionSchema("myrpg_core:stat", "Stat Compare", "STATS", List.of(
                FieldSpec.text("STAT", "stat", "test:corruption"),
                FieldSpec.cycle("OPERATOR", "operator", ">=", ">", "==", "!=", "<", "<="),
                FieldSpec.number("VALUE", "value", "0"))));

        register(new ConditionSchema("myrpg_core:variable", "Variable Compare", "VARIABLES", List.of(
                FieldSpec.cycle("SCOPE", "scope", "player", "world"),
                FieldSpec.text("NAME", "name", "my_variable"),
                FieldSpec.cycle("OPERATOR", "operator", "==", "!=", ">", ">=", "<", "<="),
                FieldSpec.text("VALUE (number or string)", "__var_value", "1"),
                FieldSpec.text("DEFAULT (number or string)", "__var_value_default", "0"))));

        register(new ConditionSchema("myrpg_core:variable_exists", "Variable Exists", "VARIABLES", List.of(
                FieldSpec.cycle("SCOPE", "scope", "player", "world"),
                FieldSpec.text("NAME", "name", "my_variable"))));

        register(new ConditionSchema("myrpg_core:health_below", "Health Below", "COMBAT", List.of(
                FieldSpec.number("FRACTION (0-1)", "value", "0.5"))));

        register(new ConditionSchema("myrpg_core:random_chance", "Random Chance", "LOGIC", List.of(
                FieldSpec.number("CHANCE (0-1)", "chance", "0.25"),
                FieldSpec.cycle("PER PLAYER SEED", "per_player_seed", "false", "true"),
                FieldSpec.number("SEED SALT", "seed_salt", "0"))));

        register(new ConditionSchema("myrpg_core:reference", "Named Condition", "LOGIC", List.of(
                FieldSpec.text("CONDITION ID", "id", "mypack:is_worthy"))));

        register(new ConditionSchema("myrpg_core:debug_flag", "Debug Flag", "LOGIC", List.of()));
        register(new ConditionSchema("myrpg_core:always_true", "Always True", "LOGIC", List.of()));
        register(new ConditionSchema("myrpg_core:always_false", "Always False", "LOGIC", List.of()));
        // Combinators (and/or/not/xor/any_of_count) hold nested condition
        // lists — they need the recursive form and are v2 for the editor.
        // Hand-written JSON using them still loads and round-trips fine.
    }

    /** Static-only registry: never instantiated. */
    private ConditionSchemas() {}
}