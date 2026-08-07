package com.myyyst.myrpg.core.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class DispatchRegistry<T> {

    private final Map<Identifier, MapCodec<? extends T>> entries = new LinkedHashMap<>();
    private final Codec<T> codec;

    public DispatchRegistry(Function<T, MapCodec<? extends T>> codecGetter) {
        this.codec = Identifier.CODEC.dispatch(
                "type",
                value -> {
                    MapCodec<? extends T> c = codecGetter.apply(value);
                    for (var e : entries.entrySet()) {
                        if (e.getValue() == c) return e.getKey();
                    }
                    throw new IllegalStateException("Unregistered type: " + value.getClass());
                },
                entries::get);
    }

    public void register(Identifier id, MapCodec<? extends T> entryCodec) {
        if (entries.putIfAbsent(id, entryCodec) != null) {
            throw new IllegalStateException("Duplicate registration: " + id);
        }
    }

    public boolean contains(Identifier id) { return entries.containsKey(id); }

    public Map<Identifier, MapCodec<? extends T>> view() { return Map.copyOf(entries); }

    public Codec<T> codec() { return codec; }
}