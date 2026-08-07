package com.myyyst.myrpg.core.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.myyyst.myrpg.core.registry.DispatchRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The universal condition vocabulary. Any mod registers types into REGISTRY
 * from its init. JSON: { "type": "myrpg_core:and", "conditions": [...] }
 *
 * Context semantics: conditions that need an absent context field return
 * false ("no player present" is not "player matches"). Document per
 * condition which fields it reads.
 */
public interface RpgCondition {

    boolean test(ConditionContext ctx);

    MapCodec<? extends RpgCondition> codec();

    record ConditionContext(
            LivingEntity self,
            @Nullable ServerPlayer player,
            @Nullable LivingEntity target
    ) {
        public static ConditionContext of(LivingEntity self) {
            return new ConditionContext(self, null, null);
        }
        public static ConditionContext of(LivingEntity self, ServerPlayer player) {
            return new ConditionContext(self, player, null);
        }
        public ConditionContext withTarget(LivingEntity newTarget) {
            return new ConditionContext(self, player, newTarget);
        }
    }

    DispatchRegistry<RpgCondition> REGISTRY = new DispatchRegistry<>(RpgCondition::codec);
    Codec<RpgCondition> CODEC = REGISTRY.codec();
}