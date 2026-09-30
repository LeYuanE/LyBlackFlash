package com.leyuan.blackflash.client;

import com.leyuan.blackflash.logic.ChanceTable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** 只读熟练度面板：没有容器、按钮或客户端状态写入，也不会暂停世界。 */
public final class BlackFlashStatsScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 224;
    private static final int PADDING = 14;
    private static final int TEXT = 0xFFF0DDDA;
    private static final int MUTED = 0xFFB9A5A2;
    private static final int ACCENT = 0xFFE15A54;

    public BlackFlashStatsScreen() {
        super(Component.translatableWithFallback("screen.black_flash.stats.title", "黑闪 · 熟练度"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Screen 的默认实现会模糊并覆盖世界背景；这里保留完全透明的面板外区域。
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) return;
        FxTick.feed();
        super.render(gfx, mouseX, mouseY, partialTick);
        if (width <= 16 || height <= 16) return;

        // 小窗口与较大的 GUI 缩放也保持整块面板可见，不裁掉计时行或 ESC 提示。
        float scale = Math.min(1.0F, Math.min((width - 16.0F) / PANEL_WIDTH, (height - 16.0F) / PANEL_HEIGHT));
        float x = (width - PANEL_WIDTH * scale) / 2.0F;
        float y = (height - PANEL_HEIGHT * scale) / 2.0F;
        gfx.pose().pushPose();
        try {
            gfx.pose().translate(x, y, 0.0F);
            gfx.pose().scale(scale, scale, 1.0F);
            gfx.fill(0, 0, PANEL_WIDTH, PANEL_HEIGHT, 0xDC080303);
            gfx.renderOutline(0, 0, PANEL_WIDTH, PANEL_HEIGHT, 0xFFAE2524);
            gfx.drawCenteredString(font, title, PANEL_WIDTH / 2, 14, ACCENT);
            gfx.fill(PADDING, 31, PANEL_WIDTH - PADDING, 32, 0xFF632020);

            if (!BlackFlashClientState.hasStatus()) {
                gfx.drawCenteredString(font, text("syncing", "正在同步服务器数据…"), PANEL_WIDTH / 2, 101, MUTED);
            } else {
                renderStats(gfx);
            }
            gfx.fill(PADDING, 194, PANEL_WIDTH - PADDING, 195, 0xFF342020);
            gfx.drawCenteredString(font, text("close", "ESC 关闭 · 世界不会暂停"), PANEL_WIDTH / 2, 205, MUTED);
        } finally {
            gfx.pose().popPose();
        }
    }

    private void renderStats(GuiGraphics gfx) {
        var status = BlackFlashClientState.snapshot();
        int count = status.count();
        drawRow(gfx, text("count", "累计黑闪"), String.format(Locale.ROOT, "%,d", count), 44, TEXT);
        drawRow(gfx, text("tier", "段位"), ChanceTable.tier(count).getString(), 65, ACCENT);
        drawRow(gfx, text("base_chance", "基础概率"), chance(status.baseChance()), 86, TEXT);
        drawRow(gfx, text("effective_chance", "实际概率"), chance(status.effectiveChance()), 107, ACCENT);

        double streakTicks = BlackFlashClientState.streakRemainingTicks();
        boolean streakActive = status.streak() > 0 && streakTicks > 0.0;
        String streak = streakActive
                ? "x" + status.streak() + " · " + BlackFlashStatusRenderer.seconds(streakTicks)
                : text("inactive", "未激活");
        drawRow(gfx, text("combo", "连击"), streak, 128, streakActive ? TEXT : MUTED);

        double mugaTicks = BlackFlashClientState.mugaRemainingTicks();
        boolean mugaActive = status.mugaStacks() > 0 && mugaTicks > 0.0;
        String muga = mugaActive
                ? Component.translatableWithFallback("screen.black_flash.stats.muga_value", "%s 层 · %s · %s",
                        status.mugaStacks(), BlackFlashStatusRenderer.multiplier(status.mugaMultiplier()),
                        BlackFlashStatusRenderer.seconds(mugaTicks)).getString()
                : text("inactive", "未激活");
        drawRow(gfx, text("muga", "无我境界"), muga, 149, mugaActive ? TEXT : MUTED);
        drawRow(gfx, text("daily_count", "今日黑闪"), String.format(Locale.ROOT, "%,d", status.dailyCount()), 170, TEXT);
    }

    private void drawRow(GuiGraphics gfx, String label, String value, int y, int color) {
        gfx.drawString(font, label, PADDING, y, MUTED, false);
        int available = PANEL_WIDTH - PADDING * 2 - font.width(label) - 12;
        String visible = fit(value, Math.max(0, available));
        gfx.drawString(font, visible, PANEL_WIDTH - PADDING - font.width(visible), y, color, false);
    }

    private String fit(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String ellipsis = "…";
        if (font.width(ellipsis) > maxWidth) return "";
        return font.plainSubstrByWidth(value, maxWidth - font.width(ellipsis)) + ellipsis;
    }

    private static String chance(double value) {
        if (!Double.isFinite(value)) return "--";
        return String.format(Locale.ROOT, "%.2f%%", Math.max(0.0, Math.min(1.0, value)) * 100.0);
    }

    private static String text(String suffix, String fallback) {
        return Component.translatableWithFallback("screen.black_flash.stats." + suffix, fallback).getString();
    }

    // Screen 默认的 shouldCloseOnEsc/keyPressed/onClose 已提供 ESC 关闭行为。
}
