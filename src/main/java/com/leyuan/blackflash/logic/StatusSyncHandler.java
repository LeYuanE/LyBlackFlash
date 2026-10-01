package com.leyuan.blackflash.logic;

import com.leyuan.blackflash.BlackFlash;
import com.leyuan.blackflash.attachment.BlackFlashAttachments;
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

    /**
     * 周期性校正。不能简单删掉：无我过期依赖 {@link GrowthManager#mugaStacks} 的写回清理，
     * 而该调用发生在 {@link GrowthManager#snapshot} 内；客户端倒计时也需要这个频率校正。
     *
     * <p>但只对「身上确实有可显示状态」的玩家发送，从未接触过本模组的玩家不会持续收到包。
     * 熟练度一旦大于 0 会永久保留，因此这里用 count &gt; 0 兜底，不会漏掉有成长的玩家。
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 10 != 0) return;
        if (player.getData(BlackFlashAttachments.COUNT) <= 0
                && player.getData(BlackFlashAttachments.STREAK) <= 0
                && player.getData(BlackFlashAttachments.MUGA_STACKS) <= 0) return;
        send(player);
    }
}
