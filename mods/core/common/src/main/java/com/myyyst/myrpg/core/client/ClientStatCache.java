package com.myyyst.myrpg.core.client;

import com.myyyst.myrpg.core.network.RpgPayloads;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/** Client-side view of the local player's HUD stats. */
public final class ClientStatCache {

    private static final Map<Identifier, RpgPayloads.StatEntry> ENTRIES = new LinkedHashMap<>();

    public static void accept(RpgPayloads.SyncStats payload) {
        for (RpgPayloads.StatEntry entry : payload.entries()) {
            ENTRIES.put(entry.statId(), entry);
        }
    }

    public static Iterable<RpgPayloads.StatEntry> entries() { return ENTRIES.values(); }

    public static void clear() { ENTRIES.clear(); }   // call on disconnect

    private ClientStatCache() {}
}