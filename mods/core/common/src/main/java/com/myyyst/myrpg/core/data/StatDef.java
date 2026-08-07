package com.myyyst.myrpg.core.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record StatDef(
        Optional<String> displayName,
        double defaultValue,
        double min,
        double max,
        Optional<Identifier> vanillaAttribute,
        double attributeScaling
        //List<StatEffect> effects
) {
    public static final Codec<StatDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("display_name").forGetter(StatDef::displayName),
            Codec.DOUBLE.optionalFieldOf("default", 0.0).forGetter(StatDef::defaultValue),
            Codec.DOUBLE.optionalFieldOf("min", 0.0).forGetter(StatDef::min),
            Codec.DOUBLE.optionalFieldOf("max", Double.MAX_VALUE).forGetter(StatDef::max),
            Identifier.CODEC.optionalFieldOf("vanilla_attribute").forGetter(StatDef::vanillaAttribute),
            Codec.DOUBLE.optionalFieldOf("attribute_scaling", 1.0).forGetter(StatDef::attributeScaling)
            //StatEffect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(StatDef::effects)
    ).apply(i, StatDef::new));
}
