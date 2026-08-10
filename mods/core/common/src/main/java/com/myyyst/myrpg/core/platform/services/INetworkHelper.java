package com.myyyst.myrpg.core.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public interface INetworkHelper {

    /** Server -> a specific client. */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Client -> server. Only ever call from client-side code. */
    void sendToServer(CustomPacketPayload payload);
}