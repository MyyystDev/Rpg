package com.myyyst.myrpg.entities.network;

import com.myyyst.myrpg.entities.MyrpgEntities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Every custom packet the entities mod sends - the counterpart of core's {@code RpgPayloads}.
 *
 * <p>Direction is noted per payload: S2C = server to client, C2S = client to server.
 * As in core, the editor payloads carry raw JSON text so the client can edit definitions
 * without any datapack access.</p>
 */
public final class EntitiesPayloads {

    /** Shorthand for a packet id in this mod's namespace. */
    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MyrpgEntities.MOD_ID, path);
    }

    /** One entity definition as (id string, JSON string). */
    public record EntityFile(String entityId, String json) {
        public static final StreamCodec<RegistryFriendlyByteBuf, EntityFile> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, EntityFile::entityId,
                        ByteBufCodecs.STRING_UTF8, EntityFile::json,
                        EntityFile::new);
    }

    /** S2C: opens the entity browser with every loaded definition. */
    public record OpenEntityBrowser(List<EntityFile> entities) implements CustomPacketPayload {
        public static final Type<OpenEntityBrowser> TYPE = new Type<>(id("open_entity_browser"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenEntityBrowser> STREAM_CODEC =
                StreamCodec.composite(
                        EntityFile.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenEntityBrowser::entities,
                        OpenEntityBrowser::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: spawn one definition at the sender's position (permission-checked). */
    public record SpawnEntity(String entityId) implements CustomPacketPayload {
        public static final Type<SpawnEntity> TYPE = new Type<>(id("spawn_entity"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SpawnEntity> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, SpawnEntity::entityId,
                        SpawnEntity::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S2C: open the editor directly on one definition (wand right-click). */
    public record OpenEntityEditor(List<EntityFile> entities, String focus)
            implements CustomPacketPayload {
        public static final Type<OpenEntityEditor> TYPE = new Type<>(id("open_entity_editor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenEntityEditor> STREAM_CODEC =
                StreamCodec.composite(
                        EntityFile.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenEntityEditor::entities,
                        ByteBufCodecs.STRING_UTF8, OpenEntityEditor::focus,
                        OpenEntityEditor::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: save one entity definition into the overlay datapack. */
    public record SaveEntity(String entityId, String json) implements CustomPacketPayload {
        public static final Type<SaveEntity> TYPE = new Type<>(id("save_entity"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveEntity> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, SaveEntity::entityId,
                        ByteBufCodecs.STRING_UTF8, SaveEntity::json,
                        SaveEntity::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: delete one overlay entity file. */
    public record DeleteEntity(String entityId) implements CustomPacketPayload {
        public static final Type<DeleteEntity> TYPE = new Type<>(id("delete_entity"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DeleteEntity> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, DeleteEntity::entityId,
                        DeleteEntity::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Static holder for the payload records: never instantiated. */
    private EntitiesPayloads() {}
}
