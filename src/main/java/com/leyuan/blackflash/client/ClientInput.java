package com.leyuan.blackflash.client;

import com.leyuan.blackflash.BlackFlash;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 游戏内按键消费；不接管聊天、背包或其他 Screen 的输入。 */
@EventBusSubscriber(modid = BlackFlash.MOD_ID, value = Dist.CLIENT)
public final class ClientInput {
    private ClientInput() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (BlackFlashClient.PROFICIENCY_KEY == null
                || mc.player == null || mc.level == null || mc.screen != null) return;
        if (BlackFlashClient.PROFICIENCY_KEY.consumeClick()) {
            mc.setScreen(new BlackFlashStatsScreen());
        }
    }
}
