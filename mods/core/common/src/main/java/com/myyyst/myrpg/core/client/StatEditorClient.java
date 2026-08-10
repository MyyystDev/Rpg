package com.myyyst.myrpg.core.client;

import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.client.editor.StatLibraryScreen;
import com.myyyst.myrpg.core.client.editor.StatWorkingSet;
import com.myyyst.myrpg.core.network.RpgPayloads;
import net.minecraft.client.Minecraft;

public final class StatEditorClient {
    public static void open(RpgPayloads.OpenStatEditor payload) {
        Constants.LOG.info("[myrpg/editor] Received {} stat definitions", payload.stats().size());
        Minecraft.getInstance().gui.setScreen(
                new StatLibraryScreen(StatWorkingSet.from(payload))
        );
    }
    private StatEditorClient() {}
}