package com.myyyst.myrpg.core.effect;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Active effect instances for one owner. All mutation goes through EffectManager. */
public class EffectStore {

    /** Shared by this store's NBT save and by PlayerEffects' world-saved data. */
    static final Codec<List<EffectInstance>> LIST_CODEC = EffectInstance.CODEC.listOf();

    /** At most one instance per effect id; stacking is handled inside the instance. */
    private final List<EffectInstance> instances = new ArrayList<>();

    /** @return the active instance of that effect, or null if the owner does not have it. */
    @Nullable
    public EffectInstance get(Identifier effectId) {
        for (EffectInstance instance : instances) {
            if (instance.effectId.equals(effectId)) return instance;
        }
        return null;
    }

    /** Convenience presence check. */
    public boolean has(Identifier effectId) { return get(effectId) != null; }

    /** Live list - EffectManager adds/removes through it; iterate a copy if you mutate. */
    public List<EffectInstance> all() { return instances; }

    /** True when the owner has no active effects, letting the ticker skip them entirely. */
    public boolean isEmpty() { return instances.isEmpty(); }

    // ------------------------------------------------------------ persistence

    /** Writes the instances under {@code key}; writes nothing when empty. */
    public void save(ValueOutput output, String key) {
        if (instances.isEmpty()) return;
        output.store(key, LIST_CODEC, List.copyOf(instances));
    }

    /** Call EffectManager.reapplyAll(owner) after this. */
    public void load(ValueInput input, String key) {
        instances.clear();
        input.read(key, LIST_CODEC).ifPresent(instances::addAll);
    }
}
