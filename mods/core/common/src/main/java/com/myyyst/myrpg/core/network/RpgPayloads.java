package com.myyyst.myrpg.core.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.myyyst.myrpg.core.Constants;

import java.util.List;

public final class RpgPayloads {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, path);
    }

    /** One HUD-visible stat's current state, with its display config inline. */
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
        // NOTE drift: Identifier.STREAM_CODEC spelling; composite arity caps
        // at some count — if 9 fields exceed it, split into nested composites
        // or encode via StreamCodec of a smaller tuple + map.
    }

    /** S2C: your HUD stats (changed entries; full set on join). */
    public record SyncStats(List<StatEntry> entries) implements CustomPacketPayload {
        public static final Type<SyncStats> TYPE = new Type<>(id("sync_stats"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncStats> STREAM_CODEC =
                StreamCodec.composite(
                        StatEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncStats::entries,
                        SyncStats::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private RpgPayloads() {}
}