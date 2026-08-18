package com.myyyst.myrpg.core.client;

import com.myyyst.myrpg.core.network.RpgPayloads;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side view of the local player's HUD stats.
 *
 * <p>Filled from {@code SyncStats} packets and read by {@code StatHudOverlay}. Everything
 * needed for drawing (name, colour, bounds, HUD mode) arrives inside the entry, so the
 * client never touches datapacks.</p>
 */
public final class ClientStatCache {

    /** Insertion-ordered, so HUD entries keep the order the server first sent them in. */
    private static final Map<Identifier, RpgPayloads.StatEntry> ENTRIES = new LinkedHashMap<>();

    /**
     * Merges an incoming sync. Entries are replaced by id rather than cleared wholesale,
     * because the per-tick packets only carry the stats that changed.
     */
    public static void accept(RpgPayloads.SyncStats payload) {
        for (RpgPayloads.StatEntry entry : payload.entries()) {
            ENTRIES.put(entry.statId(), entry);
        }
    }

    /** Everything currently known, in display order. */
    public static Iterable<RpgPayloads.StatEntry> entries() { return ENTRIES.values(); }

    /** Drops all cached stats so the next world does not inherit the previous one's HUD. */
    public static void clear() { ENTRIES.clear(); }   // call on disconnect

    /** Static-only cache: never instantiated. */
    private ClientStatCache() {}
}