package com.myyyst.myrpg.core.client;

import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.client.editor.StatLibraryScreen;
import com.myyyst.myrpg.core.client.editor.StatWorkingSet;
import com.myyyst.myrpg.core.network.RpgPayloads;
import net.minecraft.client.Minecraft;

/**
 * Client handler for the "open the stat editor" packet.
 *
 * <p>Lives on the client side only: it turns the definitions the server sent into a
 * {@code StatWorkingSet} (the editor's in-memory scratch copy) and opens the library screen
 * on top of it.</p>
 */
public final class StatEditorClient {
    /** Called when {@code OpenStatEditor} arrives; opens the editor UI. */
    public static void open(RpgPayloads.OpenStatEditor payload) {
        Constants.LOG.info("[myrpg/editor] Received {} stat definitions", payload.stats().size());
        Minecraft.getInstance().gui.setScreen(
                new StatLibraryScreen(StatWorkingSet.from(payload))
        );
    }
    /** Static-only handler: never instantiated. */
    private StatEditorClient() {}
}