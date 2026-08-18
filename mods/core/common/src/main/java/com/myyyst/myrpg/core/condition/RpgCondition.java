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

    /** @return true if the condition holds. Must be side-effect free - it may be called often. */
    boolean test(ConditionContext ctx);

    /** The codec this instance was registered with; used when writing back to JSON. */
    MapCodec<? extends RpgCondition> codec();

    /**
     * What a condition is allowed to look at.
     *
     * @param self   the entity the rule belongs to
     * @param player the player involved, if any
     * @param target the other entity in the interaction (attacker, victim, ...), if any
     */
    record ConditionContext(
            LivingEntity self,
            @Nullable ServerPlayer player,
            @Nullable LivingEntity target
    ) {
        /** Context for a non-player entity, no target. */
        public static ConditionContext of(LivingEntity self) {
            return new ConditionContext(self, null, null);
        }
        /** Context where a player is involved, no target. */
        public static ConditionContext of(LivingEntity self, ServerPlayer player) {
            return new ConditionContext(self, player, null);
        }
        /** Copy with a target attached - contexts are immutable, so this returns a new one. */
        public ConditionContext withTarget(LivingEntity newTarget) {
            return new ConditionContext(self, player, newTarget);
        }
    }

    /** Type registry; core's own types are added by {@code CoreConditions.bootstrap()}. */
    DispatchRegistry<RpgCondition> REGISTRY = new DispatchRegistry<>(RpgCondition::codec);
    /** Polymorphic codec used wherever a datapack lists conditions. */
    Codec<RpgCondition> CODEC = REGISTRY.codec();
}