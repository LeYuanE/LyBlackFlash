package com.leyuan.blackflash.logic;

import com.leyuan.blackflash.BlackFlash;
import com.leyuan.blackflash.attachment.BlackFlashAttachments;
import com.leyuan.blackflash.config.BlackFlashConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 熟练度、无我境界、连击、当日均值的读写。
 *
 * <p>所有计时都用<b>游戏刻</b>（{@link Level#getGameTime()}）而不是现实墙钟：
 * 游戏刻随游戏推进，服务器暂停/卡顿时不会偷偷走完，
 * 与 /tick 冻结、单人暂停的行为一致。配置项仍以毫秒书写，这里换算。
 */
public final class GrowthManager {
    private GrowthManager() {}

    /** 每游戏刻的毫秒数（50ms/tick），用于把配置里的毫秒值换算成刻。 */
    private static final long MS_PER_TICK = 50L;

    /** 毫秒 → 刻，至少 1 刻，避免 0 长窗口导致连击永远断开。 */
    private static long toTicks(long millis) {
        return Math.max(1L, (millis + MS_PER_TICK - 1) / MS_PER_TICK);
    }

    private static long gameTime(ServerPlayer player) {
        return player.level().getGameTime();
    }

    /** 「差一点」反馈的节流表（仅服务端内存） */
    private static final Map<UUID, Long> NEAR_MISS_LAST = new HashMap<>();

    /** 当前无我层数；过期自动归零 */
    public static int mugaStacks(ServerPlayer player) {
        long until = player.getData(BlackFlashAttachments.MUGA_UNTIL);
        int stacks = player.getData(BlackFlashAttachments.MUGA_STACKS);
        if (stacks > 0 && gameTime(player) >= until) {
            player.setData(BlackFlashAttachments.MUGA_STACKS, 0);
            player.setData(BlackFlashAttachments.MUGA_UNTIL, 0L);
            return 0;
        }
        return stacks;
    }

    /** 当前连击；超出时间窗视为 0（不写回，写回在下次触发时体现） */
    public static int liveStreak(ServerPlayer player) {
        int streak = player.getData(BlackFlashAttachments.STREAK);
        if (streak <= 0) return 0;
        long at = player.getData(BlackFlashAttachments.STREAK_AT);
        long window = toTicks(BlackFlashConfig.CONFIG.streakWindowMs.get());
        return (gameTime(player) - at) <= window ? streak : 0;
    }

    /** 黑闪命中后的全部状态更新；返回本次结算值供成就触发器使用 */
    public static FlashResult onFlashLanded(ServerPlayer player) {
        long now = gameTime(player);
        BlackFlashConfig cfg = BlackFlashConfig.CONFIG;

        // 熟练度
        int count = player.getData(BlackFlashAttachments.COUNT) + 1;
        player.setData(BlackFlashAttachments.COUNT, count);

        // 连击（时间窗内累加，否则从 1 重新起）
        int streak = liveStreak(player) + 1;
        player.setData(BlackFlashAttachments.STREAK, streak);
        player.setData(BlackFlashAttachments.STREAK_AT, now);

        // 无我境界：层数 +1 并刷新计时
        int stacks = mugaStacks(player) + 1;
        int max = cfg.mugaMaxStacks.get();
        if (max > 0) stacks = Math.min(stacks, max);
        player.setData(BlackFlashAttachments.MUGA_STACKS, stacks);
        player.setData(BlackFlashAttachments.MUGA_UNTIL, now + toTicks(cfg.mugaDurationMs.get()));

        // 当日计数（按游戏日 24000 tick 滚动）
        long dayIndex = player.level().getDayTime() / 24000L;
        long savedDay = player.getData(BlackFlashAttachments.DAILY_DAY);
        int daily = dayIndex == savedDay ? player.getData(BlackFlashAttachments.DAILY_COUNT) + 1 : 1;
        player.setData(BlackFlashAttachments.DAILY_DAY, dayIndex);
        player.setData(BlackFlashAttachments.DAILY_COUNT, daily);

        // 强制标记消费
        player.setData(BlackFlashAttachments.FORCE, 0);

        boolean bareHand = player.getMainHandItem().isEmpty();
        return new FlashResult(count, streak, daily, bareHand);
    }

    /** 暴击命中但没触发：按配置清零连击 */
    public static void onCritMissed(ServerPlayer player) {
        if (BlackFlashConfig.CONFIG.streakBreakOnCritMiss.get()) {
            player.setData(BlackFlashAttachments.STREAK, 0);
        }
    }

    /** 「差一点」微反馈节流：允许返回 true 并记录时间 */
    public static boolean allowNearMiss(ServerPlayer player) {
        BlackFlashConfig cfg = BlackFlashConfig.CONFIG;
        if (!cfg.nearMissFeedback.get()) return false;
        long now = gameTime(player);
        Long last = NEAR_MISS_LAST.get(player.getUUID());
        if (last != null && now - last < toTicks(cfg.nearMissCooldownMs.get())) return false;
        NEAR_MISS_LAST.put(player.getUUID(), now);
        return true;
    }

    public static void forgetPlayer(UUID uuid) {
        NEAR_MISS_LAST.remove(uuid);
    }

    public record FlashResult(int count, int streak, int daily, boolean bareHand) {}
}
