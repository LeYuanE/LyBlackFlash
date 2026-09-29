package com.leyuan.blackflash.client;

import com.leyuan.blackflash.BlackFlash;
import com.leyuan.blackflash.config.BlackFlashConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 触发瞬间极轻的 FOV 收缩（不含镜头旋转），仅触发者，约 220ms 回弹。
 */
@EventBusSubscriber(modid = BlackFlash.MOD_ID, value = Dist.CLIENT)
public final class CameraFx {
    private CameraFx() {}

    private static final long KICK_MS = 220L;
    private static final double KICK_FACTOR = 0.94;
    private static volatile long kickStartMs = -1L;

    public static void kick() {
        kickStartMs = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!BlackFlashConfig.CONFIG.fovKickEnabled.get()) return;
        long s = kickStartMs;
        if (s < 0) return;
        long age = System.currentTimeMillis() - s;
        if (age >= KICK_MS) { kickStartMs = -1; return; }
        double recover = age / (double) KICK_MS;              // 0 → 1
        double factor = KICK_FACTOR + (1.0 - KICK_FACTOR) * recover;
        event.setFOV(event.getFOV() * factor);
    }
}
