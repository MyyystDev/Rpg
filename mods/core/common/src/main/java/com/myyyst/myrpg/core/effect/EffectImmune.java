package com.myyyst.myrpg.core.effect;

import com.myyyst.myrpg.core.data.EffectDefinition;
import net.minecraft.resources.Identifier;

/** Implemented by entities that can refuse effects (custom entity immunities). */
public interface EffectImmune {
    boolean isEffectImmune(Identifier effectId, EffectDefinition definition);
}
