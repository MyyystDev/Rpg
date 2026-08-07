package com.myyyst.myrpg.core.condition.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.core.condition.RpgCondition;

import java.util.List;

/** and / or / not / xor / any_of_count / always_true / always_false. */
public final class Combinators {

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

    public record Not(RpgCondition condition) implements RpgCondition {
        public static final MapCodec<Not> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                RpgCondition.CODEC.fieldOf("condition").forGetter(Not::condition)
        ).apply(i, Not::new));
        @Override public boolean test(ConditionContext ctx) { return !condition.test(ctx); }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

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

    public record AlwaysTrue() implements RpgCondition {
        public static final MapCodec<AlwaysTrue> CODEC = MapCodec.unit(new AlwaysTrue());
        @Override public boolean test(ConditionContext ctx) { return true; }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    public record AlwaysFalse() implements RpgCondition {
        public static final MapCodec<AlwaysFalse> CODEC = MapCodec.unit(new AlwaysFalse());
        @Override public boolean test(ConditionContext ctx) { return false; }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    private Combinators() {}
}