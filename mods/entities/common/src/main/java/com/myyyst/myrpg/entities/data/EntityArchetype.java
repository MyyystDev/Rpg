package com.myyyst.myrpg.entities.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;

/**
 * A lightweight, component-shaped alternative to {@link EntityDefinition}: just a name and
 * a block of stats.
 *
 * <p>Nothing loads these yet - {@code EntitiesData} only registers full entity definitions -
 * so this is scaffolding for a future archetype/component system rather than live content.</p>
 */
public record EntityArchetype(
        Optional<String> displayName,
        Optional<StatsComponent> stats
) {
    public static final Codec<EntityArchetype> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("display_name").forGetter(EntityArchetype::displayName),
            StatsComponent.CODEC.optionalFieldOf("stats").forGetter(EntityArchetype::stats)
    ).apply(i, EntityArchetype::new));

    /** The three common vanilla attributes called out by name, plus arbitrary custom stats. */
    public record StatsComponent(
            Optional<Double> maxHealth,
            Optional<Double> movementSpeed,
            Optional<Double> attackDamage,
            Map<Identifier, Double> custom
    ) {
        public static final Codec<StatsComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("max_health").forGetter(StatsComponent::maxHealth),
                Codec.DOUBLE.optionalFieldOf("movement_speed").forGetter(StatsComponent::movementSpeed),
                Codec.DOUBLE.optionalFieldOf("attack_damage").forGetter(StatsComponent::attackDamage),
                Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE)
                        .optionalFieldOf("custom", Map.of()).forGetter(StatsComponent::custom)
        ).apply(i, StatsComponent::new));
    }
}