package com.myyyst.myrpg.entities;

import com.myyyst.myrpg.entities.entity.RpgEntity;
import com.myyyst.myrpg.entities.registry.RpgEntityTypes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class MyrpgEntitiesFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        registerEntityTypes();
        MyrpgEntities.init();
    }

    private static void registerEntityTypes() {
        Identifier id = Identifier.fromNamespaceAndPath(Constants.MOD_ID, RpgEntityTypes.RPG_ENTITY_ID);

        EntityType<RpgEntity> rpg_entity = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                id,
                EntityType.Builder.of(RpgEntity::new, MobCategory.MISC)
                        .sized(0.6f, 1.8f)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

        RpgEntityTypes.setRpg_entity(() -> rpg_entity);
        FabricDefaultAttributeRegistry.register(rpg_entity, RpgEntity.createAttributes());
    }
}