package com.myyyst.myrpg.core.condition;

import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.condition.core.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

public final class CoreConditions {

    public static void bootstrap() {
        // logic / structural
        register("and", Combinators.And.CODEC);
        register("or", Combinators.Or.CODEC);
        register("not", Combinators.Not.CODEC);
        register("xor", Combinators.Xor.CODEC);
        register("any_of_count", Combinators.AnyOfCount.CODEC);
        register("always_true", Combinators.AlwaysTrue.CODEC);
        register("always_false", Combinators.AlwaysFalse.CODEC);
        register("reference", Reference.CODEC);
        register("random_chance", RandomChance.CODEC);
        register("debug_flag", DebugFlag.CODEC);
        register("stat", StatCompare.CODEC);
        register("variable", VariableConditions.Variable.CODEC);
        register("variable_exists", VariableConditions.VariableExists.CODEC);
        register("variable_compare", VariableConditions.VariableCompare.CODEC);
    }

    private static void register(String path, MapCodec<? extends RpgCondition> codec) {
        RpgCondition.REGISTRY.register(
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), codec);
    }

    private CoreConditions() {}
}