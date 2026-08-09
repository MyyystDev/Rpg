package com.myyyst.myrpg.core.stat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.registry.DispatchRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jspecify.annotations.Nullable;

/**
 * A continuous effect active while an entity's stat sits inside a stage.
 * Lifecycle: apply() on stage enter, tick() every tick while inside,
 * remove() on stage exit. Implementations must be idempotent-safe:
 * apply/remove pairs can repeat (relog re-applies), so removal must not
 * assume prior application.
 *
 * Extensible: addons register their own types into REGISTRY.
 * JSON: { "type": "myrpg_core:attribute", ... }
 */
public interface StageEffect {

    /** Called once when the owner enters the stage (and on relog while inside). */
    void apply(LivingEntity owner, Identifier statId, String stageId);

    /** Called every tick while the owner remains in the stage. */
    default void tick(LivingEntity owner) {}

    /** Called once when the owner exits the stage (and must tolerate never-applied). */
    void remove(LivingEntity owner, Identifier statId, String stageId);

    MapCodec<? extends StageEffect> codec();

    DispatchRegistry<StageEffect> REGISTRY = new DispatchRegistry<>(StageEffect::codec);
    Codec<StageEffect> CODEC = REGISTRY.codec();

    static void bootstrap() {
        REGISTRY.register(core("attribute"), AttributeEffect.CODEC);
        REGISTRY.register(core("periodic_damage"), PeriodicDamage.CODEC);
    }

    private static Identifier core(String path) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, path);
    }

    // ---------------------------------------------------------------- builtins

    /**
     * Applies a vanilla attribute modifier while in the stage.
     * operation: "add_value" | "add_multiplied_base" | "add_multiplied_total".
     * The modifier id derives from stat+stage, so re-application replaces.
     */
    record AttributeEffect(Identifier attribute, String operation, double value) implements StageEffect {
        public static final MapCodec<AttributeEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("attribute").forGetter(AttributeEffect::attribute),
                Codec.STRING.optionalFieldOf("operation", "add_value").forGetter(AttributeEffect::operation),
                Codec.DOUBLE.fieldOf("value").forGetter(AttributeEffect::value)
        ).apply(i, AttributeEffect::new));

        @Override
        public void apply(LivingEntity owner, Identifier statId, String stageId) {
            AttributeInstance instance = resolve(owner);
            if (instance == null) return;
            Identifier modifierId = modifierId(statId, stageId);
            instance.removeModifier(modifierId);
            instance.addPermanentModifier(new AttributeModifier(modifierId, value, mapOperation()));
        }

        @Override
        public void remove(LivingEntity owner, Identifier statId, String stageId) {
            AttributeInstance instance = resolve(owner);
            if (instance != null) instance.removeModifier(modifierId(statId, stageId));
        }

        private Identifier modifierId(Identifier statId, String stageId) {
            return Identifier.fromNamespaceAndPath(Constants.MOD_ID,
                    "stage/" + statId.getNamespace() + "/" + statId.getPath() + "/" + stageId);
        }

        @Nullable
        private AttributeInstance resolve(LivingEntity owner) {
            Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.get(attribute).orElse(null);
            // NOTE drift: registry-lookup shape — mirror StatStore's compiled spelling.
            if (holder == null) {
                Constants.LOG.warn("[myrpg] stage effect references unknown attribute {}", attribute);
                return null;
            }
            return owner.getAttribute(holder);
        }

        private AttributeModifier.Operation mapOperation() {
            return switch (operation) {
                case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                default -> AttributeModifier.Operation.ADD_VALUE;
            };
        }

        @Override public MapCodec<? extends StageEffect> codec() { return CODEC; }
    }

    /** Deals damage every interval ticks while in the stage. */
    record PeriodicDamage(float damage, int interval) implements StageEffect {
        public static final MapCodec<PeriodicDamage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("damage").forGetter(PeriodicDamage::damage),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("interval", 100).forGetter(PeriodicDamage::interval)
        ).apply(i, PeriodicDamage::new));

        @Override public void apply(LivingEntity owner, Identifier statId, String stageId) {}
        @Override public void remove(LivingEntity owner, Identifier statId, String stageId) {}

        @Override
        public void tick(LivingEntity owner) {
            if (owner.level().getGameTime() % interval == 0) {
                // NOTE drift: hurt vs hurtServer — mirror the Damage action's spelling.
                owner.hurt(owner.damageSources().magic(), damage);
            }
        }

        @Override public MapCodec<? extends StageEffect> codec() { return CODEC; }
    }
}