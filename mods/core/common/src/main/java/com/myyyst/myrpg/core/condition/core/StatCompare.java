package com.myyyst.myrpg.core.condition.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.core.condition.RpgCondition;
import com.myyyst.myrpg.core.stat.StatHolder;
import com.myyyst.myrpg.core.stat.StatStore;
import net.minecraft.resources.Identifier;

/**
 * Compares a stat on self against a constant.
 * { "type": "myrpg_core:stat", "stat": "mypack:corruption", "operator": ">=", "value": 50 }
 * Operators: == != > >= < <=. Non-StatHolder self fails all comparisons.
 */
public record StatCompare(Identifier stat, String operator, double value) implements RpgCondition {

    public static final MapCodec<StatCompare> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("stat").forGetter(StatCompare::stat),
            Codec.STRING.optionalFieldOf("operator", ">=").forGetter(StatCompare::operator),
            Codec.DOUBLE.fieldOf("value").forGetter(StatCompare::value)
    ).apply(i, StatCompare::new));

    // StatCompare.test:
    @Override
    public boolean test(ConditionContext ctx) {
        StatStore store = StatHolder.resolve(ctx.self());
        if (store == null) return false;         // no stats at all -> cannot match
        double current = store.get(stat);        // falls back to the stat's default value
        // Unknown operators are treated as ">=", matching the codec's default.
        return switch (operator) {
            case "==" -> current == value;
            case "!=" -> current != value;
            case ">" -> current > value;
            case "<" -> current < value;
            case "<=" -> current <= value;
            default -> current >= value;
        };
    }

    @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
}