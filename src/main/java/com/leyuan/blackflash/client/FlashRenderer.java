package com.leyuan.blackflash.client;

import com.leyuan.blackflash.BlackFlash;
import com.leyuan.blackflash.config.BlackFlashConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** 世界层黑闪：缓存折线、连续锥形笔触，红色晕光 / 红边 / 黑心分别整批提交。 */
@EventBusSubscriber(modid = BlackFlash.MOD_ID, value = Dist.CLIENT)
public final class FlashRenderer {
    private FlashRenderer() {}

    private static final int MAX_FLASHES = 24;
    private static final double MAX_DISTANCE_SQUARED = 64 * 64;
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static ClientLevel activeLevel;

    private static final class RenderTypes {
        static final RenderType GLOW = create("black_flash_glow", true);
        static final RenderType EDGE = create("black_flash_edge", false);
        static final RenderType CORE = create("black_flash_core", false);

        /**
         * 三层都输出到 <b>主目标</b>，而不是 {@code PARTICLES_TARGET}。
         *
         * <p>原因：粒子目标在 Fabulous 下会被清空并拷入不透明深度，而我们不做深度写入，
         * 于是闪电在粒子缓冲里的深度是「背后墙面的深度」。后续
         * {@code transparency.fsh} 用这份深度做远近排序，会把位于闪电之后的玻璃、水
         * 合成到闪电之前 —— 前景的黑闪被身后的半透明物体盖住。
         *
         * <p>留在主目标上，深度测试直接对真实场景深度生效：遮挡关系始终正确，也不参与
         * 粒子层的排序。代价是不走 Fabulous 的透明合成，属于可接受的取舍。
         */
        private static RenderType create(String name, boolean additive) {
            return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                    8192, false, false, RenderType.CompositeState.builder()
                            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                            .setTransparencyState(additive ? RenderStateShard.LIGHTNING_TRANSPARENCY
                                    : RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setCullState(RenderStateShard.NO_CULL)
                            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                            .createCompositeState(false));
        }
    }

    private static final class Flash {
        final Vec3 hit;
        final double birthTick;
        final FlashGeometry.Timing timing;
        final List<Ribbon> ribbons;
        final AABB bounds;
        boolean visible;

        Flash(Vec3 hit, Vec3 fist, long seed, double birthTick, FlashGeometry.Timing timing) {
            this.hit = hit;
            this.birthTick = birthTick;
            this.timing = timing;
            Vec3 offset = fist.subtract(hit);
            ribbons = FlashGeometry.create(seed, offset.x, offset.y, offset.z).stream()
                    .map(Ribbon::new).toList();
            bounds = new AABB(hit, hit).inflate(6);
        }

        double age(double nowTick) {
            return Math.max(0, (nowTick - birthTick) * 50);
        }
    }

    private static final class Ribbon {
        final FlashGeometry.Bolt bolt;
        final double[] x, y, z, sideX, sideY, sideZ, width, alpha;
        int count;

        Ribbon(FlashGeometry.Bolt bolt) {
            this.bolt = bolt;
            int size = bolt.points().size();
            x = new double[size]; y = new double[size]; z = new double[size];
            sideX = new double[size]; sideY = new double[size]; sideZ = new double[size];
            width = new double[size]; alpha = new double[size];
        }

        void prepare(Vec3 origin, Vec3 camera, FlashGeometry.Timing timing, double age, double progress) {
            count = 0;
            var points = bolt.points();
            if (progress <= points.getFirst().reveal()) return;
            double ox = origin.x - camera.x, oy = origin.y - camera.y, oz = origin.z - camera.z;
            double scale = timing.widthScale(age);
            for (int i = 0; i < points.size(); i++) {
                var point = points.get(i);
                if (point.reveal() <= progress) {
                    double tipWidth = i == points.size() - 1 ? 1 : Math.clamp(
                            (progress - point.reveal()) / (points.get(i + 1).reveal() - point.reveal()), 0, 1);
                    set(i, point.x() + ox, point.y() + oy, point.z() + oz,
                            point.halfWidth() * scale * tipWidth, timing.alpha(age, point.reveal()));
                } else {
                    var previous = points.get(i - 1);
                    double t = (progress - previous.reveal()) / (point.reveal() - previous.reveal());
                    if (t > 1.0e-6) {
                        set(i, previous.x() + (point.x() - previous.x()) * t + ox,
                                previous.y() + (point.y() - previous.y()) * t + oy,
                                previous.z() + (point.z() - previous.z()) * t + oz,
                                0, timing.alpha(age, progress));
                    }
                    break;
                }
            }
            if (count < 2) return;
            if (progress < points.getLast().reveal()) width[count - 1] = 0;

            // 相邻段共用接缝顶点；三种材质复用同一帧的相机朝向与截断结果。
            for (int i = 0; i < count; i++) {
                int before = Math.max(0, i - 1), after = Math.min(count - 1, i + 1);
                double dx = x[after] - x[before], dy = y[after] - y[before], dz = z[after] - z[before];
                double sx = dz * y[i] - dy * z[i];
                double sy = dx * z[i] - dz * x[i];
                double sz = dy * x[i] - dx * y[i];
                double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (length < 1.0e-8) {
                    if (Math.abs(dy) < Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.9) {
                        sx = -dz; sy = 0; sz = dx;
                    } else {
                        sx = 0; sy = dz; sz = -dy;
                    }
                    length = Math.sqrt(sx * sx + sy * sy + sz * sz);
                }
                if (length < 1.0e-8) { sx = 1; sy = 0; sz = 0; length = 1; }
                if (i > 0 && sx * sideX[i - 1] + sy * sideY[i - 1] + sz * sideZ[i - 1] < 0) {
                    length = -length;
                }
                sideX[i] = sx / length;
                sideY[i] = sy / length;
                sideZ[i] = sz / length;
            }
        }

        private void set(int i, double px, double py, double pz, double halfWidth, double opacity) {
            x[i] = px; y[i] = py; z[i] = pz;
            width[i] = halfWidth; alpha[i] = opacity;
            count = i + 1;
        }

        void draw(VertexConsumer consumer, Matrix4f pose, double size, int red, int green, int blue,
                  double opacity, boolean glow) {
            for (int i = 1; i < count; i++) {
                if (Math.max(alpha[i - 1], alpha[i]) * opacity < 1.0 / 255) continue;
                if (glow) {
                    // 从细红边到透明外沿，避免宽实心红带吞掉黑色笔触。
                    quad(consumer, pose, i, 1.08, size, 0.18 * opacity, 0, red, green, blue);
                    quad(consumer, pose, i, -size, -1.08, 0, 0.18 * opacity, red, green, blue);
                } else {
                    quad(consumer, pose, i, -size, size, opacity, opacity, red, green, blue);
                }
            }
        }

        private void quad(VertexConsumer consumer, Matrix4f pose, int i, double left, double right,
                          double leftAlpha, double rightAlpha, int red, int green, int blue) {
            vertex(consumer, pose, i - 1, left, leftAlpha, red, green, blue);
            vertex(consumer, pose, i - 1, right, rightAlpha, red, green, blue);
            vertex(consumer, pose, i, right, rightAlpha, red, green, blue);
            vertex(consumer, pose, i, left, leftAlpha, red, green, blue);
        }

        private void vertex(VertexConsumer consumer, Matrix4f pose, int i, double side, double opacity,
                            int red, int green, int blue) {
            double offset = width[i] * side;
            consumer.addVertex(pose, (float) (x[i] + sideX[i] * offset),
                            (float) (y[i] + sideY[i] * offset), (float) (z[i] + sideZ[i] * offset))
                    .setColor(red, green, blue, (int) Math.clamp(alpha[i] * opacity * 255, 0, 255));
        }
    }

    /**
     * 仅客户端主线程调用；包处理通过 enqueueWork 进入，渲染与 tick 不并发。
     *
     * <p>出生时刻取单调特效时钟的当前值（不是世界时间）：世界时间会被服务器同步包
     * 覆盖，用它当基准会让正在播放的闪电在卡顿同步后倒退或重新播放。
     */
    public static void spawn(Vec3 hit, Vec3 fist, long seed) {
        var mc = Minecraft.getInstance();
        syncLevel(mc.level);
        if (activeLevel == null || !finite(hit) || !finite(fist)) return;
        if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(hit) > MAX_DISTANCE_SQUARED) return;
        FxClock.sample(worldTicks(mc));
        double birthTick = FxClock.birth();
        expire(FxClock.now());
        if (FLASHES.size() >= MAX_FLASHES) FLASHES.removeFirst();
        var cfg = BlackFlashConfig.CONFIG;
        FLASHES.add(new Flash(hit, fist, seed, birthTick,
                new FlashGeometry.Timing(cfg.flashStartMs.get(), cfg.flashSpreadMs.get(), cfg.flashFadeMs.get())));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        syncLevel(mc.level);
        if (activeLevel == null) return;
        // 只按实际观测到的世界时间推进；暂停、/tick freeze 时世界时间不动，特效也冻结。
        FxClock.sample(worldTicks(mc));
        expire(FxClock.now());
    }

    /** 当前世界时间（含帧插值），用作特效时钟的唯一输入。 */
    private static double worldTicks(Minecraft mc) {
        if (activeLevel == null) return FxClock.now();
        return activeLevel.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(true);
    }

    /** 与状态/HUD 生命周期共享同一个世界切换重置点。 */
    public static void syncClientLevel() {
        syncLevel(Minecraft.getInstance().level);
    }

    private static void syncLevel(ClientLevel level) {
        if (activeLevel != level) {
            FLASHES.clear();
            FxClock.reset();
            activeLevel = level;
        }
    }

    private static void expire(double nowTick) {
        for (int i = FLASHES.size() - 1; i >= 0; i--) {
            Flash flash = FLASHES.get(i);
            if (flash.age(nowTick) >= flash.timing.lifetimeMs()) FLASHES.remove(i);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        syncLevel(mc.level);
        double nowTick = 0;
        if (activeLevel != null) {
            double worldTicks = worldTicks(mc);
            // 渲染帧只采样世界时钟；回退的同步值被 FxClock 丢弃。
            FxClock.sample(worldTicks);
            nowTick = FxClock.now();
            expire(nowTick);
        }
        if (FLASHES.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        boolean anyVisible = false;
        for (Flash flash : FLASHES) {
            flash.visible = camera.distanceToSqr(flash.hit) <= MAX_DISTANCE_SQUARED
                    && event.getFrustum().isVisible(flash.bounds);
            if (!flash.visible) continue;
            anyVisible = true;
            double age = flash.age(nowTick), progress = flash.timing.progress(age);
            for (Ribbon ribbon : flash.ribbons) ribbon.prepare(flash.hit, camera, flash.timing, age, progress);
        }
        if (!anyVisible) return;

        Matrix4f pose = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        drawPass(buffers, pose, RenderTypes.GLOW, 0);
        drawPass(buffers, pose, RenderTypes.EDGE, 1);
        drawPass(buffers, pose, RenderTypes.CORE, 2);
    }

    private static void drawPass(MultiBufferSource.BufferSource buffers, Matrix4f pose, RenderType type, int pass) {
        // 自定义材质共用 sharedBuffer；必须整批提交，不能交替持有失效的 VertexConsumer。
        VertexConsumer consumer = buffers.getBuffer(type);
        for (Flash flash : FLASHES) {
            if (!flash.visible) continue;
            for (Ribbon ribbon : flash.ribbons) {
                if (pass == 0) ribbon.draw(consumer, pose, 2.3, 212, 30, 27, ribbon.bolt.energy(), true);
                else if (pass == 1) ribbon.draw(consumer, pose, 1.14, 174, 25, 27, 0.94, false);
                else ribbon.draw(consumer, pose, 0.86, 10, 3, 3, 1, false);
            }
        }
        buffers.endBatch(type);
    }

    /** 近似持械手位置，不读取第一人称专用骨骼，旁观者也能生成第二起点。 */
    public static Vec3 fistPosition(int attackerId, Vec3 fallback) {
        var level = Minecraft.getInstance().level;
        if (level == null) return fallback;
        Entity attacker = level.getEntity(attackerId);
        if (attacker == null) return fallback;
        Vec3 look = attacker.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        double hand = attacker instanceof LivingEntity living && living.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        return attacker.getEyePosition().add(look.scale(0.65)).add(right.scale(hand * 0.22)).add(0, -0.25, 0);
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    public static void clear() {
        FLASHES.clear();
        activeLevel = null;
    }
}
