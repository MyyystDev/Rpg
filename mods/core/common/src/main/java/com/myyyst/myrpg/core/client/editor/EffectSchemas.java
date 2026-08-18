package com.myyyst.myrpg.core.client.editor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side form schemas for registered stage-effect types.
 *
 * <p>The server-side {@code DispatchRegistry} knows how to <em>parse</em> a typed object;
 * it says nothing about how to <em>edit</em> one. These schemas fill that gap: each entry
 * declares the fields the editor should show for a given type, and
 * {@code TypedObjectConfigScreen} renders a form from it generically.</p>
 *
 * <p>{@link FieldSpec} is shared by the condition and action schema classes too, which is
 * why they static-import it from here.</p>
 */
public final class EffectSchemas {

    /** How a field is edited: free text, numeric text, or a fixed set cycled by clicking. */
    public enum FieldType { STRING, NUMBER, CYCLE }

    /**
     * One editable field of a typed object.
     *
     * @param label    caption shown in the form
     * @param key      JSON key it reads/writes (dotted paths supported via {@code JsonEdit})
     * @param options  the allowed values for CYCLE fields, null otherwise
     * @param fallback value used when the JSON has no entry yet
     */
    public record FieldSpec(String label, String key, FieldType type,
                            String[] options, String fallback) {
        /** Free-text field. */
        public static FieldSpec text(String label, String key, String fallback) {
            return new FieldSpec(label, key, FieldType.STRING, null, fallback);
        }
        /** Numeric field. */
        public static FieldSpec number(String label, String key, String fallback) {
            return new FieldSpec(label, key, FieldType.NUMBER, null, fallback);
        }
        /** Cycle field; the first option doubles as the default. */
        public static FieldSpec cycle(String label, String key, String... options) {
            return new FieldSpec(label, key, FieldType.CYCLE, options, options[0]);
        }
    }

    /**
     * The editor form for one stage-effect type.
     *
     * @param typeId   must match the id used in {@code StageEffect.REGISTRY}
     * @param category groups the type in the picker screen
     */
    public record EffectSchema(String typeId, String label, String category, List<FieldSpec> fields) {}

    /** Insertion-ordered so the picker lists types in a stable, authored order. */
    private static final Map<String, EffectSchema> SCHEMAS = new LinkedHashMap<>();

    /** Addons call this alongside StageEffect.REGISTRY.register. */
    public static void register(EffectSchema schema) {
        SCHEMAS.put(schema.typeId(), schema);
    }

    /** Every known schema, keyed by type id. */
    public static Map<String, EffectSchema> all() { return SCHEMAS; }

    // Schemas for core's own stage-effect types, mirroring StageEffect.bootstrap().
    static {
        register(new EffectSchema("myrpg_core:attribute", "Attribute Modifier", "ATTRIBUTES", List.of(
                FieldSpec.text("ATTRIBUTE", "attribute", "minecraft:movement_speed"),
                FieldSpec.cycle("OPERATION", "operation", "add_value", "add_multiplied_base", "add_multiplied_total"),
                FieldSpec.number("VALUE", "value", "0.0"))));
        register(new EffectSchema("myrpg_core:periodic_damage", "Periodic Damage", "STATUS", List.of(
                FieldSpec.number("DAMAGE", "damage", "1"),
                FieldSpec.number("INTERVAL (ticks)", "interval", "100"))));
    }

    /** Static-only registry: never instantiated. */
    private EffectSchemas() {}
}