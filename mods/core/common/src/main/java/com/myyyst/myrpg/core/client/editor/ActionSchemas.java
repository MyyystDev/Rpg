package com.myyyst.myrpg.core.client.editor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.myyyst.myrpg.core.client.editor.EffectSchemas.FieldSpec;

/**
 * Client-side form schemas for registered action types - the action counterpart of
 * {@link EffectSchemas} (whose {@code FieldSpec} it reuses).
 *
 * <p>Each entry describes the fields the editor shows for one {@code RpgAction} type.
 * A type with no schema here cannot be built through the UI, though hand-written JSON
 * using it still loads normally.</p>
 */
public final class ActionSchemas {

    /**
     * The editor form for one action type.
     * @param typeId must match the id used in {@code RpgAction.REGISTRY}
     */
    public record ActionSchema(String typeId, String label, String category, List<FieldSpec> fields) {}

    /** Insertion-ordered so the picker groups actions in a stable order. */
    private static final Map<String, ActionSchema> SCHEMAS = new LinkedHashMap<>();

    /** Addons call this alongside their {@code RpgAction.REGISTRY.register}. */
    public static void register(ActionSchema schema) { SCHEMAS.put(schema.typeId(), schema); }
    /** Every known schema, keyed by type id. */
    public static Map<String, ActionSchema> all() { return SCHEMAS; }

    // Schemas for core's own actions, mirroring RpgAction.bootstrap().
    // The "__var_value" keys are handled specially by the config screen, which decides
    // whether to write {"number": x} or {"string": "x"} for a VarValue.
    static {
        register(new ActionSchema("myrpg_core:modify_stat", "Modify Stat", "STATS", List.of(
                FieldSpec.text("STAT", "stat", "test:corruption"),
                FieldSpec.cycle("OPERATION", "operation", "add", "subtract", "multiply", "divide", "set"),
                FieldSpec.number("VALUE", "value", "1"))));
        register(new ActionSchema("myrpg_core:damage", "Damage", "COMBAT", List.of(
                FieldSpec.number("AMOUNT", "amount", "1"))));
        register(new ActionSchema("myrpg_core:apply_effect", "Apply Potion Effect", "COMBAT", List.of(
                FieldSpec.text("EFFECT", "effect", "minecraft:slowness"),
                FieldSpec.number("DURATION (ticks)", "duration", "100"),
                FieldSpec.number("AMPLIFIER", "amplifier", "0"))));
        register(new ActionSchema("myrpg_core:play_sound", "Play Sound", "AUDIO", List.of(
                FieldSpec.text("SOUND", "sound", "minecraft:entity.vex.ambient"),
                FieldSpec.number("VOLUME", "volume", "1.0"),
                FieldSpec.number("PITCH", "pitch", "1.0"))));
        register(new ActionSchema("myrpg_core:speak", "Speak", "TEXT", List.of(
                FieldSpec.text("TEXT", "text", "..."),
                FieldSpec.number("RANGE", "range", "16"))));
        register(new ActionSchema("myrpg_core:run_function", "Run Function", "CUSTOM", List.of(
                FieldSpec.text("FUNCTION", "function", "mypack:my_function"))));
        register(new ActionSchema("myrpg_core:set_variable", "Set Variable", "VARIABLES", List.of(
                FieldSpec.cycle("SCOPE", "scope", "player", "world"),
                FieldSpec.text("NAME", "name", "my_variable"),
                FieldSpec.text("VALUE (number or string)", "__var_value", "1"))));
        register(new ActionSchema("myrpg_core:modify_variable", "Modify Variable", "VARIABLES", List.of(
                FieldSpec.cycle("SCOPE", "scope", "player", "world"),
                FieldSpec.text("NAME", "name", "my_variable"),
                FieldSpec.cycle("OPERATION", "operation", "add", "subtract", "multiply", "divide", "set"),
                FieldSpec.number("VALUE", "value", "1"),
                FieldSpec.number("DEFAULT", "default", "0"))));
    }

    /** Static-only registry: never instantiated. */
    private ActionSchemas() {}
}