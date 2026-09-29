package com.leyuan.blackflash.client;

import com.leyuan.blackflash.network.BlackFlashEffectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

/**
 * 客户端演出分发：把服务端演出包路由到各特效模块。
 * 飘字/FOV 收缩只对触发者本人展示；闪电对所有人展示。
 */
public final class ClientFx {
    private ClientFx() {}

    /** 由 NetworkHandler 在主线程调用 */
    public static void onEffect(BlackFlashEffectPayload p) {
        Vec3 hit = new Vec3(p.x(), p.y(), p.z());
        Vec3 fist = FlashRenderer.fistPosition(p.attackerId(), hit);
        // 世界层闪电：所有玩家（含旁观者）都渲染
        FlashRenderer.spawn(hit, fist, p.seed());

        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (p.attackerId() == mc.player.getId()) {
                FloatingTextRenderer.show();   // 飘字：仅触发者
                CameraFx.kick();               // FOV 收缩：仅触发者
            }
            if (p.targetId() == mc.player.getId()) {
                ScreenFx.onVictim();           // 被击中者红黑痕迹
            }
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
        FloatingTextRenderer.render(gfx, tracker);
        ScreenFx.renderVictimOverlay(gfx, tracker);
        ScreenFx.renderNearMiss(gfx, tracker);
    }
}
