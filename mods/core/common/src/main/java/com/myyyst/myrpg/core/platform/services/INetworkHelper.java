package com.myyyst.myrpg.core.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-specific packet sending, reached through {@code Services.NETWORK}.
 *
 * <p>The payload types themselves are declared in the common module ({@code RpgPayloads},
 * {@code EntitiesPayloads}); only the act of putting them on the wire differs per loader.</p>
 */
public interface INetworkHelper {

    /** Server -> a specific client. */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Client -> server. Only ever call from client-side code. */
    void sendToServer(CustomPacketPayload payload);
}