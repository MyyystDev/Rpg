package com.myyyst.myrpg.core.variable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * A variable's value: number or string. Exactly one side is present.
 *
 * <p>Modelled as a record with two {@link Optional} fields (rather than {@code Object})
 * so it serialises cleanly through a codec and stays immutable. Stored by {@link Variables}.</p>
 */
public record VarValue(Optional<Double> number, Optional<String> string) {

    /** Persisted/wire form: {@code {"number": 5.0}} or {@code {"string": "abc"}}. */
    public static final Codec<VarValue> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("number").forGetter(VarValue::number),
            Codec.STRING.optionalFieldOf("string").forGetter(VarValue::string)
    ).apply(i, VarValue::new));

    /** Wraps a numeric value. */
    public static VarValue of(double value) { return new VarValue(Optional.of(value), Optional.empty()); }
    /** Wraps a text value. */
    public static VarValue of(String value) { return new VarValue(Optional.empty(), Optional.of(value)); }

    /** True when this holds a number, i.e. it can take part in numeric comparisons. */
    public boolean isNumber() { return number.isPresent(); }
    /** Numeric view; string values (and empty ones) read as 0. */
    public double asNumber() { return number.orElse(0.0); }
    /** Display view; numbers are stringified, an empty value reads as "". */
    public String asString() { return string.orElseGet(() -> number.map(String::valueOf).orElse("")); }
}