package com.myyyst.myrpg.core.network;

import com.myyyst.myrpg.core.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class RpgPayloads {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, path);
    }

    // ================================================== HUD sync (existing)

    public record StatEntry(
            Identifier statId, double value, double min, double max,
            String name, String color, String hudType, String visibility, boolean showValue
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, StatEntry> STREAM_CODEC =
                StreamCodec.composite(
                        Identifier.STREAM_CODEC, StatEntry::statId,
                        ByteBufCodecs.DOUBLE, StatEntry::value,
                        ByteBufCodecs.DOUBLE, StatEntry::min,
                        ByteBufCodecs.DOUBLE, StatEntry::max,
                        ByteBufCodecs.STRING_UTF8, StatEntry::name,
                        ByteBufCodecs.STRING_UTF8, StatEntry::color,
                        ByteBufCodecs.STRING_UTF8, StatEntry::hudType,
                        ByteBufCodecs.STRING_UTF8, StatEntry::visibility,
                        ByteBufCodecs.BOOL, StatEntry::showValue,
                        StatEntry::new);
    }

    public record SyncStats(List<StatEntry> entries) implements CustomPacketPayload {
        public static final Type<SyncStats> TYPE = new Type<>(id("sync_stats"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncStats> STREAM_CODEC =
                StreamCodec.composite(
                        StatEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncStats::entries,
                        SyncStats::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ================================================== stat editor

    /** One stat definition as (id string, pretty/plain JSON string). */
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

    private RpgPayloads() {}
}