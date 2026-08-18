package com.myyyst.myrpg.core.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.core.registry.DispatchRegistry;
import com.myyyst.myrpg.core.stat.StatHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public interface RpgCondition {

    boolean test(ConditionContext ctx);

    MapCodec<? extends RpgCondition> codec();

    record ConditionContext(LivingEntity self) {}

    DispatchRegistry<RpgCondition> REGISTRY = new DispatchRegistry<>(RpgCondition::codec);
    Codec<RpgCondition> CODEC = REGISTRY.codec();

    /** Core's own builtins. Other mods register theirs from their own init. */
    static void bootstrap() {
        REGISTRY.register(core("random_chance"), RandomChance.CODEC);
        REGISTRY.register(core("health_below"), HealthBelow.CODEC);
        REGISTRY.register(core("all_of"), AllOf.CODEC);
        REGISTRY.register(core("any_of"), AnyOf.CODEC);
        REGISTRY.register(core("not"), Not.CODEC);
        REGISTRY.register(core("stat_at_least"), StatAtLeast.CODEC);
        REGISTRY.register(core("stat_below"), StatBelow.CODEC);
    }

    private static Identifier core(String path) {
        return Identifier.fromNamespaceAndPath("rpg_core", path);
    }

    // ---------------------------------------------------------------- built-ins

    /** True when self carries stats and the stat is >= value. */
    record StatAtLeast(Identifier stat, double value) implements RpgCondition {
        static final MapCodec<StatAtLeast> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("stat").forGetter(StatAtLeast::stat),
                Codec.DOUBLE.fieldOf("value").forGetter(StatAtLeast::value)
        ).apply(i, StatAtLeast::new));

        @Override public boolean test(ConditionContext ctx) {
            return ctx.self() instanceof StatHolder holder
                    && holder.rpgStats().get(stat) >= value;
        }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    record StatBelow(Identifier stat, double value) implements RpgCondition {
        static final MapCodec<StatBelow> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("stat").forGetter(StatBelow::stat),
                Codec.DOUBLE.fieldOf("value").forGetter(StatBelow::value)
        ).apply(i, StatBelow::new));

        @Override public boolean test(ConditionContext ctx) {
            return ctx.self() instanceof StatHolder holder
                    && holder.rpgStats().get(stat) < value;
        }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    record HealthBelow(double value) implements RpgCondition {
        static final MapCodec<HealthBelow> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("value").forGetter(HealthBelow::value)
        ).apply(i, HealthBelow::new));

        @Override public boolean test(ConditionContext ctx) {
            LivingEntity self = ctx.self();
            return self.getHealth() / self.getMaxHealth() < value;
        }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    record RandomChance(double chance) implements RpgCondition {
        static final MapCodec<RandomChance> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.doubleRange(0, 1).fieldOf("chance").forGetter(RandomChance::chance)
        ).apply(i, RandomChance::new));

        @Override public boolean test(ConditionContext ctx) {
            return ctx.self().getRandom().nextDouble() < chance;
        }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    record AllOf(List<RpgCondition> conditions) implements RpgCondition {
        static final MapCodec<AllOf> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                RpgCondition.CODEC.listOf().fieldOf("conditions").forGetter(AllOf::conditions)
        ).apply(i, AllOf::new));

        @Override public boolean test(ConditionContext ctx) {
            return conditions.stream().allMatch(c -> c.test(ctx));
        }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    record AnyOf(List<RpgCondition> conditions) implements RpgCondition {
        static final MapCodec<AnyOf> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                RpgCondition.CODEC.listOf().fieldOf("conditions").forGetter(AnyOf::conditions)
        ).apply(i, AnyOf::new));

        @Override public boolean test(ConditionContext ctx) {
            return conditions.stream().anyMatch(c -> c.test(ctx));
        }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    record Not(RpgCondition condition) implements RpgCondition {
        static final MapCodec<Not> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                RpgCondition.CODEC.fieldOf("condition").forGetter(Not::condition)
        ).apply(i, Not::new));

        @Override public boolean test(ConditionContext ctx) { return !condition.test(ctx); }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }
}