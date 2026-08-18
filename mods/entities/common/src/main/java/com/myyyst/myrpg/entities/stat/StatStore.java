package com.myyyst.myrpg.core.stat;

import com.mojang.serialization.Codec;
import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.core.data.StatDef;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.HashMap;
import java.util.Map;

public class StatStore {

    private static final Codec<Map<Identifier, Double>> VALUES_CODEC =
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE);

    private final Map<Identifier, Double> values = new HashMap<>();

    // ------------------------------------------------------------ access

    public double get(Identifier stat) {
        Double stored = values.get(stat);
        if (stored != null) return stored;
        return CoreData.STATS.get(stat).map(StatDef::defaultValue).orElse(0.0);
    }

    public void set(Identifier stat, double value) {
        StatDef def = CoreData.STATS.get(stat).orElse(null);
        if (def == null) {
            Constants.LOG.warn("[myrpg] Setting undefined stat {} — no definition loaded", stat);
        } else {
            value = Mth.clamp(value, def.min(), def.max());
        }
        values.put(stat, value);
    }

    public void add(Identifier stat, double delta) {
        set(stat, get(stat) + delta);
    }

    public void clear() {
        values.clear();
    }

    public Map<Identifier, Double> all() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    // ------------------------------------------------------------ vanilla bridge

    public void applyVanillaAttributes(LivingEntity owner) {
        values.forEach((statId, value) -> {
            StatDef def = CoreData.STATS.get(statId).orElse(null);
            if (def == null || def.vanillaAttribute().isEmpty()) return;

            Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE
                    .get(def.vanillaAttribute().get()).orElse(null);

            if (attribute == null) {
                Constants.LOG.warn("[myrpg] Stat {} maps to unknown attribute {}",
                        statId, def.vanillaAttribute().get());
                return;
            }
            AttributeInstance instance = owner.getAttribute(attribute);
            if (instance == null) return; // owner's species lacks this attribute

            Identifier modifierId = Identifier.fromNamespaceAndPath(
                    Constants.MOD_ID, "stat/" + statId.getNamespace() + "/" + statId.getPath());
            instance.removeModifier(modifierId);
            instance.addPermanentModifier(new AttributeModifier(
                    modifierId, value * def.attributeScaling(),
                    AttributeModifier.Operation.ADD_VALUE));
        });
    }

    // ------------------------------------------------------------ persistence

    public void save(ValueOutput output, String key) {
        if (values.isEmpty()) return;
        output.store(key, VALUES_CODEC, Map.copyOf(values));
    }

    public void load(ValueInput input, String key) {
        values.clear();
        input.read(key, VALUES_CODEC).ifPresent(values::putAll);
    }
}