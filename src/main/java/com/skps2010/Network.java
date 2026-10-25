package com.skps2010;

import com.skps2010.client.ClientPayloadHandler;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Network {
    private Network() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        // optional(): players without the mod (or servers without it) can still connect.
        PayloadRegistrar registrar = event.registrar("1").optional();

        // The lambda bodies only run on the client, so ClientPayloadHandler is never
        // class-loaded on a dedicated server.
        registrar.playToClient(FoodHistoryPayload.TYPE, FoodHistoryPayload.STREAM_CODEC,
                (payload, ctx) -> ClientPayloadHandler.handleFoodHistory(payload));
        registrar.playToClient(CravingPayload.TYPE, CravingPayload.STREAM_CODEC,
                (payload, ctx) -> ClientPayloadHandler.handleCraving(payload));
    }

    /** Sends only if the player is real and their client has this mod installed. */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player instanceof FakePlayer || player.connection == null) return;
        if (!player.connection.hasChannel(payload)) return;
        PacketDistributor.sendToPlayer(player, payload);
    }
}
