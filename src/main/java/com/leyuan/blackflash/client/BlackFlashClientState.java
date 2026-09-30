package com.leyuan.blackflash.client;

import com.leyuan.blackflash.network.BlackFlashStatusPayload;
import com.leyuan.blackflash.status.BlackFlashStatus;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 客户端本人的服务端权威状态缓存；只在客户端主线程读写，不读取玩家附件。
 * 生命周期与本人/维度校验由 {@link ClientFx} 负责，命中/预览包不改写此快照。
 */
public final class BlackFlashClientState {
    private BlackFlashClientState() {}

    private static final BlackFlashStatus EMPTY = new BlackFlashStatus(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 1.0);

    private static BlackFlashStatus status = EMPTY;
    private static UUID playerId;
    private static ResourceLocation dimension;
    private static double receivedAtTicks;
    private static boolean hasStatus;

    public static void reset() {
        status = EMPTY;
        playerId = null;
        dimension = null;
        receivedAtTicks = 0.0;
        hasStatus = false;
    }

    /**
     * 由 ClientFx 校验上下文并采样 FxClock 后调用；返回是否接纳了此快照。
     * 同一服务端 tick 可能发生多次命中或管理命令，所以仅拒绝严格更旧的快照。
     * resetVisuals 只是演出重置标记，不能绕过旧包校验。
     */
    public static boolean apply(BlackFlashStatusPayload payload) {
        if (hasStatus && (!playerId.equals(payload.playerId())
                || !dimension.equals(payload.dimension())
                || payload.status().serverGameTime() < status.serverGameTime())) {
            return false;
        }
        status = payload.status();
        playerId = payload.playerId();
        dimension = payload.dimension();
        receivedAtTicks = FxClock.now();
        hasStatus = true;
        return true;
    }

    /** 尚未收到当前世界的本人快照时，HUD 不应把 EMPTY 当成真实零值状态。 */
    public static boolean hasStatus() {
        return hasStatus;
    }

    /** 原始权威字段（包括收到时的剩余 tick）；倒计时请用下方剩余时间方法。 */
    public static BlackFlashStatus snapshot() {
        return status;
    }

    public static int count() { return status.count(); }
    public static int dailyCount() { return status.dailyCount(); }
    public static int streak() { return status.streak(); }
    public static int mugaStacks() { return status.mugaStacks(); }
    public static double baseChance() { return status.baseChance(); }
    public static double effectiveChance() { return status.effectiveChance(); }
    public static double mugaMultiplier() { return status.mugaMultiplier(); }

    public static double streakProgress() {
        return status.streakDurationTicks() <= 0 ? 0.0
                : streakRemainingTicks() / (double) status.streakDurationTicks();
    }

    public static double mugaProgress() {
        return status.mugaDurationTicks() <= 0 ? 0.0
                : mugaRemainingTicks() / (double) status.mugaDurationTicks();
    }

    /** 收到快照时的 FxClock 游戏 tick，不是墙钟或服务端世界时间。 */
    public static double receivedAtTicks() {
        return receivedAtTicks;
    }

    public static double streakRemainingTicks() {
        return remainingTicks(status.streakRemainingTicks());
    }

    public static double mugaRemainingTicks() {
        return remainingTicks(status.mugaRemainingTicks());
    }

    private static double remainingTicks(long receivedRemainingTicks) {
        if (!hasStatus) return 0.0;
        double elapsedTicks = Math.max(0.0, FxClock.now() - receivedAtTicks);
        return Math.max(0.0, receivedRemainingTicks - elapsedTicks);
    }
}
