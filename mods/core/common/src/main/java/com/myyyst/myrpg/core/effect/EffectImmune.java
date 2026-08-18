package com.myyyst.myrpg.core.effect;

import com.myyyst.myrpg.core.data.EffectDefinition;
import net.minecraft.resources.Identifier;

/** Implemented by entities that can refuse effects (custom entity immunities). */
public interface EffectImmune {
    /**
     * Checked by {@code EffectManager.apply} before anything else happens.
     *
     * @param definition passed too so immunity can be decided by category or tag,
     *                   not just by exact id
     * @return true to refuse the effect entirely
     */
    boolean isEffectImmune(Identifier effectId, EffectDefinition definition);
}
