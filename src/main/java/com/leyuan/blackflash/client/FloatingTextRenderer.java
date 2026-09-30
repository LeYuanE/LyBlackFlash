package com.leyuan.blackflash.client;

import com.leyuan.blackflash.config.BlackFlashConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 触发者屏幕下方的「黑闪」飘字：弹出（过冲 1.2 倍）→ 回弹 → 停留 → 上浮淡出。
 * 大小与时长可配：floatingTextScale（默认 1.5 倍）、floatingTextMs（默认 1400ms）。
 * 仅触发者可见。字体直接用原版字体（含中文字形），不做自定义贴图。
 */
public final class FloatingTextRenderer {
    private FloatingTextRenderer() {}

    private static final float STREAK_SCALE = 0.65f;
    private static final int STREAK_GAP = 4;
    private static volatile long startMs = -1L;
    private static volatile int currentStreak = 1;

    public static void show() {
        show(1);
    }

    public static void show(int streak) {
        currentStreak = Math.max(1, streak);
        startMs = FxClock.gameMillis();
    }

    public static void clear() {
        startMs = -1L;
        currentStreak = 1;
    }

    public static void render(GuiGraphics gfx, DeltaTracker tracker) {
        FxTick.feed();
        long s = startMs;
        if (s < 0) return;
        BlackFlashConfig cfg = BlackFlashConfig.CONFIG;
        long age = FxClock.gameMillis() - s;
        int total = cfg.floatingTextMs.get();
        if (age > total) { startMs = -1; return; }
        if (Minecraft.getInstance().player == null) return;

        double t = age / (double) total;
        float base = cfg.floatingTextScale.get().floatValue();

        // 缩放曲线：0–18% 冲到 1.2×基准，18–30% 回弹到 1×基准，之后保持
        float scale;
        if (t < 0.18) scale = (float) ((0.2 + (t / 0.18) * 1.0) * base);
        else if (t < 0.30) scale = (float) ((1.2 - ((t - 0.18) / 0.12) * 0.2) * base);
        else scale = base;

        // 60% 后上浮淡出
        double rise = t > 0.6 ? (t - 0.6) / 0.4 * 10.0 : 0.0;
        int alpha = t < 0.6 ? 255 : (int) Math.max(0, 255 * (1.0 - (t - 0.6) / 0.4));
        // 原版 Font.adjustColor 会把 alpha 0~3 的颜色当成"未指定透明度"并强制拉回 255，
        // 淡出末帧若落在这个区间会突然闪亮一帧。低于 4 直接不画。
        if (alpha < 4) return;

        Component text = Component.translatable("text.black_flash.name");
        int streak = currentStreak;
        Component streakText = streak > 1 ? Component.literal("×" + streak) : null;
        var font = Minecraft.getInstance().font;
        int w = font.width(text);
        int totalWidth = w + (streakText == null ? 0
                : STREAK_GAP + (int) Math.ceil(font.width(streakText) * STREAK_SCALE));
        int titleX = -totalWidth / 2;
        int cx = gfx.guiWidth() / 2;
        int cy = (int) (gfx.guiHeight() - 52 - rise); // 物品栏上方

        PoseStack pose = gfx.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0.0);
        pose.scale(scale, scale, 1.0f);
        // 红字主体 + 黑色阴影，保证在任何背景上都可读
        gfx.drawString(font, text, titleX + 1, 1, (alpha << 24) | 0x0A0303, false);
        gfx.drawString(font, text, titleX, 0, (alpha << 24) | 0xAE2524, false);
        if (streakText != null) {
            // 连击数字随标题共用弹出/淡出动画，以较小字号靠右对齐基线。
            pose.pushPose();
            pose.translate(titleX + w + STREAK_GAP, (font.lineHeight - 1) * (1.0f - STREAK_SCALE), 0.0);
            pose.scale(STREAK_SCALE, STREAK_SCALE, 1.0f);
            gfx.drawString(font, streakText, 1, 1, (alpha << 24) | 0x0A0303, false);
            gfx.drawString(font, streakText, 0, 0, (alpha << 24) | 0xAE2524, false);
            pose.popPose();
        }
        pose.popPose();
    }
}
