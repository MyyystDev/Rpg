package com.myyyst.myrpg.core.data;

import com.myyyst.myrpg.core.condition.RpgCondition;


/**
 * The datapack-backed content of the core mod.
 *
 * <p>Each field is a reload listener that scans every loaded datapack for JSON files under
 * the given folder and parses them with a codec. A file at
 * {@code data/&lt;namespace&gt;/myrpg/stats/rage.json} therefore becomes the entry
 * {@code &lt;namespace&gt;:rage} in {@link #STATS}.</p>
 *
 * <p>The managers must be registered with the server's resource-reload pipeline by each
 * loader module (see {@code MyRpgFabric} / {@code MyRpgNeoForge}), otherwise they stay empty.</p>
 */
public final class CoreData {
    /** Player/entity stats: value range, stages, rules, HUD. Folder: {@code myrpg/stats}. */
    public static final RpgDataManager<StatDef> STATS =
            new RpgDataManager<>("myrpg/stats", StatDef.CODEC, "stat definition");

    /** Reusable conditions that other files can point at by id, so logic is not duplicated. */
    public static final RpgDataManager<RpgCondition> NAMED_CONDITIONS =
            new RpgDataManager<>("myrpg/conditions", RpgCondition.CODEC, "named condition");

    /** Custom status effects (bleeding, stunned, ...). Folder: {@code myrpg/effects}. */
    public static final RpgDataManager<EffectDefinition> EFFECTS =
            new RpgDataManager<>("myrpg/effects", EffectDefinition.CODEC, "effect definition");

    /** Static holder: never instantiated. */
    private CoreData() {}
}