package com.myyyst.myrpg.entities.registry;

import com.myyyst.myrpg.entities.entity.RpgEntity;
import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

public final class RpgEntityTypes {

    public static final String RPG_ENTITY_ID = "rpg_entity";

    private static Supplier<EntityType<RpgEntity>> rpg_entity;

    public static void setRpg_entity(Supplier<EntityType<RpgEntity>> supplier) {
        rpg_entity = supplier;
    }

    public static EntityType<RpgEntity> rpg_entity() {
        return rpg_entity.get();
    }

    private RpgEntityTypes() {}
}