package com.myyyst.myrpg.entities;

import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.entities.data.EntitiesData;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class MyrpgEntitiesFabric implements ModInitializer {

    public static EntityType<RpgEntity> RPG_ENTITY;

    @Override
    public void onInitialize() {
        // NOTE drift: EntityType.Builder.build argument + registration shape —
        // keep the archetype-era lines that compiled.
        // Fabric — build the key once, use for both register + build:
        Identifier entityId = Identifier.fromNamespaceAndPath(MyrpgEntities.MOD_ID, "rpg_entity");
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, entityId);
        RPG_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, entityId,
                EntityType.Builder.of(RpgEntity::new, MobCategory.CREATURE)
                        .sized(0.6f, 1.95f)
                        .build(key));

        FabricDefaultAttributeRegistry.register(RPG_ENTITY, RpgEntity.createAttributes());

        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new ReloadAdapter("entities", EntitiesData.ENTITIES));
        // NOTE drift: if RpgDataManager needs an id for Fabric's listener,
        // keep the archetype-era registration line.

        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new ReloadAdapter("conditions", CoreData.NAMED_CONDITIONS));

        MyrpgEntities.init();
    }

    private static class ReloadAdapter implements IdentifiableResourceReloadListener {

        private final Identifier id;
        private final PreparableReloadListener listener;

        ReloadAdapter(String path, PreparableReloadListener listener) {
            this.id = Identifier.fromNamespaceAndPath(Constants.MOD_ID, path);   // entities' Constants
            this.listener = listener;
        }

        @Override
        public Identifier getFabricId() { return id; }

        @Override
        public CompletableFuture<Void> reload(PreparableReloadListener.SharedState sharedState,
                                              Executor backgroundExecutor,
                                              PreparableReloadListener.PreparationBarrier barrier,
                                              Executor gameExecutor) {
            return listener.reload(sharedState, backgroundExecutor, barrier, gameExecutor);
        }
    }
}