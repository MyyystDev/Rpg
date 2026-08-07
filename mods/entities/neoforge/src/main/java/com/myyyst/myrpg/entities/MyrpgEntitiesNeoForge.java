package com.myyyst.myrpg.entities;

import com.myyyst.myrpg.entities.entity.RpgEntity;
import com.myyyst.myrpg.entities.registry.RpgEntityTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(Constants.MOD_ID)
public class MyrpgEntitiesNeoForge {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Constants.MOD_ID);

    private static final Supplier<EntityType<RpgEntity>> RPG_ENTITY = ENTITY_TYPES.register(
            RpgEntityTypes.RPG_ENTITY_ID,
            () -> EntityType.Builder.of(RpgEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(Constants.MOD_ID, RpgEntityTypes.RPG_ENTITY_ID))));

    public MyrpgEntitiesNeoForge(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        RpgEntityTypes.setRpg_entity(RPG_ENTITY);
        modBus.addListener(this::onAttributes);
        MyrpgEntities.init();
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(RPG_ENTITY.get(), RpgEntity.createAttributes().build());
    }
}