package com.myyyst.myrpg.core.editor;

import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.core.data.StatDef;
import com.myyyst.myrpg.core.network.RpgPayloads;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class EditorNet {

    public static final String STATS_FOLDER = "myrpg/stats";

    public static void handleSave(ServerPlayer player, RpgPayloads.SaveStat payload) {
        Identifier statId = Identifier.tryParse(payload.statId());
        if (statId == null) {
            player.sendSystemMessage(Component.literal("[editor] Invalid stat id"));
            return;
        }
        OverlaySaver.save(player, STATS_FOLDER, statId, payload.json(), StatDef.CODEC);
    }

    public static void handleDelete(ServerPlayer player, RpgPayloads.DeleteStat payload) {
        Identifier statId = Identifier.tryParse(payload.statId());
        if (statId == null) {
            player.sendSystemMessage(Component.literal("[editor] Invalid stat id"));
            return;
        }
        OverlaySaver.delete(player, STATS_FOLDER, statId);
    }

    private EditorNet() {}
}