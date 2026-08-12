package com.myyyst.myrpg.entities.ai;

import com.mojang.serialization.MapCodec;
import com.myyyst.myrpg.core.registry.DispatchRegistry;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

/** One configured AI goal from an entity definition. */
public interface AiGoalDef {

    DispatchRegistry<AiGoalDef> REGISTRY = new DispatchRegistry<>(AiGoalDef::codec);

    MapCodec<? extends AiGoalDef> codec();

    /** Priority in the goal selector; lower runs first. */
    int priority();

    /** Builds the vanilla goal. Null = goal unavailable for this entity. */
    @Nullable Goal build(RpgEntity entity);
}