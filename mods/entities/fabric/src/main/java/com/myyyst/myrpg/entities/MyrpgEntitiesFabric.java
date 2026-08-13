package com.myyyst.myrpg.entities;

import com.myyyst.myrpg.entities.data.EntitiesData;
import com.myyyst.myrpg.entities.editor.EntityEditorNet;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import com.myyyst.myrpg.entities.item.EntityWandItem;
import com.myyyst.myrpg.entities.network.EntitiesPayloads;
import com.myyyst.myrpg.entities.registry.RpgEntityTypes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import net.minecraft.world.item.Item;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class MyrpgEntitiesFabric implements ModInitializer {

    public static EntityType<RpgEntity> RPG_ENTITY;

    @Override
    public void onInitialize() {
        Identifier entityId = Identifier.fromNamespaceAndPath(
                MyrpgEntities.MOD_ID, RpgEntityTypes.RPG_ENTITY_ID);
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, entityId);
        RPG_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, entityId,
                EntityType.Builder.of(RpgEntity::new, MobCategory.CREATURE)
                        .sized(0.6f, 1.95f)
                        .build(key));

        FabricDefaultAttributeRegistry.register(RPG_ENTITY, RpgEntity.createAttributes());
        RpgEntityTypes.setRpg_entity(() -> RPG_ENTITY);

        Identifier wandId = Identifier.fromNamespaceAndPath(MyrpgEntities.MOD_ID, "entity_wand");
        Registry.register(BuiltInRegistries.ITEM, wandId,
                new EntityWandItem(new Item.Properties().stacksTo(1)
                        .setId(ResourceKey.create(Registries.ITEM, wandId))));

        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new ReloadAdapter("entities", EntitiesData.ENTITIES));

        PayloadTypeRegistry.clientboundPlay().register(
                EntitiesPayloads.OpenEntityBrowser.TYPE,
                EntitiesPayloads.OpenEntityBrowser.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                EntitiesPayloads.OpenEntityEditor.TYPE,
                EntitiesPayloads.OpenEntityEditor.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                EntitiesPayloads.SpawnEntity.TYPE,
                EntitiesPayloads.SpawnEntity.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                EntitiesPayloads.SaveEntity.TYPE,
                EntitiesPayloads.SaveEntity.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                EntitiesPayloads.DeleteEntity.TYPE,
                EntitiesPayloads.DeleteEntity.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                EntitiesPayloads.SpawnEntity.TYPE,
                (payload, context) -> context.server().execute(
                        () -> EntityEditorNet.handleSpawn(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(
                EntitiesPayloads.SaveEntity.TYPE,
                (payload, context) -> context.server().execute(
                        () -> EntityEditorNet.handleSave(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(
                EntitiesPayloads.DeleteEntity.TYPE,
                (payload, context) -> context.server().execute(
                        () -> EntityEditorNet.handleDelete(context.player(), payload)));

        MyrpgEntities.init();
    }

    private static class ReloadAdapter implements IdentifiableResourceReloadListener {

        private final Identifier id;
        private final PreparableReloadListener listener;

        ReloadAdapter(String path, PreparableReloadListener listener) {
            this.id = Identifier.fromNamespaceAndPath(Constants.MOD_ID, path);
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
