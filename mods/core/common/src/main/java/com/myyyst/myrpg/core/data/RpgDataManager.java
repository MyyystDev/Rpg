package com.myyyst.myrpg.core.data;

import com.mojang.serialization.Codec;
import com.myyyst.myrpg.core.Constants;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Generic loader for one folder of datapack JSON files.
 *
 * <p>Minecraft calls {@link #apply} on every {@code /reload} (and on world load) with the
 * already-parsed entries; this class simply swaps them in wholesale, so a reload can never
 * leave a half-updated table behind. Files that fail to parse are dropped by the vanilla
 * loader with an error in the log and are simply absent here.</p>
 *
 * @param <T> the parsed content type (StatDef, EffectDefinition, ...)
 */
public class RpgDataManager<T> extends SimpleJsonResourceReloadListener<T> {

    /** Only used in the "Loaded N x(s)" log line. */
    private final String displayName;
    /** volatile: written on the reload thread, read by game logic on the server thread. */
    private volatile Map<Identifier, T> values = Collections.emptyMap();

    /**
     * @param folder resource path relative to {@code data/&lt;namespace&gt;/}, e.g. {@code "myrpg/stats"}
     * @param codec  parser for a single file
     */
    public RpgDataManager(String folder, Codec<T> codec, String displayName) {
        super(codec, FileToIdConverter.json(folder));
        this.displayName = displayName;
    }

    /** Called by the resource-reload pipeline once all files have been read and parsed. */
    @Override
    protected void apply(Map<Identifier, T> entries, ResourceManager manager, ProfilerFiller profiler) {
        this.values = Collections.unmodifiableMap(entries);
        Constants.LOG.info("[rpg] Loaded {} {}(s)", values.size(), displayName);
    }

    /** Looks up one entry; empty when no datapack defines it. */
    public Optional<T> get(Identifier id) { return Optional.ofNullable(values.get(id)); }

    /** Every loaded entry (immutable). Used by the editor and by commands that list content. */
    public Map<Identifier, T> all() { return values; }
}