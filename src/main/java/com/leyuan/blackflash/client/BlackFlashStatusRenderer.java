package com.leyuan.blackflash.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** 本人的持续状态 HUD；只读服务端同步后的客户端状态，不推算游戏概率。 */
public final class BlackFlashStatusRenderer {
    private static final int PANEL_WIDTH = 208;
    private static final int MARGIN = 8;
    private static final int PADDING = 7;
    private static final int ROW_HEIGHT = 26;
    private static final int HOTBAR_CLEARANCE = 64;
    private static final int BORDER = 0xFFAE2524;
    private static final int TEXT = 0xFFF0DDDA;
    private static final int MUTED = 0xFFB9A5A2;

    private BlackFlashStatusRenderer() {}

    public static void render(GuiGraphics gfx, DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.isDeadOrDying()) return;
        FxTick.feed();
        if (mc.options.hideGui || mc.screen != null || !BlackFlashClientState.hasStatus()) return;

        var status = BlackFlashClientState.snapshot();
        double streakTicks = BlackFlashClientState.streakRemainingTicks();
        double mugaTicks = BlackFlashClientState.mugaRemainingTicks();
        boolean streakActive = status.streak() > 0 && streakTicks > 0.0;
        boolean mugaActive = status.mugaStacks() > 0 && mugaTicks > 0.0;
        int rows = (streakActive ? 1 : 0) + (mugaActive ? 1 : 0);
        if (rows == 0) return;

        int width = Math.min(PANEL_WIDTH, gfx.guiWidth() - MARGIN * 2);
        int height = PADDING * 2 + ROW_HEIGHT * rows;
        if (width <= PADDING * 2 || gfx.guiHeight() < height + HOTBAR_CLEARANCE + MARGIN) return;
        int x = MARGIN;
        int y = gfx.guiHeight() - HOTBAR_CLEARANCE - height;
        gfx.fill(x, y, x + width, y + height, 0xC8080303);
        gfx.renderOutline(x, y, width, height, BORDER);

        int rowY = y + PADDING;
        int contentWidth = width - PADDING * 2;
        if (streakActive) {
            String label = Component.translatableWithFallback("hud.black_flash.combo", "连击 x%s",
                    status.streak()).getString();
            double progress = status.streakDurationTicks() > 0 ? streakTicks / status.streakDurationTicks() : 0.0;
            drawStatus(gfx, mc.font, label, streakTicks, progress,
                    x + PADDING, rowY, contentWidth, 0xFFAE2524);
            rowY += ROW_HEIGHT;
        }
        if (mugaActive) {
            String label = Component.translatableWithFallback("hud.black_flash.muga", "无我 %s 层 · %s",
                    status.mugaStacks(), multiplier(status.mugaMultiplier())).getString();
            double progress = status.mugaDurationTicks() > 0 ? mugaTicks / status.mugaDurationTicks() : 0.0;
            drawStatus(gfx, mc.font, label, mugaTicks, progress,
                    x + PADDING, rowY, contentWidth, 0xFFE15A54);
        }
    }

    private static void drawStatus(GuiGraphics gfx, Font font, String label, double remainingTicks,
                                   double progress, int x, int y, int width, int color) {
        gfx.drawString(font, font.plainSubstrByWidth(label, width), x, y, TEXT, false);
        String time = seconds(remainingTicks);
        int timeWidth = font.width(time);
        int barWidth = Math.max(0, width - timeWidth - 7);
        int barY = y + font.lineHeight + 5;
        gfx.fill(x, barY, x + barWidth, barY + 3, 0xFF342020);
        double fraction = Double.isFinite(progress) ? Math.max(0.0, Math.min(1.0, progress)) : 0.0;
        int filled = (int) Math.ceil(barWidth * fraction);
        if (filled > 0) gfx.fill(x, barY, x + filled, barY + 3, color);
        gfx.drawString(font, time, x + width - timeWidth, y + font.lineHeight + 1, MUTED, false);
    }

    /** 向上取整到十分之一秒，避免仍有效的最后一帧提前显示 0.0s。 */
    static String seconds(double ticks) {
        if (!Double.isFinite(ticks)) return "--";
        return String.format(Locale.ROOT, "%.1fs", Math.ceil(Math.max(0.0, ticks) / 2.0) / 10.0);
    }

    /** 显示同步的总倍率，而不是客户端本地配置的单层倍率。 */
    static String multiplier(double value) {
        if (Double.isNaN(value) || value < 0.0) return "--";
        if (Double.isInfinite(value)) return "x∞";
        return String.format(Locale.ROOT, value >= 10000.0 ? "x%.2e" : "x%.2f", value);
    }
}
