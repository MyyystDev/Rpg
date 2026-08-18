package com.myyyst.myrpg.entities.ai;

import com.mojang.serialization.MapCodec;
import com.myyyst.myrpg.core.registry.DispatchRegistry;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

/**
 * One target-selection rule from an entity definition.
 *
 * <p>Same shape as {@link AiGoalDef}, but these go into the entity's <em>target</em>
 * selector - they decide who counts as an enemy, while AI goals decide what to do about it.</p>
 */
public interface TargetDef {

    /** Type registry, populated by {@code Targets.init()}. */
    DispatchRegistry<TargetDef> REGISTRY = new DispatchRegistry<>(TargetDef::codec);

    /** The codec this instance was registered with; used when writing back to JSON. */
    MapCodec<? extends TargetDef> codec();

    /** Priority in the target selector; lower runs first. */
    int priority();

    /** Builds the vanilla target goal. Null = unavailable for this entity. */
    @Nullable Goal build(RpgEntity entity);
}