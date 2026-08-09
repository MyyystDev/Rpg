package com.myyyst.myrpg.core.stat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

public interface StatHolder {
    StatStore rpgStats();

    @Nullable
    static StatStore resolve(LivingEntity entity) {
        if (entity instanceof StatHolder holder) return holder.rpgStats();
        if (entity instanceof ServerPlayer player) return PlayerStats.get(player);
        return null;
    }
}