package com.myyyst.myrpg.core.variable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** A variable's value: number or string. Exactly one side is present. */
public record VarValue(Optional<Double> number, Optional<String> string) {

    public static final Codec<VarValue> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("number").forGetter(VarValue::number),
            Codec.STRING.optionalFieldOf("string").forGetter(VarValue::string)
    ).apply(i, VarValue::new));

    public static VarValue of(double value) { return new VarValue(Optional.of(value), Optional.empty()); }
    public static VarValue of(String value) { return new VarValue(Optional.empty(), Optional.of(value)); }

    public boolean isNumber() { return number.isPresent(); }
    public double asNumber() { return number.orElse(0.0); }
    public String asString() { return string.orElseGet(() -> number.map(String::valueOf).orElse("")); }
}