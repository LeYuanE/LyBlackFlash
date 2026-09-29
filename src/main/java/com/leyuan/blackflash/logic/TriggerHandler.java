package com.leyuan.blackflash.logic;

import com.leyuan.blackflash.BlackFlash;
import com.leyuan.blackflash.attachment.BlackFlashAttachments;
import com.leyuan.blackflash.config.BlackFlashConfig;
import com.leyuan.blackflash.network.BlackFlashEffectPayload;
import com.leyuan.blackflash.network.NearMissPayload;
import com.leyuan.blackflash.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 触发链路的服务端半段：判定、掷骰、改伤害、击退、状态成长、广播演出，全部在这一个
 * LivingDamageEvent.Pre 里完成（单事件，无跨事件状态）。
 *
 * 该事件在护甲减免之后触发，setNewDamage 是绝对值赋值 —— 黑闪伤害天然绕过护甲与抗性。
 */
@EventBusSubscriber(modid = BlackFlash.MOD_ID)
public final class TriggerHandler {
    private TriggerHandler() {}

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
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
        if (cfg.requireDamageDealt.get() && event.getNewDamage() <= 0.0F) return;

        // ③ 蓄力与暴击校验
        float strength = player.getAttackStrengthScale(0.0F);
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
            if (crit) GrowthManager.onCritMissed(player);
            return;
        }

        // ⑤ 兑付：伤害 = max(攻击力, 保底) ^ (2.5 ^ 连击数)
        //
        //    连击数取「本次命中将是第几次黑闪」= 当前窗口内的连击 + 1。
        //    第 1 次：base ^ 2.5；第 2 次：base ^ 6.25；第 3 次：base ^ 15.625 …
        //    连击断掉后回到第 1 次（GrowthManager.liveStreak 会因超窗返回 0）。
        int effectiveStreak = GrowthManager.liveStreak(player) + 1;
        double base = Math.max(
                player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE),
                cfg.emptyHandFloor.get());
        double exponent = Math.pow(2.5, effectiveStreak);
        double damage = Math.pow(base, exponent);

        // 仅防 Infinity/NaN 进入 setNewDamage（float 上限），不改变任何有限结果
        if (!Double.isFinite(damage) || damage > (double) Float.MAX_VALUE) {
            damage = Float.MAX_VALUE;
        }
        int cap = cfg.damageCap.get();
        if (cap > 0) damage = Math.min(damage, cap);
        event.setNewDamage((float) damage);

        // ⑥ 额外击退（方向沿用原版：沿玩家朝向，sin 正 / cos 负）
        double yawRad = Math.toRadians(player.getYRot());
        victim.knockback(cfg.blackflashKnockback.get(), Math.sin(yawRad), -Math.cos(yawRad));

        // ⑦ 状态成长：熟练度 +1、连击 +1、无我刷新
        GrowthManager.FlashResult result = GrowthManager.onFlashLanded(player);

        // ⑧ 音效（命中点，3D 定位靠单声道 ogg）+ 广播演出包（含服务端随机种子）
        victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                BlackFlash.SOUND_IMPACT.get(), SoundSource.PLAYERS, 2.5F, 1.0F);
        long seed = player.getRandom().nextLong();
        NetworkHandler.sendToAllPlayers(player.server,
                new BlackFlashEffectPayload(player.getId(), victim.getId(),
                        victim.getX(), victim.getY(), victim.getZ(), seed));

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

    /** 「差一点」：蓄力落在 0.85~0.9 之间、其余暴击条件全满足的挥击 —— 给一次微反馈。 */
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
