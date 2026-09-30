package com.leyuan.blackflash.network;

import com.leyuan.blackflash.BlackFlash;
import com.leyuan.blackflash.client.ClientFx;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 网络包注册与收发。演出包发给同维度附近玩家（含旁观者），不向其他世界投影闪电。
 * 本类由主类显式注册到模组总线。
 */
public final class NetworkHandler {
    private NetworkHandler() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(BlackFlashEffectPayload.TYPE, BlackFlashEffectPayload.STREAM_CODEC,
                NetworkHandler::handleEffect);
        registrar.playToClient(NearMissPayload.TYPE, NearMissPayload.STREAM_CODEC,
                NetworkHandler::handleNearMiss);
    }

    public static void sendToNearbyPlayers(ServerLevel level, BlackFlashEffectPayload payload) {
        for (ServerPlayer player : level.players()) {
            if (player.getId() == payload.attackerId() || player.getId() == payload.targetId()
                    || player.distanceToSqr(payload.x(), payload.y(), payload.z()) <= 64 * 64) {
                player.connection.send(payload);
            }
        }
    }

    private static void handleEffect(BlackFlashEffectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientFx.onEffect(payload));
    }

    private static void handleNearMiss(NearMissPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientFx.onNearMiss(payload.attackerId()));
    }
}
