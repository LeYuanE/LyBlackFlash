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
    private static final int MUGA_COLOR = 0xFF9DEBFF;
    private static final int COMBO_COLOR = 0xFFAE2524;
    private static final int EMPTY_COLOR = 0xFF241316;

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
                ? durationProgress(mugaRemaining, status.mugaDurationTicks()) : 0.0, MUGA_COLOR);
        drawSegment(gfx, x + MUGA_WIDTH, y, COMBO_WIDTH, combo
                ? durationProgress(comboRemaining, status.streakDurationTicks()) : 0.0, COMBO_COLOR);
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

    private static void drawSegment(GuiGraphics gfx, int x, int y, int width, double progress, int color) {
        gfx.fill(x, y, x + width, y + BAR_HEIGHT, EMPTY_COLOR);
        int filled = (int) Math.ceil(width * Math.clamp(progress, 0.0, 1.0));
        if (filled > 0) gfx.fill(x, y, x + filled, y + BAR_HEIGHT, color);
    }
}
