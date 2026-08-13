package com.myyyst.myrpg.core.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** Anything that can carry custom effects (players + custom entities). */
public interface EffectHolder {

    EffectStore rpgEffects();

    @Nullable
    static EffectStore resolve(LivingEntity entity) {
        if (entity instanceof EffectHolder holder) return holder.rpgEffects();
        if (entity instanceof ServerPlayer player) return PlayerEffects.get(player);
        return null;
    }
}
