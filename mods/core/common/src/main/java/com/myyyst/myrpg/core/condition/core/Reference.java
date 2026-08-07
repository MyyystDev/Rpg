package com.myyyst.myrpg.core.condition.core;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.condition.RpgCondition;
import com.myyyst.myrpg.core.data.CoreData;
import net.minecraft.resources.Identifier;

/**
 * Points at a named condition file (data/<ns>/myrpg/conditions/<path>.json).
 * Creators define "is_worthy" once, reference it everywhere.
 * Recursion-guarded: a reference chain deeper than MAX_DEPTH fails false
 * with a log error instead of overflowing.
 */
public record Reference(Identifier id) implements RpgCondition {

    public static final MapCodec<Reference> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(Reference::id)
    ).apply(i, Reference::new));

    private static final int MAX_DEPTH = 16;
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    @Override
    public boolean test(ConditionContext ctx) {
        RpgCondition resolved = CoreData.NAMED_CONDITIONS.get(id).orElse(null);
        if (resolved == null) {
            Constants.LOG.warn("[myrpg] reference to unknown condition {}", id);
            return false;
        }
        int depth = DEPTH.get();
        if (depth >= MAX_DEPTH) {
            Constants.LOG.error("[myrpg] condition reference chain too deep (loop?) at {}", id);
            return false;
        }
        DEPTH.set(depth + 1);
        try {
            return resolved.test(ctx);
        } finally {
            DEPTH.set(depth);
        }
    }

    @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
}