package com.myyyst.myrpg.core.condition.core;

import com.mojang.serialization.MapCodec;
import com.myyyst.myrpg.core.RpgDebug;
import com.myyyst.myrpg.core.condition.RpgCondition;

/** True only while debug mode is on (/myrpg debug on). */
public record DebugFlag() implements RpgCondition {
    public static final MapCodec<DebugFlag> CODEC = MapCodec.unit(new DebugFlag());
    @Override public boolean test(ConditionContext ctx) { return RpgDebug.enabled(); }
    @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
}