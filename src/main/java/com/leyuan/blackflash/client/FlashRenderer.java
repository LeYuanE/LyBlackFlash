package com.leyuan.blackflash.client;

import com.leyuan.blackflash.BlackFlash;
import com.leyuan.blackflash.config.BlackFlashConfig;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 世界层闪电：黑心 + 红边的放射状折线，种子驱动、全场可见。
 *
 * 不用原版粒子（粒子基元是"点"，画不出折线，也做不了双色描边）。
 * 这里以自定义几何直接叠两层四边形：宽的红边一层、窄的黑心一层。
 * 渲染管线用 debugQuads：POSITION_COLOR 四边形、标准 alpha 混合、不剔除背面、输出主画面缓冲。
 * （不用原版闪电管线：它是"加法混合 + 天气缓冲层"，黑色在加法混合下画不出颜色，
 *   且内容要经后处理链合成，实测整条特效看不见。）
 */
@EventBusSubscriber(modid = BlackFlash.MOD_ID, value = Dist.CLIENT)
public final class FlashRenderer {
    private FlashRenderer() {}

    /** 配色：黑心 #0A0303、红边 #AE2524、亮红 #D43732（点缀） */
    private static final int[] CORE = {10, 3, 3};
    private static final int[] EDGE = {174, 37, 36};
    private static final int[] EDGE_BRIGHT = {212, 55, 50};

    private static final int BRANCHES = 14;
    private static final int SEGMENTS = 8;

    private record Flash(Vec3 hit, Vec3 fist, long seed, long birthMs) {}

    private static final List<Flash> FLASHES = new ArrayList<>();

    public static synchronized void spawn(Vec3 hit, Vec3 fist, long seed) {
        FLASHES.add(new Flash(hit, fist, seed, System.currentTimeMillis()));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        List<Flash> alive;
        BlackFlashConfig cfgPre = BlackFlashConfig.CONFIG;
        long total = cfgPre.flashStartMs.get() + cfgPre.flashSpreadMs.get() + cfgPre.flashFadeMs.get();
        long now0 = System.currentTimeMillis();
        synchronized (FLASHES) {
            if (FLASHES.isEmpty()) return;
            FLASHES.removeIf(f -> now0 - f.birthMs() > total);
            alive = new ArrayList<>(FLASHES);
        }
        if (alive.isEmpty()) return;

        BlackFlashConfig cfg = BlackFlashConfig.CONFIG;
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType boltType = RenderType.debugQuads();
        VertexConsumer vc = buffers.getBuffer(boltType);
        Matrix4f view = new Matrix4f(event.getModelViewMatrix());
        Vec3 cam = event.getCamera().getPosition();
        long now = System.currentTimeMillis();

        for (Flash f : alive) {
            drawFlash(vc, view, cam, f, now, cfg);
        }
        buffers.endBatch(boltType);
    }

    private static void drawFlash(VertexConsumer vc, Matrix4f view, Vec3 cam,
                                  Flash f, long now, BlackFlashConfig cfg) {
        long age = now - f.birthMs();
        int start = cfg.flashStartMs.get();
        int spread = cfg.flashSpreadMs.get();
        int fade = cfg.flashFadeMs.get();

        // 0–start 炸开（短粗）→ start+spread 蔓延 → 之后退散
        double lenFrac;
        if (age <= start) {
            lenFrac = 0.15 + 0.2 * (age / (double) start);
        } else {
            lenFrac = Math.min(1.0, 0.35 + 0.65 * ((age - start) / (double) spread));
        }

        double fadeBase = start + spread;

        for (int i = 0; i < BRANCHES; i++) {
            // 双中心：奇数分支从拳头位置起
            Vec3 origin = (i % 2 == 0) ? f.hit() : f.fist();

            // 出生错开：先出生的先淡出
            double birthDelay = (i / (double) BRANCHES) * spread * 0.3;
            if (age < birthDelay) continue;

            double fadeStart = fadeBase + i * (fade * 0.25 / BRANCHES) + birthDelay;
            double alpha = age <= fadeStart ? 1.0 : Math.max(0.0, 1.0 - (age - fadeStart) / (double) fade);
            if (alpha <= 0.01) continue;
            int a255 = (int) (alpha * 255.0);

            RandomSource rng = RandomSource.create(f.seed() * 31L + i);
            double[][] pts = new double[SEGMENTS + 1][3];
            pts[0][0] = origin.x;
            pts[0][1] = origin.y;
            pts[0][2] = origin.z;

            // 随机初始方向（偏上半球少一点，整体放射）
            double theta = rng.nextDouble() * Math.PI * 2.0;
            double phi = Math.acos(2.0 * rng.nextDouble() - 1.0);
            double dx = Math.sin(phi) * Math.cos(theta);
            double dy = Math.cos(phi);
            double dz = Math.sin(phi) * Math.sin(theta);

            int visible = Math.max(1, (int) Math.ceil(SEGMENTS * lenFrac));
            double px = origin.x, py = origin.y, pz = origin.z;
            for (int s = 1; s <= SEGMENTS; s++) {
                double len = 0.25 + rng.nextDouble() * 0.55;
                // 折线抖动：随机方向扰动后归一化
                double jx = dx + (rng.nextDouble() - 0.5) * 0.9;
                double jy = dy + (rng.nextDouble() - 0.5) * 0.9;
                double jz = dz + (rng.nextDouble() - 0.5) * 0.9;
                double jl = Math.sqrt(jx * jx + jy * jy + jz * jz);
                if (jl < 1e-6) jl = 1.0;
                jx /= jl; jy /= jl; jz /= jl;
                px += jx * len;
                py += jy * len;
                pz += jz * len;
                pts[s][0] = px; pts[s][1] = py; pts[s][2] = pz;
                dx = jx; dy = jy; dz = jz;
                if (s >= visible) break;
            }

            // 先红边（宽），后黑心（窄）：两层几何叠加出双色描边
            int[] edgeCol = (i % 3 == 0) ? EDGE_BRIGHT : EDGE;
            stroke(vc, view, cam, pts, visible, 0.13f, edgeCol, a255);
            stroke(vc, view, cam, pts, visible, 0.055f, CORE, a255);
        }
    }

    /**
     * 以相机朝向为 billboard 侧向，把折线画成一串共面四边形。
     *
     * <p><b>坐标空间</b>：event.getModelViewMatrix() 只含相机【旋转】（GameRenderer 用
     * {@code new Matrix4f().rotation(quaternionf)} 构造，没有平移分量），
     * 所以顶点必须传【相机相对坐标】= 世界坐标 − 相机位置。
     * 传世界坐标会把几何画到离相机「坐标数值」那么远的地方，直接超出渲染距离而完全不可见。
     */
    private static void stroke(VertexConsumer vc, Matrix4f view, Vec3 cam,
                               double[][] pts, int segs, float halfWidth, int[] rgb, int alpha) {
        if (segs < 1) return;
        for (int s = 1; s <= segs; s++) {
            // 世界坐标 → 相机相对坐标
            double ax = pts[s - 1][0] - cam.x, ay = pts[s - 1][1] - cam.y, az = pts[s - 1][2] - cam.z;
            double bx = pts[s][0] - cam.x, by = pts[s][1] - cam.y, bz = pts[s][2] - cam.z;

            double dx = bx - ax, dy = by - ay, dz = bz - az;
            double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dl < 1e-6) continue;
            dx /= dl; dy /= dl; dz /= dl;

            // 相机在相对空间里位于原点
            double mx = (ax + bx) * 0.5;
            double my = (ay + by) * 0.5;
            double mz = (az + bz) * 0.5;
            double ml = Math.sqrt(mx * mx + my * my + mz * mz);
            if (ml < 1e-6) { mx = 0; my = 1; mz = 0; ml = 1; }
            mx /= ml; my /= ml; mz /= ml;

            // side = dir × toCam
            double sx = dy * mz - dz * my;
            double sy = dz * mx - dx * mz;
            double sz = dx * my - dy * mx;
            double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-6) { sx = halfWidth; sy = 0; sz = 0; }
            else { sx = sx / sl * halfWidth; sy = sy / sl * halfWidth; sz = sz / sl * halfWidth; }

            vc.addVertex(view, (float) (ax + sx), (float) (ay + sy), (float) (az + sz))
              .setColor(rgb[0], rgb[1], rgb[2], alpha);
            vc.addVertex(view, (float) (ax - sx), (float) (ay - sy), (float) (az - sz))
              .setColor(rgb[0], rgb[1], rgb[2], alpha);
            vc.addVertex(view, (float) (bx - sx), (float) (by - sy), (float) (bz - sz))
              .setColor(rgb[0], rgb[1], rgb[2], alpha);
            vc.addVertex(view, (float) (bx + sx), (float) (by + sy), (float) (bz + sz))
              .setColor(rgb[0], rgb[1], rgb[2], alpha);
        }
    }

    /** 拳头位置：攻击者眼睛前 0.6 格；找不到实体时退回命中点 */
    public static Vec3 fistPosition(int attackerId, Vec3 fallback) {
        var level = Minecraft.getInstance().level;
        if (level == null) return fallback;
        Entity attacker = level.getEntity(attackerId);
        if (attacker == null) return fallback;
        Vec3 eye = attacker.getEyePosition();
        Vec3 look = attacker.getLookAngle();
        return eye.add(look.x * 0.6, look.y * 0.6, look.z * 0.6);
    }

    public static synchronized void clear() {
        FLASHES.clear();
    }
}
