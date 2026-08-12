package com.myyyst.myrpg.entities;

import com.myyyst.myrpg.entities.data.EntitiesData;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import com.myyyst.myrpg.entities.registry.RpgEntityTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(MyrpgEntities.MOD_ID)
public class MyrpgEntitiesNeoForge {

    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MyrpgEntities.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<RpgEntity>> RPG_ENTITY =
            ENTITY_TYPES.register(RpgEntityTypes.RPG_ENTITY_ID,
                    () -> EntityType.Builder.of(RpgEntity::new, MobCategory.CREATURE)
                            .sized(0.6f, 1.95f)
                            .build(ResourceKey.create(Registries.ENTITY_TYPE,
                                    Identifier.fromNamespaceAndPath(
                                            MyrpgEntities.MOD_ID, RpgEntityTypes.RPG_ENTITY_ID))));

    public MyrpgEntitiesNeoForge(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(this::onAttributes);
        NeoForge.EVENT_BUS.addListener(this::onReload);
        RpgEntityTypes.setRpg_entity(RPG_ENTITY::get);

        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            modBus.register(com.myyyst.myrpg.entities.client.EntitiesClientEvents.class);
        }
        MyrpgEntities.init();
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(RPG_ENTITY.get(), RpgEntity.createAttributes().build());
    }

    private void onReload(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(MyrpgEntities.MOD_ID, "entities"),
                EntitiesData.ENTITIES);
    }
}
