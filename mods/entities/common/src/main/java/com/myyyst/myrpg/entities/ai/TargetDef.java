package com.myyyst.myrpg.entities.ai;

import com.mojang.serialization.MapCodec;
import com.myyyst.myrpg.core.registry.DispatchRegistry;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

/** One target-selection rule from an entity definition. */
public interface TargetDef {

    DispatchRegistry<TargetDef> REGISTRY = new DispatchRegistry<>(TargetDef::codec);

    MapCodec<? extends TargetDef> codec();

    int priority();

    @Nullable Goal build(RpgEntity entity);
}