package com.myyyst.myrpg.core.network;

import com.myyyst.myrpg.core.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Every custom packet the core mod sends, in one place.
 *
 * <p>A payload is a record plus two statics: a {@code TYPE} (its network id) and a
 * {@code STREAM_CODEC} (how it is written to and read from the buffer). The loader modules
 * register these types and attach handlers; {@code Services.NETWORK} sends them.</p>
 *
 * <p>Direction is noted per payload: S2C = server to client, C2S = client to server.
 * The HUD payloads carry pre-resolved display data so the client never needs datapack access;
 * the editor payloads carry raw JSON text so the client can edit definitions directly.</p>
 */
public final class RpgPayloads {

    /** Shorthand for a packet id in this mod's namespace. */
    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, path);
    }

    // ================================================== HUD sync (existing)

    /**
     * One stat, flattened for the HUD: current value plus everything needed to draw it.
     * Written field by field because the record is wider than the composite helper supports.
     */
    public record StatEntry(
            Identifier statId, double value, double min, double max, double defaultValue,
            String name, String color, String hudType, String visibility,
            double visibilityValue, boolean showValue, String icon
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, StatEntry> STREAM_CODEC =
                StreamCodec.of(
                        (buf, e) -> {
                            Identifier.STREAM_CODEC.encode(buf, e.statId());
                            buf.writeDouble(e.value());
                            buf.writeDouble(e.min());
                            buf.writeDouble(e.max());
                            buf.writeDouble(e.defaultValue());
                            buf.writeUtf(e.name());
                            buf.writeUtf(e.color());
                            buf.writeUtf(e.hudType());
                            buf.writeUtf(e.visibility());
                            buf.writeDouble(e.visibilityValue());
                            buf.writeBoolean(e.showValue());
                            buf.writeUtf(e.icon());
                        },
                        buf -> new StatEntry(
                                Identifier.STREAM_CODEC.decode(buf),
                                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                                buf.readDouble(), buf.readBoolean(), buf.readUtf()));
    }

    /**
     * S2C: stat values for the HUD. Sent in full on join/respawn and as a partial
     * "only what changed" list every tick, so the client merges rather than replaces.
     */
    public record SyncStats(List<StatEntry> entries) implements CustomPacketPayload {
        public static final Type<SyncStats> TYPE = new Type<>(id("sync_stats"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncStats> STREAM_CODEC =
                StreamCodec.composite(
                        StatEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncStats::entries,
                        SyncStats::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ================================================== effect HUD sync

    /** One active custom effect, pre-resolved server-side for display. */
    public record EffectEntry(
            Identifier effectId, String name, String color, String icon,
            String category, int remaining, int level, int stacks,
            boolean showIcon, boolean showDuration, boolean showStacks, boolean showLevel
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, EffectEntry> STREAM_CODEC =
                StreamCodec.of(
                        (buf, e) -> {
                            Identifier.STREAM_CODEC.encode(buf, e.effectId());
                            buf.writeUtf(e.name());
                            buf.writeUtf(e.color());
                            buf.writeUtf(e.icon());
                            buf.writeUtf(e.category());
                            buf.writeVarInt(e.remaining());
                            buf.writeVarInt(e.level());
                            buf.writeVarInt(e.stacks());
                            buf.writeBoolean(e.showIcon());
                            buf.writeBoolean(e.showDuration());
                            buf.writeBoolean(e.showStacks());
                            buf.writeBoolean(e.showLevel());
                        },
                        buf -> new EffectEntry(
                                Identifier.STREAM_CODEC.decode(buf),
                                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                                buf.readBoolean()));
    }

    /** S2C: full replace of the local player's active custom effects. */
    public record SyncEffects(List<EffectEntry> entries) implements CustomPacketPayload {
        public static final Type<SyncEffects> TYPE = new Type<>(id("sync_effects"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncEffects> STREAM_CODEC =
                StreamCodec.composite(
                        EffectEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncEffects::entries,
                        SyncEffects::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ================================================== stat editor

    /**
     * One definition as (id string, JSON string). Reused for both stat and effect editors -
     * the editors work on raw JSON text, so no schema-specific payload is needed.
     */
    public record StatFile(String statId, String json) {
        public static final StreamCodec<RegistryFriendlyByteBuf, StatFile> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, StatFile::statId,
                        ByteBufCodecs.STRING_UTF8, StatFile::json,
                        StatFile::new);
    }

    /** S2C: opens the stat editor with every loaded stat definition. */
    public record OpenStatEditor(List<StatFile> stats) implements CustomPacketPayload {
        public static final Type<OpenStatEditor> TYPE = new Type<>(id("open_stat_editor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenStatEditor> STREAM_CODEC =
                StreamCodec.composite(
                        StatFile.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenStatEditor::stats,
                        OpenStatEditor::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: save one stat definition into the overlay datapack. */
    public record SaveStat(String statId, String json) implements CustomPacketPayload {
        public static final Type<SaveStat> TYPE = new Type<>(id("save_stat"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveStat> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, SaveStat::statId,
                        ByteBufCodecs.STRING_UTF8, SaveStat::json,
                        SaveStat::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: delete one overlay stat file. */
    public record DeleteStat(String statId) implements CustomPacketPayload {
        public static final Type<DeleteStat> TYPE = new Type<>(id("delete_stat"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DeleteStat> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, DeleteStat::statId,
                        DeleteStat::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ================================================== effect editor

    /** S2C: opens the effect editor with every loaded effect definition. */
    public record OpenEffectEditor(List<StatFile> effects) implements CustomPacketPayload {
        public static final Type<OpenEffectEditor> TYPE = new Type<>(id("open_effect_editor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenEffectEditor> STREAM_CODEC =
                StreamCodec.composite(
                        StatFile.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenEffectEditor::effects,
                        OpenEffectEditor::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: save one effect definition into the overlay datapack. */
    public record SaveEffect(String effectId, String json) implements CustomPacketPayload {
        public static final Type<SaveEffect> TYPE = new Type<>(id("save_effect"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveEffect> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, SaveEffect::effectId,
                        ByteBufCodecs.STRING_UTF8, SaveEffect::json,
                        SaveEffect::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: delete one overlay effect file. */
    public record DeleteEffect(String effectId) implements CustomPacketPayload {
        public static final Type<DeleteEffect> TYPE = new Type<>(id("delete_effect"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DeleteEffect> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, DeleteEffect::effectId,
                        DeleteEffect::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Static holder for the payload records: never instantiated. */
    private RpgPayloads() {}
}