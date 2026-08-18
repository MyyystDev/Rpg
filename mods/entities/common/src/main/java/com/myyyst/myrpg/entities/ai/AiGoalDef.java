package com.myyyst.myrpg.entities.ai;

import com.mojang.serialization.MapCodec;
import com.myyyst.myrpg.core.registry.DispatchRegistry;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

/**
 * One configured AI goal from an entity definition.
 *
 * <p>A definition object is a <em>recipe</em>, not a goal: it is parsed once from JSON and
 * shared by every entity of that type, and {@link #build} turns it into a fresh vanilla
 * {@code Goal} for each spawned entity.</p>
 *
 * <p>Uses the same {@link DispatchRegistry} machinery as core's conditions and actions, so
 * a datapack writes {@code {"type": "myrpg_entities:random_walk", "speed": 0.8}}.</p>
 */
public interface AiGoalDef {

    /** Type registry, populated by {@code AiGoals.init()}. */
    DispatchRegistry<AiGoalDef> REGISTRY = new DispatchRegistry<>(AiGoalDef::codec);

    /** The codec this instance was registered with; used when writing back to JSON. */
    MapCodec<? extends AiGoalDef> codec();

    /** Priority in the goal selector; lower runs first. */
    int priority();

    /** Builds the vanilla goal. Null = goal unavailable for this entity. */
    @Nullable Goal build(RpgEntity entity);
}