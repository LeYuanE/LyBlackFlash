package com.leyuan.blackflash.client;

import com.leyuan.blackflash.BlackFlash;
import com.leyuan.blackflash.network.BlackFlashEffectPayload;
import com.leyuan.blackflash.network.BlackFlashHitPayload;
import com.leyuan.blackflash.network.BlackFlashStatusPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.UUID;

/**
 * 客户端演出分发：把服务端演出包路由到各特效模块。
 * 飘字/FOV 收缩只对触发者本人展示；闪电对所有人展示。
 */
@EventBusSubscriber(modid = BlackFlash.MOD_ID, value = Dist.CLIENT)
public final class ClientFx {
    private ClientFx() {}

    private static ClientLevel statusLevel;
    private static LocalPlayer statusPlayer;

    /** 由 NetworkHandler 在客户端主线程调用；不从本地未同步附件重建状态。 */
    public static void onStatus(BlackFlashStatusPayload payload) {
        if (!isCurrentSelf(payload.playerId(), payload.dimension())) return;
        FxTick.feed();
        if (BlackFlashClientState.apply(payload) && payload.resetVisuals()) {
            clearStatusVisuals();
        }
    }

    /** 命中包只负责即时演出，持久状态和计时始终以完整状态包为准。 */
    public static void onHit(BlackFlashHitPayload payload) {
        if (!isCurrentSelf(payload.playerId(), payload.dimension())) return;
        FxTick.feed();
        if (payload.preview()) {
            FloatingTextRenderer.show(1);
            CameraFx.kick();
            return;
        }
        FloatingTextRenderer.show(payload.streak());
        MugaPulseRenderer.trigger(payload.mugaStacks());
        CameraFx.kick();
    }

    /** 清除状态和命中演出；不改变世界闪电、ScreenFx 或共用 FxClock。 */
    public static void resetStatus() {
        BlackFlashClientState.reset();
        clearStatusVisuals();
    }

    private static void clearStatusVisuals() {
        FloatingTextRenderer.clear();
        MugaPulseRenderer.clear();
        CameraFx.clear();
    }

    /** 同维度重登/重生也会替换对象，不能只比较维度键或玩家 UUID。 */
    public static void syncStatusLifecycle() {
        FlashRenderer.syncClientLevel();
        var mc = Minecraft.getInstance();
        if (statusLevel != mc.level || statusPlayer != mc.player) {
            resetStatus();
            statusLevel = mc.level;
            statusPlayer = mc.player;
        }
    }

    private static boolean isCurrentSelf(UUID playerId, ResourceLocation dimension) {
        syncStatusLifecycle();
        var mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null
                && playerId.equals(mc.player.getUUID())
                && dimension.equals(mc.level.dimension().location());
    }

    @SubscribeEvent
    public static void onStatusTick(ClientTickEvent.Post event) {
        syncStatusLifecycle();
    }

    @SubscribeEvent
    public static void onStatusLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetStatus();
        statusLevel = null;
        statusPlayer = null;
    }

    /** 由 NetworkHandler 在主线程调用 */
    public static void onEffect(BlackFlashEffectPayload p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 hit = new Vec3(p.x(), p.y(), p.z());
        Vec3 fist = FlashRenderer.fistPosition(p.attackerId(), hit);
        // 世界层闪电：同维度附近玩家（含旁观者）都渲染
        FlashRenderer.spawn(hit, fist, p.seed());

        // 本人的飘字/FOV 改由 hit 包驱动，避免广播演出包覆盖连击文字或重复触发。
        if (p.targetId() == mc.player.getId()) {
            ScreenFx.onVictim();           // 被击中者红黑痕迹
        }
    }

    public static void onNearMiss(int attackerId) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && attackerId == mc.player.getId()) {
            ScreenFx.onNearMiss();
        }
    }

    /** HUD 层内容：挂在所有原版层之上，绘制在物品栏上方区域 */
    public static void renderHud(GuiGraphics gfx, DeltaTracker tracker) {
        syncStatusLifecycle();
        if (Minecraft.getInstance().options.hideGui) return;
        MugaPulseRenderer.render(gfx, tracker);
        BlackFlashStatusRenderer.render(gfx, tracker);
        FloatingTextRenderer.render(gfx, tracker);
        ScreenFx.renderVictimOverlay(gfx, tracker);
        ScreenFx.renderNearMiss(gfx, tracker);
    }
}
