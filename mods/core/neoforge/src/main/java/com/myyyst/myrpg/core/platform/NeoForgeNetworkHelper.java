package com.myyyst.myrpg.core.platform;

import com.myyyst.myrpg.core.platform.services.INetworkHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge implementation of {@code INetworkHelper}.
 * Registered via {@code META-INF/services} and loaded by {@code Services.NETWORK}.
 */
public class NeoForgeNetworkHelper implements INetworkHelper {

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        // Client-only pathway; only ever invoked from client-side code
        // (the dialogue screen). Joins the client-guard cleanup list.
        ClientPacketDistributor.sendToServer(payload);
    }
}