package com.myyyst.myrpg.entities;

import com.myyyst.myrpg.entities.client.editor.EntityBrowserScreen;
import com.myyyst.myrpg.entities.data.EntitiesData;
import com.myyyst.myrpg.entities.editor.EntityEditorNet;
import com.myyyst.myrpg.entities.network.EntitiesPayloads;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import com.myyyst.myrpg.entities.item.EntityWandItem;
import com.myyyst.myrpg.entities.registry.RpgEntityTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge entry point for the entities mod - the counterpart of {@code MyrpgEntitiesFabric}.
 *
 * <p>NeoForge registers content through {@link DeferredRegister}s rather than directly, so
 * the entity type and wand item are declared here as deferred entries and only actually
 * created when the loader fires the registration events.</p>
 */
@Mod(MyrpgEntities.MOD_ID)
public class MyrpgEntitiesNeoForge {

    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MyrpgEntities.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MyrpgEntities.MOD_ID);

    static {
        ITEMS.register("entity_wand", () -> new EntityWandItem(
                new Item.Properties().stacksTo(1).setId(ResourceKey.create(Registries.ITEM,
                        Identifier.fromNamespaceAndPath(MyrpgEntities.MOD_ID, "entity_wand")))));
    }

    /**
     * The single entity type used by every custom entity; 0.6 x 1.95 is the player-sized
     * default, which individual definitions override through their appearance block.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<RpgEntity>> RPG_ENTITY =
            ENTITY_TYPES.register(RpgEntityTypes.RPG_ENTITY_ID,
                    () -> EntityType.Builder.of(RpgEntity::new, MobCategory.CREATURE)
                            .sized(0.6f, 1.95f)
                            .build(ResourceKey.create(Registries.ENTITY_TYPE,
                                    Identifier.fromNamespaceAndPath(
                                            MyrpgEntities.MOD_ID, RpgEntityTypes.RPG_ENTITY_ID))));

    /** Called by FML with this mod's own event bus. */
    public MyrpgEntitiesNeoForge(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(this::onAttributes);                    // mod bus: setup
        modBus.addListener(MyrpgEntitiesNeoForge::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener(this::onReload);            // game bus: runtime
        RpgEntityTypes.setRpg_entity(RPG_ENTITY::get);   // let common code reach the type

        // Client-only wiring; a dedicated server must never touch the renderer classes.
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            modBus.register(com.myyyst.myrpg.entities.client.EntitiesClientEvents.class);
        }
        MyrpgEntities.init();
    }

    /**
     * Registers every packet type together with its handler.
     * "1" is the protocol version, which clients must match to connect.
     */
    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // enqueueWork hops from the network thread onto the game thread before touching state.

        registrar.playToClient(
                EntitiesPayloads.OpenEntityBrowser.TYPE,
                EntitiesPayloads.OpenEntityBrowser.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> EntityBrowserScreen.open(payload)));

        registrar.playToClient(
                EntitiesPayloads.OpenEntityEditor.TYPE,
                EntitiesPayloads.OpenEntityEditor.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> EntityBrowserScreen.openFocused(payload)));

        registrar.playToServer(
                EntitiesPayloads.SpawnEntity.TYPE,
                EntitiesPayloads.SpawnEntity.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        EntityEditorNet.handleSpawn(sp, payload);
                    }
                }));

        registrar.playToServer(
                EntitiesPayloads.SaveEntity.TYPE,
                EntitiesPayloads.SaveEntity.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        EntityEditorNet.handleSave(sp, payload);
                    }
                }));

        registrar.playToServer(
                EntitiesPayloads.DeleteEntity.TYPE,
                EntitiesPayloads.DeleteEntity.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        EntityEditorNet.handleDelete(sp, payload);
                    }
                }));
    }

    /** Supplies the entity type's baseline attributes; NeoForge's twin of Fabric's registry call. */
    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(RPG_ENTITY.get(), RpgEntity.createAttributes().build());
    }

    /** Hooks the entity definition loader into /reload. */
    private void onReload(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(MyrpgEntities.MOD_ID, "entities"),
                EntitiesData.ENTITIES);
    }
}
