package com.myyyst.myrpg.core.data;

import com.myyyst.myrpg.core.condition.RpgCondition;


public final class CoreData {
    public static final RpgDataManager<StatDef> STATS =
            new RpgDataManager<>("myrpg/stats", StatDef.CODEC, "stat definition");

    public static final RpgDataManager<RpgCondition> NAMED_CONDITIONS =
            new RpgDataManager<>("myrpg/conditions", RpgCondition.CODEC, "named condition");

    public static final RpgDataManager<EffectDefinition> EFFECTS =
            new RpgDataManager<>("myrpg/effects", EffectDefinition.CODEC, "effect definition");

    private CoreData() {}
}