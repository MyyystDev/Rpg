package com.myyyst.myrpg.core.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.action.RpgAction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * Custom-effect actions (custom-status-effects-system doc). All of them act
 * on the context's SELF entity.
 *
 * NOTE: "myrpg_core:apply_effect" is already taken by the VANILLA MobEffect
 * action, so the custom-effect family uses "apply_rpg_effect" /
 * "remove_rpg_effect".
 */
public final class EffectActions {

    /** Called once from MyRpgCommon.init(). */
    public static void bootstrap() {
        register("apply_rpg_effect", ApplyRpgEffect.CODEC);
        register("remove_rpg_effect", RemoveRpgEffect.CODEC);
        register("add_effect_stack", AddEffectStack.CODEC);
        register("remove_effect_stack", RemoveEffectStack.CODEC);
        register("remove_effect_category", RemoveEffectCategory.CODEC);
        register("remove_effect_tag", RemoveEffectTag.CODEC);
        register("clear_effects", ClearEffects.CODEC);
    }

    /** Registers one action type under {@code myrpg_core:<path>}. */
    private static void register(String path, MapCodec<? extends RpgAction> codec) {
        RpgAction.REGISTRY.register(
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), codec);
    }

    // ------------------------------------------------------------ builtins

    /**
     * { "type": "myrpg_core:apply_rpg_effect", "effect": "mypack:stunned",
     *   "duration": 40, "level": 1, "stacks": 1 }
     * duration omitted / -1 = the definition's default.
     */
    public record ApplyRpgEffect(Identifier effect, int duration, int level, int stacks)
            implements RpgAction {
        static final MapCodec<ApplyRpgEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("effect").forGetter(ApplyRpgEffect::effect),
                Codec.INT.optionalFieldOf("duration", -1).forGetter(ApplyRpgEffect::duration),
                Codec.INT.optionalFieldOf("level", 1).forGetter(ApplyRpgEffect::level),
                Codec.INT.optionalFieldOf("stacks", 1).forGetter(ApplyRpgEffect::stacks)
        ).apply(i, ApplyRpgEffect::new));

        @Override public void execute(ActionContext ctx) {
            LivingEntity self = ctx.self();
            // The context player (if any) is recorded as the source, for attribution.
            EffectManager.apply(self, effect, duration, level, stacks,
                    ctx.player() != null ? ctx.player().getUUID() : null);
        }

        @Override public MapCodec<? extends RpgAction> codec() { return CODEC; }
    }

    /** { "type": "myrpg_core:remove_rpg_effect", "effect": "mypack:frozen" } */
    public record RemoveRpgEffect(Identifier effect) implements RpgAction {
        static final MapCodec<RemoveRpgEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("effect").forGetter(RemoveRpgEffect::effect)
        ).apply(i, RemoveRpgEffect::new));

        @Override public void execute(ActionContext ctx) {
            // expired=false -> this counts as a cleanse and fires on_removed, not on_expired
            EffectManager.remove(ctx.self(), effect, false);
        }

        @Override public MapCodec<? extends RpgAction> codec() { return CODEC; }
    }

    /** { "type": "myrpg_core:add_effect_stack", "effect": "...", "count": 1 } */
    public record AddEffectStack(Identifier effect, int count) implements RpgAction {
        static final MapCodec<AddEffectStack> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("effect").forGetter(AddEffectStack::effect),
                Codec.INT.optionalFieldOf("count", 1).forGetter(AddEffectStack::count)
        ).apply(i, AddEffectStack::new));

        @Override public void execute(ActionContext ctx) {
            // Re-applying with extra stacks is how stacking is expressed; -1 keeps the
            // definition's default duration.
            EffectManager.apply(ctx.self(), effect, -1, 1, Math.max(1, count), null);
        }

        @Override public MapCodec<? extends RpgAction> codec() { return CODEC; }
    }

    /** { "type": "myrpg_core:remove_effect_stack", "effect": "...", "count": 1 } */
    public record RemoveEffectStack(Identifier effect, int count) implements RpgAction {
        static final MapCodec<RemoveEffectStack> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("effect").forGetter(RemoveEffectStack::effect),
                Codec.INT.optionalFieldOf("count", 1).forGetter(RemoveEffectStack::count)
        ).apply(i, RemoveEffectStack::new));

        @Override public void execute(ActionContext ctx) {
            EffectManager.removeStacks(ctx.self(), effect, Math.max(1, count));
        }

        @Override public MapCodec<? extends RpgAction> codec() { return CODEC; }
    }

    /** { "type": "myrpg_core:remove_effect_category", "category": "harmful" } */
    public record RemoveEffectCategory(String category) implements RpgAction {
        static final MapCodec<RemoveEffectCategory> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("category").forGetter(RemoveEffectCategory::category)
        ).apply(i, RemoveEffectCategory::new));

        @Override public void execute(ActionContext ctx) {
            EffectManager.removeWhere(ctx.self(), def -> def.category().equals(category));
        }

        @Override public MapCodec<? extends RpgAction> codec() { return CODEC; }
    }

    /** { "type": "myrpg_core:remove_effect_tag", "tag": "rpg:crowd_control" } */
    public record RemoveEffectTag(String tag) implements RpgAction {
        static final MapCodec<RemoveEffectTag> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("tag").forGetter(RemoveEffectTag::tag)
        ).apply(i, RemoveEffectTag::new));

        @Override public void execute(ActionContext ctx) {
            EffectManager.removeWhere(ctx.self(), def -> def.hasTag(tag));
        }

        @Override public MapCodec<? extends RpgAction> codec() { return CODEC; }
    }

    /**
     * { "type": "myrpg_core:clear_effects" }            → everything
     * { "type": "myrpg_core:clear_effects", "category": "harmful" } → one category
     */
    public record ClearEffects(java.util.Optional<String> category) implements RpgAction {
        static final MapCodec<ClearEffects> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("category").forGetter(ClearEffects::category)
        ).apply(i, ClearEffects::new));

        @Override public void execute(ActionContext ctx) {
            EffectManager.removeWhere(ctx.self(),
                    def -> category.isEmpty() || def.category().equals(category.get()));
        }

        @Override public MapCodec<? extends RpgAction> codec() { return CODEC; }
    }

    /** Namespace class for the effect action records: never instantiated. */
    private EffectActions() {}
}
