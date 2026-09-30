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
 * 与 /tick 冻结、单人暂停的行为一致。
 */
public final class GrowthManager {
    private GrowthManager() {}

    /**
     * 连击时间窗：两次黑闪必须在这个时间内衔接才累加。
     *
     * <p>刻意<b>不做成配置项</b>：它是决定连击手感的核心参数，不是玩家偏好。
     * 放进配置里会有两个问题——调平衡时已装玩家的旧值不会跟随更新；
     * 而这个数值又直接决定伤害指数（{@code ^2.5^n}）能堆到多高，不宜由玩家单方面放宽。
     */
    public static final long STREAK_WINDOW_MS = 5_000L;

    /**
     * 无我境界持续时长（每次触发刷新计时）。
     *
     * <p>与 {@link #STREAK_WINDOW_MS} 同理，不做成配置项。
     */
    public static final long MUGA_DURATION_MS = 10_000L;

    /** 每游戏刻的毫秒数（50ms/tick）。 */
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
        long window = toTicks(STREAK_WINDOW_MS);
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
        player.setData(BlackFlashAttachments.MUGA_UNTIL, now + toTicks(MUGA_DURATION_MS));

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
