package com.leyuan.blackflash.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/** 饱食度上方的一行无文字状态条：左侧无我 2/3，右侧连击 1/3。 */
public final class BlackFlashStatusRenderer {
    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 9;
    private static final int MUGA_WIDTH = 54;
    private static final int COMBO_WIDTH = BAR_WIDTH - MUGA_WIDTH;
    private static final int MUGA_DARK = 0xFF28586C;
    private static final int MUGA_LIGHT = 0xFFC9F5FF;
    private static final int COMBO_DARK = 0xFF5A0D14;
    private static final int COMBO_LIGHT = 0xFFF06A5F;
    private static final int INK_EDGE = 0xFF5A0D14;
    private static final int EMPTY_COLOR = 0xFF160A0D;
    // 每一行的左右缺口，形成被撕开的墨迹边缘；全部在 81×9 外框内。
    private static final int[] LEFT_INSET = {3, 1, 0, 2, 1, 0, 2, 1, 3};
    private static final int[] RIGHT_INSET = {3, 0, 1, 2, 0, 1, 2, 1, 3};

    private BlackFlashStatusRenderer() {}

    /**
     * FOOD_LEVEL 上方的独立 GUI 层：原版饱食度固定占 81×9 GUI 像素，
     * 其上方一行的 y = guiHeight - 49；不使用文字，不改变原版右侧高度布局。
     */
    public static void render(GuiGraphics gfx, DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.isDeadOrDying()
                || mc.options.hideGui || mc.screen != null || !BlackFlashClientState.hasStatus()
                || !shouldShareFoodRow(mc)) return;

        var status = BlackFlashClientState.snapshot();
        double mugaRemaining = BlackFlashClientState.mugaRemainingTicks();
        double comboRemaining = BlackFlashClientState.streakRemainingTicks();
        boolean muga = status.mugaStacks() > 0 && mugaRemaining > 0.0;
        boolean combo = status.streak() > 0 && comboRemaining > 0.0;
        if (!muga && !combo) return;

        int x = gfx.guiWidth() / 2 + 10;
        int y = gfx.guiHeight() - 49;
        if (x < 0 || x + BAR_WIDTH > gfx.guiWidth() || y < 0) return;

        drawSegment(gfx, x, y, MUGA_WIDTH, muga
                ? durationProgress(mugaRemaining, status.mugaDurationTicks()) : 0.0,
                MUGA_DARK, MUGA_LIGHT);
        drawSegment(gfx, x + MUGA_WIDTH, y, COMBO_WIDTH, combo
                ? durationProgress(comboRemaining, status.streakDurationTicks()) : 0.0,
                COMBO_DARK, COMBO_LIGHT);
    }

    private static boolean shouldShareFoodRow(Minecraft mc) {
        if (!(mc.cameraEntity instanceof Player)) return false;
        if (!mc.gameMode.canHurtPlayer()) return false;
        // 载具生命条会占用右侧 HUD 空间；v1 状态条仅在玩家自己的饱食度行可见时绘制，
        // 避免在马/船等载具状态下与原版布局重叠。
        return mc.player.getVehicle() == null;
    }

    private static double durationProgress(double remaining, long duration) {
        if (duration <= 0) return 0.0;
        return Math.clamp(remaining / duration, 0.0, 1.0);
    }

    private static void drawSegment(GuiGraphics gfx, int x, int y, int width, double progress,
                                    int darkColor, int lightColor) {
        // 先铺墨色底槽，再逐行按缺口裁剪。不会向轮廓外绘制阴影或高光。
        int filled = (int) Math.ceil((width - 2) * Math.clamp(progress, 0.0, 1.0));
        for (int row = 0; row < BAR_HEIGHT; row++) {
            int left = x + LEFT_INSET[row];
            int right = x + width - RIGHT_INSET[row];
            gfx.fill(left, y + row, right, y + row + 1, INK_EDGE);
            int innerLeft = left + 1;
            int innerRight = right - 1;
            if (innerRight <= innerLeft) continue;
            gfx.fill(innerLeft, y + row, innerRight, y + row + 1, EMPTY_COLOR);
            if (filled <= 0) continue;

            int fillEnd = Math.min(innerRight, innerLeft + filled);
            // 暗色根部固定占填充的前 1/3，其余为亮色。用比例而不是整数除法，
            // 否则低进度时 filled/3 取整会把亮色段压成 0，整条退化成纯暗色。
            int span = fillEnd - innerLeft;
            int split = span <= 1 ? 0 : Math.max(1, (int) Math.round(span / 3.0));
            if (split > 0) {
                gfx.fill(innerLeft, y + row, innerLeft + split, y + row + 1, darkColor);
            }
            if (span > split) {
                gfx.fill(innerLeft + split, y + row, fillEnd, y + row + 1, lightColor);
            }
            // 仅在填充内部放一条 1px 高光，不越过 jagged 轮廓。
            if (row == 1 && fillEnd > innerLeft) {
                gfx.fill(innerLeft, y + row, fillEnd, y + row + 1, lighten(lightColor));
            }
        }
        // 分界只由左右颜色自然相遇，不额外画竖线。
    }

    private static int lighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 32);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 32);
        int b = Math.min(255, (color & 0xFF) + 32);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
