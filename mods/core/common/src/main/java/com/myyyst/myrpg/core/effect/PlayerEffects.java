package com.myyyst.myrpg.core.effect;

import com.mojang.serialization.Codec;
import com.myyyst.myrpg.core.Constants;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-player effect stores, world-persisted — mirror of PlayerStats. */
public class PlayerEffects extends SavedData {

    /** Keyed by player UUID, like {@code PlayerStats}. */
    private final Map<UUID, EffectStore> stores = new HashMap<>();

    /** @return the player's effect store, creating an empty one on first access. */
    public static EffectStore get(ServerPlayer player) {
        PlayerEffects data = getData(player.level());
        return data.stores.computeIfAbsent(player.getUUID(), u -> new EffectStore());
    }

    /** Flags the saved data for writing; call after any change. */
    public static void markDirty(ServerPlayer player) {
        getData(player.level()).setDirty();
    }

    /** All players share one instance, anchored to the overworld's data storage. */
    private static PlayerEffects getData(net.minecraft.world.level.Level level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    // ------------------------------------------------------------ persistence

    private static final Codec<Map<UUID, List<EffectInstance>>> STORES_CODEC =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, EffectStore.LIST_CODEC);

    public static final Codec<PlayerEffects> CODEC = STORES_CODEC.xmap(
            PlayerEffects::fromMap,
            PlayerEffects::toMap);

    /** Registration handle: file name "myrpg_core/player_effects.dat". */
    public static final SavedDataType<PlayerEffects> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "player_effects"),
            PlayerEffects::new,
            CODEC,
            null);

    /**
     * Load path. Instances are restored as-is; their attribute modifiers are re-applied by
     * {@code EffectManager.reapplyAll} when the player joins, without firing on_applied.
     */
    private static PlayerEffects fromMap(Map<UUID, List<EffectInstance>> raw) {
        PlayerEffects data = new PlayerEffects();
        raw.forEach((uuid, list) -> {
            EffectStore store = new EffectStore();
            store.all().addAll(list);
            data.stores.put(uuid, store);
        });
        return data;
    }

    /** Save path: snapshot every store so the save thread never sees a live list. */
    private Map<UUID, List<EffectInstance>> toMap() {
        Map<UUID, List<EffectInstance>> out = new HashMap<>();
        stores.forEach((uuid, store) -> out.put(uuid, List.copyOf(store.all())));
        return out;
    }
}
