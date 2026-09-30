package com.leyuan.blackflash.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 不依赖渲染状态的种子几何；坐标以命中点为原点，reveal 表示蔓延前沿到达的进度。 */
final class FlashGeometry {
    private FlashGeometry() {}

    record Point(double x, double y, double z, double halfWidth, double reveal) {}
    record Bolt(List<Point> points, float energy) {
        Bolt {
            points = List.copyOf(points);
        }
    }

    record Timing(int startMs, int spreadMs, int fadeMs) {
        Timing {
            startMs = Math.clamp(startMs, 1, 10_000);
            spreadMs = Math.clamp(spreadMs, 1, 10_000);
            fadeMs = Math.clamp(fadeMs, 1, 10_000);
        }

        int lifetimeMs() {
            return startMs + spreadMs + fadeMs;
        }

        double progress(double ageMs) {
            if (ageMs <= 0) return 0;
            if (ageMs < startMs) {
                double t = ageMs / startMs;
                return 0.34 * (1 - Math.pow(1 - t, 3));
            }
            double t = clamp((ageMs - startMs) / spreadMs);
            return 0.34 + 0.66 * (0.6 * t + 0.4 * smooth(t));
        }

        double alpha(double ageMs, double reveal) {
            if (ageMs < 0 || ageMs >= lifetimeMs()) return 0;
            double fadeStart = startMs + spreadMs + clamp(reveal) * fadeMs * 0.58;
            return 1 - smooth(clamp((ageMs - fadeStart) / (fadeMs * 0.42)));
        }

        double widthScale(double ageMs) {
            return 1.28 - 0.28 * clamp(ageMs / startMs);
        }
    }

    private record V(double x, double y, double z) {
        V add(V b) { return new V(x + b.x, y + b.y, z + b.z); }
        V scale(double s) { return new V(x * s, y * s, z * s); }
        V cross(V b) { return new V(y * b.z - z * b.y, z * b.x - x * b.z, x * b.y - y * b.x); }
        double length() { return Math.sqrt(x * x + y * y + z * z); }
        V normal() {
            double length = length();
            return length < 1.0e-8 ? new V(0, 0, 1) : scale(1 / length);
        }
    }

    static List<Bolt> create(long seed, double fistX, double fistY, double fistZ) {
        Random random = new Random(seed);
        V origin = new V(0, 0, 0);
        V fist = new V(fistX, fistY, fistZ);
        if (!Double.isFinite(fist.length()) || fist.length() > 4) {
            fist = origin;
        }
        V forward = fist.scale(-1).normal();
        V right = perpendicular(forward);
        V up = forward.cross(right).normal();
        List<Bolt> bolts = new ArrayList<>();
        double rotation = random.nextDouble() * Math.PI * 2;

        // 命中面上的放射扇形保留纵深，正面能读出轮廓，侧面仍然是世界空间闪电。
        for (int i = 0; i < 9; i++) {
            double angle = rotation + i * Math.PI * 2 / 9 + signed(random) * 0.22;
            V radial = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            V direction = radial.add(forward.scale(signed(random) * 0.48)).normal();
            double length = 1.85 + random.nextDouble() * 1.25;
            double width = 0.095 + random.nextDouble() * 0.075;
            double delay = i == 0 ? 0 : random.nextDouble() * 0.09;
            Bolt main = path(random, origin, direction, length, width, 9,
                    delay, 0.91 + random.nextDouble() * 0.09, 0.78f + random.nextFloat() * 0.22f);
            bolts.add(main);

            if (i % 2 == 0) {
                Point root = main.points().get(3 + random.nextInt(3));
                V forkDirection = direction.scale(0.65)
                        .add(right.scale(signed(random) * 0.9))
                        .add(up.scale(signed(random) * 0.9)).normal();
                bolts.add(path(random, new V(root.x(), root.y(), root.z()), forkDirection,
                        0.65 + random.nextDouble() * 0.65, root.halfWidth() * 0.55, 5,
                        root.reveal(), Math.min(1, root.reveal() + 0.3), 0.76f));
            }
        }

        if (fist.length() > 0.12) {
            List<Point> bridge = new ArrayList<>();
            for (int i = 0; i <= 6; i++) {
                double t = i / 6.0;
                double jitter = Math.sin(t * Math.PI) * 0.17;
                V p = fist.scale(1 - t).add(right.scale(signed(random) * jitter))
                        .add(up.scale(signed(random) * jitter));
                bridge.add(new Point(p.x(), p.y(), p.z(), (0.035 + 0.065 * t) * Math.pow(1 - t, 0.35), t * 0.28));
            }
            bolts.add(new Bolt(bridge, 1.0f));
            for (int i = 0; i < 4; i++) {
                double angle = rotation + i * Math.PI * 0.5;
                V direction = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)))
                        .add(forward.scale(0.45)).normal();
                bolts.add(path(random, fist, direction, 0.5 + random.nextDouble() * 0.55,
                        0.04 + random.nextDouble() * 0.025, 5,
                        random.nextDouble() * 0.04, 0.45 + random.nextDouble() * 0.18, 0.85f));
            }
        }
        return List.copyOf(bolts);
    }

    private static Bolt path(Random random, V origin, V direction, double length, double width,
                             int segments, double revealStart, double revealEnd, float energy) {
        V side = perpendicular(direction);
        V up = direction.cross(side).normal();
        List<Point> points = new ArrayList<>(segments + 1);
        double bend = signed(random) * length * 0.2;
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double envelope = Math.sin(Math.PI * t);
            double zigzag = i == 0 || i == segments ? 0
                    : (i % 2 == 0 ? 1 : -1) * (0.055 + random.nextDouble() * 0.15);
            V p = origin.add(direction.scale(length * t))
                    .add(side.scale(bend * t * t + zigzag * envelope))
                    .add(up.scale(signed(random) * 0.13 * envelope));
            double taper = Math.pow(1 - t, 0.72);
            double thickness = width * taper * (i == 0 ? 1 : 0.65 + random.nextDouble() * 0.65);
            points.add(new Point(p.x(), p.y(), p.z(), thickness,
                    revealStart + (revealEnd - revealStart) * Math.pow(t, 1.08)));
        }
        return new Bolt(points, energy);
    }

    private static V perpendicular(V direction) {
        V axis = Math.abs(direction.y()) < 0.9 ? new V(0, 1, 0) : new V(1, 0, 0);
        return axis.cross(direction).normal();
    }

    private static double signed(Random random) {
        return random.nextDouble() * 2 - 1;
    }

    private static double clamp(double value) {
        return Math.clamp(value, 0, 1);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }
}
