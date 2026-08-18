package com.myyyst.myrpg.entities.registry;

import com.myyyst.myrpg.entities.entity.RpgEntity;
import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

/**
 * Holder for the single entity type this mod registers.
 *
 * <p>Entity registration differs between loaders (Fabric registers directly, NeoForge uses
 * a deferred register), and the common module cannot do either. So each loader creates the
 * type and hands it back here as a {@link Supplier}, which common code can then resolve.</p>
 */
public final class RpgEntityTypes {

    /** Registry path of the type: {@code myrpg_entities:rpg_entity}. */
    public static final String RPG_ENTITY_ID = "rpg_entity";

    /** Set once by the loader module during registration. */
    private static Supplier<EntityType<RpgEntity>> rpg_entity;

    /** Called by the loader module as soon as the type exists. */
    public static void setRpg_entity(Supplier<EntityType<RpgEntity>> supplier) {
        rpg_entity = supplier;
    }

    /**
     * @return the registered entity type
     * @throws NullPointerException if called before the loader registered it
     */
    public static EntityType<RpgEntity> rpg_entity() {
        return rpg_entity.get();
    }

    /** Static-only holder: never instantiated. */
    private RpgEntityTypes() {}
}