package com.leyuan.blackflash.client;

import java.util.List;

/** 无第三方测试依赖，可随离线 check 执行。 */
public final class FlashGeometryChecks {
    private static int checks;

    public static void main(String[] args) {
        deterministicGeometry();
        geometryBounds();
        fallbackOrigins();
        animationTimeline();
        invalidTimings();
        fxClock();
        System.out.println("Flash geometry checks passed: " + checks);
    }

    private static void deterministicGeometry() {
        var first = FlashGeometry.create(1234, 0.2, 0.15, -1.3);
        require(first.equals(FlashGeometry.create(1234, 0.2, 0.15, -1.3)), "Same seed changed geometry");
        require(!first.equals(FlashGeometry.create(4321, 0.2, 0.15, -1.3)), "Different seeds reused geometry");
        try {
            first.clear();
            throw new AssertionError("Bolt list must be immutable");
        } catch (UnsupportedOperationException expected) {
            checks++;
        }
        try {
            first.getFirst().points().clear();
            throw new AssertionError("Point list must be immutable");
        } catch (UnsupportedOperationException expected) {
            checks++;
        }
    }

    private static void geometryBounds() {
        for (long seed = 0; seed < 512; seed++) {
            var bolts = FlashGeometry.create(seed, 0.3, 0.1, -1.5);
            require(bolts.size() == 19, "Expected nine main bolts, five forks, bridge and four fist bolts");
            int segments = 0;
            for (var bolt : bolts) {
                require(bolt.energy() > 0 && bolt.energy() <= 1, "Invalid bolt energy");
                require(bolt.points().getLast().halfWidth() == 0, "Every bolt, including bridge, needs a pointed tip");
                segments += bolt.points().size() - 1;
                FlashGeometry.Point previous = null;
                for (var p : bolt.points()) {
                    require(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()), "Non-finite point");
                    require(p.x() * p.x() + p.y() * p.y() + p.z() * p.z() < 36, "Bolt escaped local effect bounds");
                    require(p.halfWidth() >= 0 && p.halfWidth() < 0.25, "Invalid ribbon width");
                    require(p.reveal() >= 0 && p.reveal() <= 1, "Invalid reveal progress");
                    if (previous != null) {
                        require(p.reveal() > previous.reveal(), "Reveal order must be strictly increasing");
                        double dx = p.x() - previous.x(), dy = p.y() - previous.y(), dz = p.z() - previous.z();
                        require(dx * dx + dy * dy + dz * dz > 1.0e-10, "Degenerate segment");
                    }
                    previous = p;
                }
            }
            require(segments == 132, "Geometry budget changed");
            for (int i : new int[]{1, 4, 7, 10, 13}) {
                var forkRoot = bolts.get(i).points().getFirst();
                boolean attached = bolts.get(i - 1).points().stream().anyMatch(p ->
                        p.x() == forkRoot.x() && p.y() == forkRoot.y() && p.z() == forkRoot.z()
                                && p.reveal() == forkRoot.reveal());
                require(attached, "Fork appeared before or away from its parent");
                require(bolts.get(i).points().getLast().halfWidth() == 0, "Fork tip must taper to zero");
            }
        }
    }

    private static void fallbackOrigins() {
        for (double x : new double[]{0, 50, Double.NaN, Double.POSITIVE_INFINITY}) {
            List<FlashGeometry.Bolt> bolts = FlashGeometry.create(17, x, 0, 0);
            require(bolts.size() == 14, "Invalid fist origin should omit fist geometry");
            for (var bolt : bolts) {
                for (var p : bolt.points()) {
                    require(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()), "Invalid fallback point");
                }
            }
        }
        require(FlashGeometry.create(0, 0, 1.5, 0).size() == 19, "Vertical attack basis failed");
    }

    private static void animationTimeline() {
        var timing = new FlashGeometry.Timing(100, 1000, 600);
        require(timing.lifetimeMs() == 1700, "Config lifetime changed");
        require(timing.progress(-1) == 0 && timing.progress(0) == 0, "Effect starts before birth");
        require(Math.abs(timing.progress(100) - 0.34) < 1.0e-9, "Burst phase endpoint changed");
        require(timing.progress(1100) == 1, "Spread must finish before fade");
        double previous = -1;
        for (double age = 0; age <= 1700; age += 0.5) {
            double progress = timing.progress(age);
            require(progress >= previous && progress >= 0 && progress <= 1, "Reveal is not monotonic");
            require(progress - Math.max(0, previous) < 0.02, "Reveal jumped between segments");
            previous = progress;
            double rootAlpha = timing.alpha(age, 0);
            double tipAlpha = timing.alpha(age, 1);
            require(rootAlpha >= 0 && rootAlpha <= 1 && tipAlpha >= rootAlpha && tipAlpha <= 1, "Fade must move root to tip");
        }
        require(timing.alpha(1400, 0) == 0, "Root should disappear before the tip");
        require(timing.alpha(1400, 1) == 1, "Tip should linger after the root");
        for (int i = 0; i <= 100; i++) {
            double reveal = i / 100.0;
            require(timing.alpha(1699.9, reveal) < 0.000001, "Cleanup would truncate visible geometry");
            require(timing.alpha(1700, reveal) == 0, "Expired geometry still visible");
        }
        require(timing.widthScale(0) > timing.widthScale(100), "Initial burst must be stronger than spread");
    }

    private static void invalidTimings() {
        for (int value : new int[]{Integer.MIN_VALUE, -1, 0, 1, 100, Integer.MAX_VALUE}) {
            var timing = new FlashGeometry.Timing(value, value, value);
            require(timing.lifetimeMs() > 0 && timing.lifetimeMs() <= 30_000, "Unbounded lifetime");
            for (double age : new double[]{0, 0.5, 1, 100, timing.lifetimeMs() - 0.001, timing.lifetimeMs()}) {
                require(Double.isFinite(timing.progress(age)), "Invalid animation progress");
                require(Double.isFinite(timing.alpha(age, 0.5)), "Invalid animation alpha");
            }
            require(timing.alpha(timing.lifetimeMs(), 1) == 0, "Clamped timing still cuts off a bolt");
        }
    }

    /**
     * 特效时钟：核心保证是「年龄永不减少」。世界时间会被服务器同步覆盖，
     * 卡顿时同步值可能倒退，用世界时间直接相减会让正在播放的闪电重播。
     */
    private static void fxClock() {
        var timing = new FlashGeometry.Timing(100, 1000, 600);
        FxClock.reset();
        FxClock.advance(1000, 0, true);
        double birth = FxClock.birth();
        require(FxClock.birth() == FxClock.now(), "Birth must equal the current FX tick");

        // 正常推进：年龄随帧插值增长。
        double previousAge = 0;
        for (int frame = 0; frame < 400; frame++) {
            FxClock.advance(1000 + frame * 0.05, 0.05, true);
            double age = (FxClock.now() - birth) * 50;
            require(age >= previousAge, "Age must never decrease while time advances");
            previousAge = age;
        }

        // 服务器卡顿后同步倒流：世界时间回退 20 tick，动画不得倒退或重播。
        double beforeRewind = FxClock.now();
        double rewoundWorld = 1000 + 20 - 20;
        for (int i = 0; i < 40; i++) {
            FxClock.advance(rewoundWorld, 0.05, true);
            rewoundWorld += 0.05;
        }
        double afterRewind = FxClock.now();
        require(afterRewind == beforeRewind, "Time sync rewind restarted the animation");
        // 世界时间重新追上旧基准后，后续正向样本才继续推进。
        for (int i = 0; i < 50; i++) {
            FxClock.sample(1020.0 + i * 0.05);
        }
        double afterCatchup = FxClock.now();
        require(afterCatchup >= afterRewind, "Clock did not resume after sync catch-up");

        // 小偏差按每帧上限平滑收敛，不会让动画瞬移。
        FxClock.reset();
        FxClock.advance(1000, 0, true);
        FxClock.advance(1010, 0.05, true);
        double justBeforeCatchup = FxClock.now();
        FxClock.advance(1016, 0.05, true);
        double step = FxClock.now() - justBeforeCatchup;
        require(step > 0 && step <= 5.05, "Small drift should converge gradually, not teleport");

        // 大幅跳变（重登/跨维度/时间同步）直接重基准：接受新时间，
        // 但绝不把跳变时长算进动画年龄，否则正在播放的演出会被瞬间快进完。
        FxClock.reset();
        FxClock.advance(1000, 0, true);
        double beforeJump = FxClock.now();
        FxClock.advance(9000, 0.05, true);
        require(FxClock.now() == 9000, "Large jump should rebase onto the new world time");
        require(FxClock.now() - beforeJump > 5.05,
                "Large jump must NOT be subject to the gradual catch-up cap");

        // 切世界/重置后从当前世界时间重新起步，不继承旧时刻。
        FxClock.reset();
        require(FxClock.now() == 0, "Reset must clear the clock");
        FxClock.advance(900, 0, true);
        require(FxClock.now() == 900, "First sample after reset adopts world time");
        require(!FxClock.isRewind(1000), "World time running ahead is not a rewind");
        require(!FxClock.isRewind(900), "Equal world time is not a rewind");
        require(!FxClock.isRewind(899.97), "Sub-threshold jitter is not a rewind");
        require(FxClock.isRewind(890), "A large step back is treated as a rewind");
        require(!FxClock.isRewind(Double.NaN), "Non-finite world time is not a rewind");

        // 非有限输入不得污染时钟。
        double stable = FxClock.now();
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            FxClock.advance(bad, 0.05, true);
            require(FxClock.now() == stable, "Non-finite world time must be ignored");
        }
        FxClock.reset();
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
