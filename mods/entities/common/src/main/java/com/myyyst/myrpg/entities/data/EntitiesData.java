package com.myyyst.myrpg.entities.data;

import com.myyyst.myrpg.core.data.RpgDataManager;

/**
 * The datapack-backed content of the entities mod - the counterpart of core's
 * {@code CoreData}, reusing its {@link RpgDataManager}.
 *
 * <p>A file at {@code data/&lt;ns&gt;/myrpg/entities/bandit.json} becomes the entry
 * {@code &lt;ns&gt;:bandit}. The manager must be registered with the server's reload
 * pipeline by each loader module, or it stays empty.</p>
 */
public final class EntitiesData {

    /** Custom entity definitions. Folder: {@code myrpg/entities}. */
    public static final RpgDataManager<EntityDefinition> ENTITIES =
            new RpgDataManager<>("myrpg/entities", EntityDefinition.CODEC, "entity definition");

    /** Static holder: never instantiated. */
    private EntitiesData() {}
}