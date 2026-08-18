package com.myyyst.myrpg.core.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A small "type -> codec" registry that turns a family of subtypes into one polymorphic
 * {@link Codec}, driven by a {@code "type"} field in JSON.
 *
 * <p>This is the backbone of the whole data-driven design: conditions, actions, triggers,
 * stage effects and AI goals each own a {@code DispatchRegistry}, so a datapack can write</p>
 *
 * <pre>{@code { "type": "myrpg:stat_compare", "stat": "myrpg:rage", "min": 50 } }</pre>
 *
 * <p>and the registry decides which Java class parses the rest of the object. Registration
 * happens once at startup in the various {@code bootstrap()} methods.</p>
 *
 * @param <T> the common supertype (e.g. {@code RpgCondition})
 */
public class DispatchRegistry<T> {

    /** Insertion-ordered so editor drop-downs list types in registration order. */
    private final Map<Identifier, MapCodec<? extends T>> entries = new LinkedHashMap<>();
    private final Codec<T> codec;

    /**
     * @param codecGetter maps an instance back to its own codec, used when *writing* JSON
     *                    (usually a {@code T::codec} method reference)
     */
    public DispatchRegistry(Function<T, MapCodec<? extends T>> codecGetter) {
        this.codec = Identifier.CODEC.dispatch(
                "type",
                // Serialising: find the id this instance's codec was registered under.
                value -> {
                    MapCodec<? extends T> c = codecGetter.apply(value);
                    for (var e : entries.entrySet()) {
                        if (e.getValue() == c) return e.getKey();
                    }
                    throw new IllegalStateException("Unregistered type: " + value.getClass());
                },
                // Deserialising: look up the codec for the id found in the "type" field.
                entries::get);
    }

    /**
     * Registers one subtype under {@code id}.
     *
     * @throws IllegalStateException if the id is already taken - a fail-fast guard against
     *         two bootstraps claiming the same name.
     */
    public void register(Identifier id, MapCodec<? extends T> entryCodec) {
        if (entries.putIfAbsent(id, entryCodec) != null) {
            throw new IllegalStateException("Duplicate registration: " + id);
        }
    }

    /** @return true if a subtype is registered under {@code id} (used to validate editor input). */
    public boolean contains(Identifier id) { return entries.containsKey(id); }

    /** Immutable snapshot of every registered type, for editor pickers and debugging. */
    public Map<Identifier, MapCodec<? extends T>> view() { return Map.copyOf(entries); }

    /** The polymorphic codec to use wherever a {@code T} appears in a datapack file. */
    public Codec<T> codec() { return codec; }
}