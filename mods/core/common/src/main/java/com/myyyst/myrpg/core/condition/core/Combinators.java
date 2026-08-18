package com.myyyst.myrpg.core.condition.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.core.condition.RpgCondition;

import java.util.List;

/**
 * and / or / not / xor / any_of_count / always_true / always_false.
 *
 * <p>The boolean algebra of the condition system: these are the only types that nest other
 * conditions, which is what lets a datapack express arbitrarily complex logic without any
 * scripting language. All of them short-circuit.</p>
 */
public final class Combinators {

    /** True when every nested condition passes. An empty list is true (vacuous truth). */
    public record And(List<RpgCondition> conditions) implements RpgCondition {
        public static final MapCodec<And> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                RpgCondition.CODEC.listOf().fieldOf("conditions").forGetter(And::conditions)
        ).apply(i, And::new));
        @Override public boolean test(ConditionContext ctx) {
            for (RpgCondition c : conditions) if (!c.test(ctx)) return false;
            return true;
        }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    /** True when at least one nested condition passes. An empty list is false. */
    public record Or(List<RpgCondition> conditions) implements RpgCondition {
        public static final MapCodec<Or> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                RpgCondition.CODEC.listOf().fieldOf("conditions").forGetter(Or::conditions)
        ).apply(i, Or::new));
        @Override public boolean test(ConditionContext ctx) {
            for (RpgCondition c : conditions) if (c.test(ctx)) return true;
            return false;
        }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    /** Inverts the nested condition. */
    public record Not(RpgCondition condition) implements RpgCondition {
        public static final MapCodec<Not> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                RpgCondition.CODEC.fieldOf("condition").forGetter(Not::condition)
        ).apply(i, Not::new));
        @Override public boolean test(ConditionContext ctx) { return !condition.test(ctx); }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    /** True when exactly one of the two passes. Note both sides are always evaluated. */
    public record Xor(RpgCondition first, RpgCondition second) implements RpgCondition {
        public static final MapCodec<Xor> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                RpgCondition.CODEC.fieldOf("first").forGetter(Xor::first),
                RpgCondition.CODEC.fieldOf("second").forGetter(Xor::second)
        ).apply(i, Xor::new));
        @Override public boolean test(ConditionContext ctx) {
            return first.test(ctx) ^ second.test(ctx);
        }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    /** "At least {@code count} of these pass" - a threshold gate, e.g. 2 of 3 quest steps done. */
    public record AnyOfCount(int count, List<RpgCondition> conditions) implements RpgCondition {
        public static final MapCodec<AnyOfCount> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("count").forGetter(AnyOfCount::count),
                RpgCondition.CODEC.listOf().fieldOf("conditions").forGetter(AnyOfCount::conditions)
        ).apply(i, AnyOfCount::new));
        @Override public boolean test(ConditionContext ctx) {
            int passed = 0;
            for (RpgCondition c : conditions) {
                if (c.test(ctx) && ++passed >= count) return true;   // early exit
            }
            return false;
        }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    /** Constant true - handy as a placeholder while authoring a pack. Takes no fields. */
    public record AlwaysTrue() implements RpgCondition {
        public static final MapCodec<AlwaysTrue> CODEC = MapCodec.unit(new AlwaysTrue());
        @Override public boolean test(ConditionContext ctx) { return true; }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    /** Constant false - lets an author disable a rule without deleting it. */
    public record AlwaysFalse() implements RpgCondition {
        public static final MapCodec<AlwaysFalse> CODEC = MapCodec.unit(new AlwaysFalse());
        @Override public boolean test(ConditionContext ctx) { return false; }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    /** Namespace class for the combinator records: never instantiated. */
    private Combinators() {}
}