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

    private final Map<UUID, EffectStore> stores = new HashMap<>();

    public static EffectStore get(ServerPlayer player) {
        PlayerEffects data = getData(player.level());
        return data.stores.computeIfAbsent(player.getUUID(), u -> new EffectStore());
    }

    public static void markDirty(ServerPlayer player) {
        getData(player.level()).setDirty();
    }

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

    public static final SavedDataType<PlayerEffects> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "player_effects"),
            PlayerEffects::new,
            CODEC,
            null);

    private static PlayerEffects fromMap(Map<UUID, List<EffectInstance>> raw) {
        PlayerEffects data = new PlayerEffects();
        raw.forEach((uuid, list) -> {
            EffectStore store = new EffectStore();
            store.all().addAll(list);
            data.stores.put(uuid, store);
        });
        return data;
    }

    private Map<UUID, List<EffectInstance>> toMap() {
        Map<UUID, List<EffectInstance>> out = new HashMap<>();
        stores.forEach((uuid, store) -> out.put(uuid, List.copyOf(store.all())));
        return out;
    }
}
