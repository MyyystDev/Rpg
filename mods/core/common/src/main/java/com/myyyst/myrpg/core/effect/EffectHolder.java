package com.myyyst.myrpg.core.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** Anything that can carry custom effects (players + custom entities). */
public interface EffectHolder {

    /** The entity's own store; must return the same instance every call. */
    EffectStore rpgEffects();

    /**
     * @return the entity's effect store, or null for entities that cannot carry effects.
     *         Players are handled through the world-saved {@code PlayerEffects} rather
     *         than by implementing this interface.
     */
    @Nullable
    static EffectStore resolve(LivingEntity entity) {
        if (entity instanceof EffectHolder holder) return holder.rpgEffects();
        if (entity instanceof ServerPlayer player) return PlayerEffects.get(player);
        return null;
    }
}
