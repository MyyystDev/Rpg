package com.myyyst.myrpg.core.stat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Implemented by entities that carry their own stats (see {@code RpgEntity}).
 *
 * <p>Players deliberately do <em>not</em> implement this - their stores live in the
 * world-persisted {@code PlayerStats} instead - so {@link #resolve} is the one place that
 * knows how to get a store for "any living entity".</p>
 */
public interface StatHolder {
    /** The entity's own store; must return the same instance every call. */
    StatStore rpgStats();

    /** @return the entity's stat store, or null for entities that have none (vanilla mobs). */
    @Nullable
    static StatStore resolve(LivingEntity entity) {
        if (entity instanceof StatHolder holder) return holder.rpgStats();
        if (entity instanceof ServerPlayer player) return PlayerStats.get(player);
        return null;
    }
}