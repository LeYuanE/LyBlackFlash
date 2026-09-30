package com.leyuan.blackflash.client;

/**
 * 纯 Java 的客户端特效时钟（不引用 Minecraft 类型，便于离线单测）。
 *
 * <p><b>为什么不直接用 world.getGameTime()：</b>客户端的世界时间会被服务器的
 * 时间同步包直接覆盖。服务器卡顿（例如配置 20 TPS 但实际只跑到 10 TPS）时，
 * 同步值可能<b>小于</b>客户端已经走过的值，于是「现在 − 出生时刻」当场变小，
 * 正在播放的闪电会突然退回更早的动画阶段——表现就是闪一下、消失、再重新长出来。
 *
 * <p>这里只在同步值<b>前进</b>时采纳它（避免倒退），并把世界时间回退期间的高水位
 * 保留到同步值追上。动画的「年龄」按实际观测到的世界时间增量推进，因此暂停、
 * /tick freeze、低 TPS 拉伸等行为保持一致。
 */
final class FxClock {
    private FxClock() {}

    /** 允许的最大回退幅度：低于此值视为同步噪声，直接忽略。 */
    private static final double MAX_REWIND = 0.05;
    /** 单次同步允许的最大补正幅度（tick）：避免时间跳变时动画瞬移。 */
    private static final double MAX_CORRECTION = 5.0;
    /** 偏离超过该阈值后开始靠拢（tick）：约 10 秒。 */
    private static final double DRIFT_TOLERANCE = 200.0;

    private static double ticks;
    private static double lastWorldTicks;
    private static boolean started;

    /**
     * 以观测到的世界时间推进。世界时间没有前进时（暂停、/tick freeze）时钟也不前进；
     * 服务端同步把世界时间向后校正时，丢弃回退但重新基准化后续增量。
     */
    static void sample(double worldTicks) {
        if (!Double.isFinite(worldTicks)) return;
        if (!started) {
            ticks = worldTicks;
            lastWorldTicks = worldTicks;
            started = true;
            return;
        }
        double delta = worldTicks - lastWorldTicks;
        // 服务器回退时保留高水位，等待世界时间追上后再继续推进，避免动画倒退或重播。
        if (delta <= 0) return;
        lastWorldTicks = worldTicks;
        ticks += Math.min(delta, MAX_CORRECTION);
    }

    /** 保留给纯 Java 测试与离散状态机使用的推进入口。 */
    static void advance(double worldTicks, double delta, boolean monotonic) {
        if (!Double.isFinite(worldTicks) || !Double.isFinite(delta)) return;
        if (!started || !monotonic) {
            ticks = worldTicks;
            lastWorldTicks = worldTicks;
            started = true;
            return;
        }
        double previousWorldTicks = lastWorldTicks;
        sample(worldTicks);
        if (delta > 0 && worldTicks == previousWorldTicks) ticks += delta;
    }

    /** 世界时间相对本时钟是否明显倒退（同步倒流、切维度等）。 */
    static boolean isRewind(double worldTicks) {
        return started && Double.isFinite(worldTicks) && worldTicks + MAX_REWIND < ticks;
    }

    /** 当前特效时刻（tick）。所有动画年龄都必须以它为基准相减。 */
    static double now() {
        return ticks;
    }

    static void reset() {
        ticks = 0;
        lastWorldTicks = 0;
        started = false;
    }

    /**
     * 出生时刻：直接取「当前已采纳时钟」，再由调用方在每次推进时叠加帧插值。
     *
     * <p>这样新特效一定从 age = 0 起步：既不继承同步跳变之前的旧时刻，
     * 也不会因为用了「世界时间 + 本帧插值」而一出生就已经跑掉一帧的时长。
     */
    static double birth() {
        return ticks;
    }

    // ---------------------------------------------------------------------
    // 供 HUD / 镜头等客户端演出共用的游戏时间取用口。
    // ---------------------------------------------------------------------

    /**
     * 当前游戏时间（毫秒口径，单调、暂停时冻结）。
     *
     * <p>等价于「游戏刻 × 50 + 帧插值」，但走单调时钟，因此不受服务器时间同步
     * 倒流影响。所有客户端演出都应该用它，而不是 {@code System.currentTimeMillis()}
     * —— 后者在单人暂停、/tick freeze 或服务器卡顿时仍会继续走。
     */
    static long gameMillis() {
        return (long) (ticks * 50.0);
    }
}

