package com.leyuan.blackflash.client;

import com.leyuan.blackflash.BlackFlash;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * 客户端初始化（模组总线，由主类在客户端侧显式注册）：
 * 注册黑闪 HUD 层（飘字 / 被击中者屏幕特效 / 微反馈）。
 */
public final class BlackFlashClient {
    public static final KeyMapping PROFICIENCY_KEY = new KeyMapping(
            "key.black_flash.proficiency", InputConstants.Type.KEYSYM, InputConstants.KEY_K,
            "key.categories.black_flash");

    private BlackFlashClient() {}

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        PROFICIENCY_KEY.setKeyConflictContext(KeyConflictContext.IN_GAME);
        event.register(PROFICIENCY_KEY);
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        // 瞬时演出保持 above-all；状态条插在 FOOD_LEVEL 之后，正好占用饱食度上方的一行。
        event.registerAboveAll(BlackFlash.rl("blackflash_hud"), ClientFx::renderHud);
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL,
                BlackFlash.rl("blackflash_status_bar"), BlackFlashStatusRenderer::render);
    }
}
