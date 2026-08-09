package com.myyyst.myrpg.core.condition.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.core.condition.RpgCondition;
import com.myyyst.myrpg.core.variable.VarValue;
import com.myyyst.myrpg.core.variable.Variables;

import java.util.Optional;

/** variable / variable_exists / variable_compare. */
public final class VariableConditions {

    /**
     * Compares a variable against a constant.
     * { "type": "myrpg_core:variable", "scope": "player", "name": "karma",
     *   "operator": ">=", "value": 10, "default": 0 }
     * String form: "value": is matched with == / != only.
     */
    public record Variable(String scope, String name, String operator,
                           VarValue value, Optional<VarValue> defaultValue) implements RpgCondition {
        public static final MapCodec<Variable> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("scope", "player").forGetter(Variable::scope),
                Codec.STRING.fieldOf("name").forGetter(Variable::name),
                Codec.STRING.optionalFieldOf("operator", "==").forGetter(Variable::operator),
                VarValue.CODEC.fieldOf("value").forGetter(Variable::value),
                VarValue.CODEC.optionalFieldOf("default").forGetter(Variable::defaultValue)
        ).apply(i, Variable::new));

        @Override
        public boolean test(ConditionContext ctx) {
            VarValue current = Variables.get(ctx.self().level(), scope, name, ctx.player())
                    .or(this::defaultValue).orElse(null);
            if (current == null) return false;
            return compare(current, operator, value);
        }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    public record VariableExists(String scope, String name) implements RpgCondition {
        public static final MapCodec<VariableExists> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("scope", "player").forGetter(VariableExists::scope),
                Codec.STRING.fieldOf("name").forGetter(VariableExists::name)
        ).apply(i, VariableExists::new));

        @Override
        public boolean test(ConditionContext ctx) {
            return Variables.get(ctx.self().level(), scope, name, ctx.player()).isPresent();
        }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    /** Compares two variables to each other. */
    public record VariableCompare(String scopeA, String nameA, String operator,
                                  String scopeB, String nameB) implements RpgCondition {
        public static final MapCodec<VariableCompare> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("scope_a", "player").forGetter(VariableCompare::scopeA),
                Codec.STRING.fieldOf("name_a").forGetter(VariableCompare::nameA),
                Codec.STRING.optionalFieldOf("operator", "==").forGetter(VariableCompare::operator),
                Codec.STRING.optionalFieldOf("scope_b", "player").forGetter(VariableCompare::scopeB),
                Codec.STRING.fieldOf("name_b").forGetter(VariableCompare::nameB)
        ).apply(i, VariableCompare::new));

        @Override
        public boolean test(ConditionContext ctx) {
            var a = Variables.get(ctx.self().level(), scopeA, nameA, ctx.player()).orElse(null);
            var b = Variables.get(ctx.self().level(), scopeB, nameB, ctx.player()).orElse(null);
            if (a == null || b == null) return false;
            return compare(a, operator, b);
        }
        @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
    }

    private static boolean compare(VarValue a, String operator, VarValue b) {
        if (a.isNumber() && b.isNumber()) {
            double x = a.asNumber(), y = b.asNumber();
            return switch (operator) {
                case "!=" -> x != y;
                case ">" -> x > y;
                case ">=" -> x >= y;
                case "<" -> x < y;
                case "<=" -> x <= y;
                default -> x == y;
            };
        }
        // string comparison: equality operators only
        boolean equal = a.asString().equals(b.asString());
        return "!=".equals(operator) ? !equal : equal;
    }

    private VariableConditions() {}
}