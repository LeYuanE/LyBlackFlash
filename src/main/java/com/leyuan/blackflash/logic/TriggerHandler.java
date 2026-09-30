package com.leyuan.blackflash.logic;

import com.leyuan.blackflash.BlackFlash;
import com.leyuan.blackflash.attachment.BlackFlashAttachments;
import com.leyuan.blackflash.config.BlackFlashConfig;
import com.leyuan.blackflash.network.BlackFlashEffectPayload;
import com.leyuan.blackflash.network.BlackFlashHitPayload;
import com.leyuan.blackflash.network.NearMissPayload;
import com.leyuan.blackflash.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 触发链路的服务端半段：判定、掷骰、改伤害、击退、状态成长、广播演出，全部在这一个
 * 事件里完成（单事件，无跨事件状态）。
 *
 * <p><b>伤害注入点在减免之前</b>：用 {@link LivingIncomingDamageEvent}（在
 * {@code LivingEntity#hurt} 中、任何减免计算之前触发），按倍率<b>放大</b>原始伤害，
 * 而不是替换减免后的结果。这样附魔、药水等加成照常生效，护甲、抗性、
 * 保护附魔等减免也照常生效 —— 黑闪只是「这一刀更重」，其余走原版管线。
 */
@EventBusSubscriber(modid = BlackFlash.MOD_ID)
public final class TriggerHandler {
    private TriggerHandler() {}

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;

        BlackFlashConfig cfg = BlackFlashConfig.CONFIG;
        if (!cfg.enabled.get()) return;

        // ① 攻击者必须是玩家、且是玩家亲手近战
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!event.getSource().is(DamageTypeTags.IS_PLAYER_ATTACK)) return;
        if (event.getSource().getDirectEntity() != player) return;
        if (event.getSource().is(DamageTypeTags.IS_PROJECTILE)) return;
        if (event.getSource().is(DamageTypeTags.IS_FALL)) return;

        // PvP 开关
        if (victim instanceof ServerPlayer && !cfg.pvpEnabled.get()) return;

        // ② 真正造成伤害
        float original = event.getAmount();
        if (cfg.requireDamageDealt.get() && original <= 0.0F) return;

        // ③ 蓄力与暴击校验
        //
        //    采样参数必须与原版 Player.attack 一致（那里读 getAttackStrengthScale(0.5F)
        //    并用 f2 > 0.9F 作暴击门槛）。若这里用 0.0F，冷却差半 tick 的挥击会出现
        //    「原版判定为暴击、模组却算作未满蓄力」的分歧，连 /blackflash force 都会被挡住。
        float strength = player.getAttackStrengthScale(0.5F);
        if (cfg.requireFullCharge.get() && strength <= 0.9F) {
            maybeNearMiss(player, victim, strength);
            return;
        }
        boolean crit = isCrit(player, strength);
        if (cfg.requireCrit.get() && !crit) return;

        // ④ 读熟练度 → 算概率（× 无我倍率）→ 掷骰
        boolean forced = player.getData(BlackFlashAttachments.FORCE) != 0;
        int count = player.getData(BlackFlashAttachments.COUNT);
        int stacks = GrowthManager.mugaStacks(player);
        double chance = forced ? 1.0 : ChanceTable.withMuga(count, stacks);
        if (player.getRandom().nextFloat() >= chance) {
            if (crit) {
                GrowthManager.onCritMissed(player);
                StatusSyncHandler.send(player);
            }
            return;
        }

        // ⑤ 兑付：把这一刀放大到 base ^ (2.5 ^ 连击数)
        //
        //    连击数取「本次命中将是第几次黑闪」= 当前窗口内的连击 + 1。
        //    第 1 次：base ^ 2.5；第 2 次：base ^ 6.25；第 3 次：base ^ 15.625 …
        //    连击断掉后回到第 1 次（GrowthManager.liveStreak 会因超窗返回 0）。
        //
        //    base 取「本刀的实际伤害」而不是裸攻击力属性：这样附魔、药水、力量效果
        //    都会真正计入基数（锋利 V 的 10 点比无附魔的 7 点更强），与设计决策 28
        //    「基数含附魔与药水，2.5 次方叠在所有加成之后」一致。
        //
        //    注入点在减免之前，所以放大后的数值仍会被护甲、抗性、保护附魔照常减免 ——
        //    黑闪只是「这一刀更重」，不绕过任何防御。
        int effectiveStreak = GrowthManager.liveStreak(player) + 1;
        double base = Math.max(original, cfg.emptyHandFloor.get());
        double exponent = Math.pow(2.5, effectiveStreak);
        double amplified = Math.pow(base, exponent);

        int cap = cfg.damageCap.get();
        if (cap > 0) amplified = Math.min(amplified, cap);

        // 仅防 Infinity/NaN 进入伤害管线（float 上限），不改变任何有限结果
        if (!Double.isFinite(amplified) || amplified > (double) Float.MAX_VALUE) {
            amplified = Float.MAX_VALUE;
        } else if (amplified < 0.0) {
            amplified = 0.0;
        }
        event.setAmount((float) amplified);

        // ⑥ 额外击退（方向沿用原版：沿玩家朝向，sin 正 / cos 负）
        double yawRad = Math.toRadians(player.getYRot());
        victim.knockback(cfg.blackflashKnockback.get(), Math.sin(yawRad), -Math.cos(yawRad));

        // ⑦ 状态成长：熟练度 +1、连击 +1、无我刷新
        GrowthManager.FlashResult result = GrowthManager.onFlashLanded(player);
        StatusSyncHandler.send(player);
        player.connection.send(new BlackFlashHitPayload(player.getUUID(), player.level().dimension().location(),
                result.streak(), GrowthManager.mugaStacks(player), false));

        // ⑧ 音效（命中点，3D 定位靠单声道 ogg）+ 广播演出包（含服务端随机种子）
        //
        //    位置用 getY(0.6) 而非 getY()：getY() 返回【脚底】，闪电会从地面炸开、视觉偏低。
        //    getY(0.6) 取碰撞箱 60% 高度处（约胸口），更像"命中身体"。
        victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                BlackFlash.SOUND_IMPACT.get(), SoundSource.PLAYERS, 2.5F, 1.0F);
        long seed = player.getRandom().nextLong();
        NetworkHandler.sendToNearbyPlayers(player.serverLevel(),
                new BlackFlashEffectPayload(player.getId(), victim.getId(),
                        victim.getX(), victim.getY(0.6), victim.getZ(), seed));

        // ⑨ 成就判定
        BlackFlash.FLASH_TRIGGER.get().trigger(player,
                result.count(), result.streak(), result.daily(), result.bareHand());

        if (forced) {
            BlackFlash.LOGGER.debug("[black_flash] forced flash on {}", player.getGameProfile().getName());
        }
    }

    /**
     * 原版暴击判定（玩家俗称「跳跃斩」）：
     * 蓄力 > 0.9 && 下落中 && !脚踩地 && !爬梯子 && !水中 && !失明 && !骑乘 && !冲刺。
     */
    private static boolean isCrit(ServerPlayer player, float strength) {
        return strength > 0.9F
                && player.fallDistance > 0.0F
                && !player.onGround()
                && !player.onClimbable()
                && !player.isInWater()
                && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isPassenger()
                && !player.isSprinting();
    }

    /**
     * 「差一点」：蓄力已经越过 0.85、但还没到原版暴击门槛 0.9 的挥击 —— 给一次微反馈。
     *
     * <p>门槛 0.9 与原版一致（见 {@link #isCrit}），所以这一档永远不会和真正的暴击重叠；
     * 采样精度同样用 0.5F，反馈的时机才与玩家看到的那一刀吻合。
     */
    private static void maybeNearMiss(ServerPlayer player, LivingEntity victim, float strength) {
        BlackFlashConfig cfg = BlackFlashConfig.CONFIG;
        if (!cfg.nearMissFeedback.get()) return;
        if (strength <= 0.85F) return;
        boolean otherwiseCrit = player.fallDistance > 0.0F
                && !player.onGround()
                && !player.onClimbable()
                && !player.isInWater()
                && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isPassenger()
                && !player.isSprinting();
        if (!otherwiseCrit) return;
        if (GrowthManager.allowNearMiss(player)) {
            player.connection.send(new NearMissPayload(player.getId()));
        }
    }
}
