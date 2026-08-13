package com.myyyst.myrpg.core.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.condition.RpgCondition;
import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.core.data.EffectDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Custom-effect conditions (custom-status-effects-system doc).
 * All of them read the context's SELF entity. Entities without an effect
 * store simply never match.
 */
public final class EffectConditions {

    /** Called once from MyRpgCommon.init(). */
    public static void bootstrap() {
        register("has_effect", HasEffect.CODEC);
        register("effect_stacks", EffectStacks.CODEC);
        register("effect_level", EffectLevel.CODEC);
        register("has_effect_category", HasEffectCategory.CODEC);
        register("has_effect_tag", HasEffectTag.CODEC);
    }

    private static void register(String path, MapCodec<? extends RpgCondition> codec) {
        RpgCondition.REGISTRY.register(
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), codec);
    }

    @Nullable
    private static EffectStore storeOf(RpgCondition.ConditionContext ctx) {
        LivingEntity self = ctx.self();
        return self == null ? null : EffectHolder.resolve(self);
    }

    // ------------------------------------------------------------ builtins

    /** { "type": "myrpg_core:has_effect", "effect": "mypack:bleeding" } */
    public record HasEffect(Identifier effect) implements RpgCondition {
        static final MapCodec<HasEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("effect").forGetter(HasEffect::effect)
        ).apply(i, HasEffect::new));

        @Override public boolean test(ConditionContext ctx) {
            EffectStore store = storeOf(ctx);
            return store != null && store.has(effect);
        }

        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    /**
     * { "type": "myrpg_core:effect_stacks", "effect": "...",
     *   "at_least": 3, "at_most": 5 }   (both bounds optional)
     */
    public record EffectStacks(Identifier effect, int atLeast, int atMost) implements RpgCondition {
        static final MapCodec<EffectStacks> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("effect").forGetter(EffectStacks::effect),
                Codec.INT.optionalFieldOf("at_least", 1).forGetter(EffectStacks::atLeast),
                Codec.INT.optionalFieldOf("at_most", Integer.MAX_VALUE).forGetter(EffectStacks::atMost)
        ).apply(i, EffectStacks::new));

        @Override public boolean test(ConditionContext ctx) {
            EffectStore store = storeOf(ctx);
            EffectInstance instance = store == null ? null : store.get(effect);
            return instance != null && instance.stacks >= atLeast && instance.stacks <= atMost;
        }

        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    /**
     * { "type": "myrpg_core:effect_level", "effect": "...",
     *   "at_least": 2, "at_most": 4 }   (both bounds optional)
     */
    public record EffectLevel(Identifier effect, int atLeast, int atMost) implements RpgCondition {
        static final MapCodec<EffectLevel> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("effect").forGetter(EffectLevel::effect),
                Codec.INT.optionalFieldOf("at_least", 1).forGetter(EffectLevel::atLeast),
                Codec.INT.optionalFieldOf("at_most", Integer.MAX_VALUE).forGetter(EffectLevel::atMost)
        ).apply(i, EffectLevel::new));

        @Override public boolean test(ConditionContext ctx) {
            EffectStore store = storeOf(ctx);
            EffectInstance instance = store == null ? null : store.get(effect);
            return instance != null && instance.level >= atLeast && instance.level <= atMost;
        }

        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    /** { "type": "myrpg_core:has_effect_category", "category": "harmful" } */
    public record HasEffectCategory(String category) implements RpgCondition {
        static final MapCodec<HasEffectCategory> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("category").forGetter(HasEffectCategory::category)
        ).apply(i, HasEffectCategory::new));

        @Override public boolean test(ConditionContext ctx) {
            EffectStore store = storeOf(ctx);
            if (store == null) return false;
            for (EffectInstance instance : store.all()) {
                EffectDefinition def = CoreData.EFFECTS.get(instance.effectId).orElse(null);
                if (def != null && def.category().equals(category)) return true;
            }
            return false;
        }

        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    /** { "type": "myrpg_core:has_effect_tag", "tag": "rpg:crowd_control" } */
    public record HasEffectTag(String tag) implements RpgCondition {
        static final MapCodec<HasEffectTag> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("tag").forGetter(HasEffectTag::tag)
        ).apply(i, HasEffectTag::new));

        @Override public boolean test(ConditionContext ctx) {
            EffectStore store = storeOf(ctx);
            if (store == null) return false;
            for (EffectInstance instance : store.all()) {
                EffectDefinition def = CoreData.EFFECTS.get(instance.effectId).orElse(null);
                if (def != null && def.hasTag(tag)) return true;
            }
            return false;
        }

        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    private EffectConditions() {}
}
