package com.myyyst.myrpg.core.condition;

import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.condition.core.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

/**
 * Registers core's built-in condition types into {@code RpgCondition.REGISTRY}.
 *
 * <p>Called once from {@code MyRpgCommon.init}, before any datapack is parsed - a type that
 * is not registered by then cannot be read from JSON. The short paths below become ids such
 * as {@code myrpg_core:and}.</p>
 */
public final class CoreConditions {

    /** Registers every built-in condition type. Must run before datapack loading. */
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

    /** Registers one type under {@code myrpg_core:<path>}. */
    private static void register(String path, MapCodec<? extends RpgCondition> codec) {
        RpgCondition.REGISTRY.register(
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), codec);
    }

    /** Static-only helper: never instantiated. */
    private CoreConditions() {}
}