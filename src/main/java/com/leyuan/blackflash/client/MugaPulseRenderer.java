package com.leyuan.blackflash.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/** 无我刷新时的本人屏幕边缘脉冲；不注册世界渲染，也不生成实体周围光环。 */
public final class MugaPulseRenderer {
    private static final double DURATION_TICKS = 8.0; // 20 TPS 时约 400ms；暂停时冻结。
    private static final int EDGE_COLOR = 0x590A0C;
    private static double startTick = Double.NaN;
    private static ClientLevel ownerLevel;
    private static LocalPlayer ownerPlayer;

    private MugaPulseRenderer() {}

    /** 仅在客户端主线程、确认是本人的无我刷新后调用。 */
    public static void trigger() {
        trigger(1);
    }

    /** 仅在客户端主线程、确认是本人的命中/无我刷新后调用。 */
    public static void trigger(int mugaStacks) {
        Minecraft mc = Minecraft.getInstance();
        if (mugaStacks <= 0 || mc.level == null || mc.player == null || mc.player.isDeadOrDying()) {
            clear();
            return;
        }
        FxTick.feed();
        ownerLevel = mc.level;
        ownerPlayer = mc.player;
        startTick = FxClock.now();
    }

    /** 供断线、重置与切世界时清理；渲染时也会校验世界和玩家身份。 */
    public static void clear() {
        startTick = Double.NaN;
        ownerLevel = null;
        ownerPlayer = null;
    }

    public static void render(GuiGraphics gfx, DeltaTracker tracker) {
        if (Double.isNaN(startTick)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level != ownerLevel
                || mc.player != ownerPlayer || mc.player.isDeadOrDying()) {
            clear();
            return;
        }

        FxTick.feed();
        double age = FxClock.now() - startTick;
        if (age < 0.0 || age >= DURATION_TICKS) {
            clear();
            return;
        }
        if (mc.options.hideGui || mc.screen != null) return;

        // 前一刻快速浮现，随后平滑衰减；只染边缘，视野中央保持透明。
        double envelope = age < 1.0 ? age : Math.pow((DURATION_TICKS - age) / (DURATION_TICKS - 1.0), 2.0);
        int width = gfx.guiWidth();
        int height = gfx.guiHeight();
        if (width < 2 || height < 2) return;
        int edge = Math.max(1, Math.min(36, Math.min(width, height) / 10));
        int bands = Math.min(8, edge);
        for (int band = 0; band < bands; band++) {
            double strength = 1.0 - band / (double) bands;
            int alpha = (int) (100.0 * envelope * strength * strength);
            if (alpha <= 0) continue;
            int outer = edge * band / bands;
            int inner = edge * (band + 1) / bands;
            int color = (alpha << 24) | EDGE_COLOR;
            // 四条不重叠的边框带，避免角落被重复混合而过亮。
            gfx.fill(outer, outer, width - outer, inner, color);
            gfx.fill(outer, height - inner, width - outer, height - outer, color);
            gfx.fill(outer, inner, inner, height - inner, color);
            gfx.fill(width - inner, inner, width - outer, height - inner, color);
        }
    }
}
