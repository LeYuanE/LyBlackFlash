package com.leyuan.blackflash.client;

import com.leyuan.blackflash.BlackFlash;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * 客户端初始化（模组总线，由主类在客户端侧显式注册）：
 * 注册黑闪 HUD 层（飘字 / 被击中者屏幕特效 / 微反馈）。
 */
public final class BlackFlashClient {
    private BlackFlashClient() {}

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BlackFlash.rl("blackflash_hud"), ClientFx::renderHud);
    }
}
