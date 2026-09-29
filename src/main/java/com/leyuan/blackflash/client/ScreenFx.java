package com.leyuan.blackflash.client;

import com.leyuan.blackflash.BlackFlash;
import com.leyuan.blackflash.config.BlackFlashConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 两处"挨打方/旁观方"的轻量 HUD：
 *  - 被黑闪击中的玩家：屏幕出现轻微红黑痕迹（比触发者演出更淡，无飘字）；
 *  - 「差一点」微反馈：物品栏上方一线暗红细痕，极短，节流由服务端控制。
 */
public final class ScreenFx {
    private ScreenFx() {}

    private static volatile long victimStartMs = -1L;
    private static volatile long nearMissStartMs = -1L;
    private static final long VICTIM_MS = 700L;
    private static final long NEAR_MISS_MS = 450L;

    public static void onVictim() {
        if (!BlackFlashConfig.CONFIG.hitPlayerScreenFx.get()) return;
        victimStartMs = System.currentTimeMillis();
    }

    public static void onNearMiss() {
        nearMissStartMs = System.currentTimeMillis();
    }

    public static void renderVictimOverlay(GuiGraphics gfx, DeltaTracker tracker) {
        long s = victimStartMs;
        if (s < 0) return;
        long age = System.currentTimeMillis() - s;
        if (age > VICTIM_MS) { victimStartMs = -1; return; }
        double t = age / (double) VICTIM_MS;
        int alpha = (int) (70 * (1.0 - t)); // 最亮也只有 27% 不透明度：刻意比触发者演出更淡
        if (alpha <= 0) return;

        int w = gfx.guiWidth();
        int h = gfx.guiHeight();

        // 上下暗红横带 + 左右窄黑带 + 少量斜向"痕迹"点条
        int darkRed = (alpha << 24) | 0x590A0C;
        int blackish = ((alpha / 2) << 24) | 0x0A0303;
        gfx.fill(0, 0, w, h / 10, darkRed);
        gfx.fill(0, h * 9 / 10, w, h, darkRed);
        gfx.fill(0, 0, w / 20, h, blackish);
        gfx.fill(w * 19 / 20, 0, w, h, blackish);

        int streak = ((alpha / 3) << 24) | 0xAE2524;
        long seed = 0x5EEDL;
        for (int i = 0; i < 6; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int x = (int) Math.floorMod(seed >>> 33, w);
            int y = (int) Math.floorMod(seed >>> 17, h);
            gfx.fill(x, y, x + 2, y + 10, streak);
        }
    }

    public static void renderNearMiss(GuiGraphics gfx, DeltaTracker tracker) {
        long s = nearMissStartMs;
        if (s < 0) return;
        long age = System.currentTimeMillis() - s;
        if (age > NEAR_MISS_MS) { nearMissStartMs = -1; return; }
        double t = age / (double) NEAR_MISS_MS;
        int alpha = (int) (120 * (1.0 - t));
        int w = gfx.guiWidth();
        int h = gfx.guiHeight();
        int mid = w / 2;
        // 物品栏上方的一线暗红：短、暗、快
        gfx.fill(mid - 24, h - 58, mid + 24, h - 57, (alpha << 24) | 0x590A0C);
        gfx.fill(mid - 8, h - 56, mid + 8, h - 55, ((alpha / 2) << 24) | 0x0A0303);
    }
}
