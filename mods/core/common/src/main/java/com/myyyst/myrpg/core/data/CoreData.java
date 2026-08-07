package com.myyyst.myrpg.core.data;

public final class CoreData {
    public static final RpgDataManager<StatDef> STATS =
            new RpgDataManager<>("myrpg/stats", StatDef.CODEC, "stat definition");

    private CoreData() {}
}