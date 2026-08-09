package com.myyyst.myrpg.core.variable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.core.Constants;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Variable storage: world-scoped (shared) and player-scoped (per UUID).
 * Party scope arrives with the party system; the scope strings in
 * conditions/actions already reserve it.
 */
public class Variables extends SavedData {

    private final Map<String, VarValue> world = new HashMap<>();
    private final Map<UUID, Map<String, VarValue>> players = new HashMap<>();

    // ------------------------------------------------------------ access

    public static Optional<VarValue> get(Level level, String scope, String name,
                                         @Nullable ServerPlayer player) {
        Variables data = getData(level);
        return switch (scope) {
            case "world" -> Optional.ofNullable(data.world.get(name));
            case "player" -> player == null ? Optional.empty()
                    : Optional.ofNullable(data.players.getOrDefault(player.getUUID(), Map.of()).get(name));
            default -> {
                Constants.LOG.warn("[myrpg] Unknown variable scope '{}'", scope);
                yield Optional.empty();
            }
        };
    }

    public static void set(Level level, String scope, String name,
                           @Nullable ServerPlayer player, VarValue value) {
        Variables data = getData(level);
        switch (scope) {
            case "world" -> data.world.put(name, value);
            case "player" -> {
                if (player == null) {
                    Constants.LOG.warn("[myrpg] player-scope variable '{}' set with no player in context", name);
                    return;
                }
                data.players.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).put(name, value);
            }
            default -> Constants.LOG.warn("[myrpg] Unknown variable scope '{}'", scope);
        }
        data.setDirty();
    }

    public static void remove(Level level, String scope, String name, @Nullable ServerPlayer player) {
        Variables data = getData(level);
        switch (scope) {
            case "world" -> data.world.remove(name);
            case "player" -> {
                if (player != null) {
                    Map<String, VarValue> map = data.players.get(player.getUUID());
                    if (map != null) map.remove(name);
                }
            }
        }
        data.setDirty();
    }

    private static Variables getData(Level level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
        // NOTE drift: same computeIfAbsent spelling as PlayerStats/BankData.
    }

    // ------------------------------------------------------------ persistence

    private static final Codec<Map<String, VarValue>> SCOPE_CODEC =
            Codec.unboundedMap(Codec.STRING, VarValue.CODEC);

    private static final Codec<Variables> CODEC = RecordCodecBuilder.create(i -> i.group(
            SCOPE_CODEC.optionalFieldOf("world", Map.of()).forGetter(d -> Map.copyOf(d.world)),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, SCOPE_CODEC)   // or UUID_STRING
                    .optionalFieldOf("players", Map.of()).forGetter(d -> {
                        Map<UUID, Map<String, VarValue>> out = new HashMap<>();
                        d.players.forEach((u, m) -> out.put(u, Map.copyOf(m)));
                        return out;
                    })
    ).apply(i, Variables::fromMaps));

    private static Variables fromMaps(Map<String, VarValue> world,
                                      Map<UUID, Map<String, VarValue>> players) {
        Variables data = new Variables();
        data.world.putAll(world);
        players.forEach((u, m) -> data.players.put(u, new HashMap<>(m)));
        return data;
    }

    public static final SavedDataType<Variables> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "variables"),
            Variables::new,
            CODEC,
            null);
    // NOTE drift: parameter list per PlayerStats/BankData.
}