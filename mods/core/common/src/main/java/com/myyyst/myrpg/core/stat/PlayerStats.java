package com.myyyst.myrpg.core.stat;

import com.mojang.serialization.Codec;
import com.myyyst.myrpg.core.Constants;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player stat stores, world-persisted. Anchored to the overworld's
 * data storage (one file per save, like BankData was).
 *
 * NOTE drift: the SavedData registration/factory idiom is the old
 * project's BankData pattern — transcribe the compiled spelling from
 * there (SavedDataType shape, computeIfAbsent call, codec-vs-nbt
 * serialization). The structure below assumes the codec-based variant.
 */
public class PlayerStats extends SavedData {

    /** Keyed by player UUID so stores survive name changes and offline players stay loaded. */
    private final Map<UUID, StatStore> stores = new HashMap<>();

    /** @return the player's store, creating an empty one on first access. */
    public static StatStore get(ServerPlayer player) {
        PlayerStats data = getData(player.level());
        StatStore store = data.stores.computeIfAbsent(player.getUUID(), u -> new StatStore());
        return store;
    }

    /** Mark dirty after modifications so the world save writes it. */
    public static void markDirty(ServerPlayer player) {
        getData(player.level()).setDirty();
    }

    /** All players share one instance, anchored to the overworld's data storage. */
    private static PlayerStats getData(net.minecraft.world.level.Level level) {
        ServerLevel overworld = level.getServer().overworld();
        // NOTE drift: dataStorage().computeIfAbsent(...) — BankData's exact
        // spelling, with this class's type/factory.
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    // TYPE definition + serialization: mirror BankData, storing
    // Map<UUID, Map<Identifier, Double>> — each player's StatStore.all()
    // via the VALUES_CODEC already in StatStore (expose it or re-declare).

    // ------------------------------------------------------------ persistence

    /** On-disk shape: {@code {"<uuid>": {"<ns>:<stat>": value}}}. Stage state is not saved. */
    private static final Codec<Map<UUID, Map<Identifier, Double>>> STORES_CODEC =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC,
                    Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE));

    /** Bridges the raw map above to/from live StatStore objects. */
    public static final Codec<PlayerStats> CODEC = STORES_CODEC.xmap(
            PlayerStats::fromMap,
            PlayerStats::toMap);

    /** Registration handle: file name "myrpg_core/player_stats.dat", factory, codec, no data-fixer. */
    public static final SavedDataType<PlayerStats> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "player_stats"),
            PlayerStats::new,
            CODEC,
            null);
    // NOTE drift: SavedDataType's exact parameter list (id shape, whether the
    // last arg is DataFixTypes or absent) — transcribe from the old BankData.

    /**
     * Load path. Values are pushed straight into each store's map, bypassing
     * {@code StatStore.set} on purpose: no clamping, no stage events, no owner needed.
     * Stages are recomputed by {@code PlayerStatTicker.onJoin}.
     */
    private static PlayerStats fromMap(Map<UUID, Map<Identifier, Double>> raw) {
        PlayerStats data = new PlayerStats();
        raw.forEach((uuid, values) -> {
            StatStore store = new StatStore();
            values.forEach(store.all()::put);   // raw load — stages recomputed on join
            data.stores.put(uuid, store);
        });
        return data;
    }

    /** Save path: snapshot every store so the save thread never sees a live map. */
    private Map<UUID, Map<Identifier, Double>> toMap() {
        Map<UUID, Map<Identifier, Double>> out = new HashMap<>();
        stores.forEach((uuid, store) -> out.put(uuid, Map.copyOf(store.all())));
        return out;
    }
}