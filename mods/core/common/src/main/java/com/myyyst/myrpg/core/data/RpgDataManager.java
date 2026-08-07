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

public class RpgDataManager<T> extends SimpleJsonResourceReloadListener<T> {

    private final String displayName;
    private volatile Map<Identifier, T> values = Collections.emptyMap();

    public RpgDataManager(String folder, Codec<T> codec, String displayName) {
        super(codec, FileToIdConverter.json(folder));
        this.displayName = displayName;
    }

    @Override
    protected void apply(Map<Identifier, T> entries, ResourceManager manager, ProfilerFiller profiler) {
        this.values = Collections.unmodifiableMap(entries);
        Constants.LOG.info("[rpg] Loaded {} {}(s)", values.size(), displayName);
    }

    public Optional<T> get(Identifier id) { return Optional.ofNullable(values.get(id)); }

    public Map<Identifier, T> all() { return values; }
}