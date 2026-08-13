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

    static final Codec<List<EffectInstance>> LIST_CODEC = EffectInstance.CODEC.listOf();

    private final List<EffectInstance> instances = new ArrayList<>();

    @Nullable
    public EffectInstance get(Identifier effectId) {
        for (EffectInstance instance : instances) {
            if (instance.effectId.equals(effectId)) return instance;
        }
        return null;
    }

    public boolean has(Identifier effectId) { return get(effectId) != null; }

    public List<EffectInstance> all() { return instances; }

    public boolean isEmpty() { return instances.isEmpty(); }

    // ------------------------------------------------------------ persistence

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
