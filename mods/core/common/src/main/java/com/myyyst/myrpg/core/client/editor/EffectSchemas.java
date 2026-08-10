package com.myyyst.myrpg.core.client.editor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-side form schemas for registered stage-effect types. */
public final class EffectSchemas {

    public enum FieldType { STRING, NUMBER, CYCLE }

    public record FieldSpec(String label, String key, FieldType type,
                            String[] options, String fallback) {
        public static FieldSpec text(String label, String key, String fallback) {
            return new FieldSpec(label, key, FieldType.STRING, null, fallback);
        }
        public static FieldSpec number(String label, String key, String fallback) {
            return new FieldSpec(label, key, FieldType.NUMBER, null, fallback);
        }
        public static FieldSpec cycle(String label, String key, String... options) {
            return new FieldSpec(label, key, FieldType.CYCLE, options, options[0]);
        }
    }

    public record EffectSchema(String typeId, String label, String category, List<FieldSpec> fields) {}

    private static final Map<String, EffectSchema> SCHEMAS = new LinkedHashMap<>();

    /** Addons call this alongside StageEffect.REGISTRY.register. */
    public static void register(EffectSchema schema) {
        SCHEMAS.put(schema.typeId(), schema);
    }

    public static Map<String, EffectSchema> all() { return SCHEMAS; }

    static {
        register(new EffectSchema("myrpg_core:attribute", "Attribute Modifier", "ATTRIBUTES", List.of(
                FieldSpec.text("ATTRIBUTE", "attribute", "minecraft:movement_speed"),
                FieldSpec.cycle("OPERATION", "operation", "add_value", "add_multiplied_base", "add_multiplied_total"),
                FieldSpec.number("VALUE", "value", "0.0"))));
        register(new EffectSchema("myrpg_core:periodic_damage", "Periodic Damage", "STATUS", List.of(
                FieldSpec.number("DAMAGE", "damage", "1"),
                FieldSpec.number("INTERVAL (ticks)", "interval", "100"))));
    }

    private EffectSchemas() {}
}