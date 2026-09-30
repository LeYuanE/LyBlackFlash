package com.leyuan.blackflash.logic;

import com.leyuan.blackflash.BlackFlash;
import com.leyuan.blackflash.network.BlackFlashStatusPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** 服务端私有状态同步，不向旁观者广播。 */
@EventBusSubscriber(modid = BlackFlash.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class StatusSyncHandler {
    private StatusSyncHandler() {}

    public static void send(ServerPlayer player) {
        send(player, false);
    }

    public static void send(ServerPlayer player, boolean resetVisuals) {
        player.connection.send(new BlackFlashStatusPayload(player.getUUID(),
                player.level().dimension().location(), GrowthManager.snapshot(player), resetVisuals));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player, true);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player, true);
    }

    @SubscribeEvent
    public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player, true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) GrowthManager.forgetPlayer(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 10 == 0) send(player);
    }
}
