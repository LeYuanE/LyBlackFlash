package com.leyuan.blackflash.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 全部可调数值。COMMON 类型：服务端生效、客户端本地也有同一份（演出时长类键读本地值）。
 */
public final class BlackFlashConfig {
    public static final BlackFlashConfig CONFIG;
    public static final ModConfigSpec SPEC;

    // 总开关与触发
    public final ModConfigSpec.ConfigValue<Boolean> enabled;
    public final ModConfigSpec.ConfigValue<Double> chancePerHit;
    public final ModConfigSpec.ConfigValue<Double> chanceCap;
    public final ModConfigSpec.ConfigValue<Boolean> requireCrit;
    public final ModConfigSpec.ConfigValue<Boolean> requireFullCharge;
    public final ModConfigSpec.ConfigValue<Boolean> requireDamageDealt;
    public final ModConfigSpec.ConfigValue<Boolean> pvpEnabled;

    // 伤害与击退
    public final ModConfigSpec.ConfigValue<Double> emptyHandFloor;
    public final ModConfigSpec.ConfigValue<Integer> damageCap;
    public final ModConfigSpec.ConfigValue<Boolean> armorApplies;
    public final ModConfigSpec.ConfigValue<Double> blackflashKnockback;

    // 无我境界
    public final ModConfigSpec.ConfigValue<Double> mugaMultiplier;
    public final ModConfigSpec.ConfigValue<Long> mugaDurationMs;
    public final ModConfigSpec.ConfigValue<Integer> mugaMaxStacks;
    public final ModConfigSpec.ConfigValue<Boolean> mugaAddsProficiency;

    // 连击
    public final ModConfigSpec.ConfigValue<Long> streakWindowMs;
    public final ModConfigSpec.ConfigValue<Boolean> streakBreakOnCritMiss;

    // 演出时长（毫秒）
    public final ModConfigSpec.ConfigValue<Integer> flashStartMs;
    public final ModConfigSpec.ConfigValue<Integer> flashSpreadMs;
    public final ModConfigSpec.ConfigValue<Integer> flashFadeMs;
    public final ModConfigSpec.ConfigValue<Integer> floatingTextMs;
    public final ModConfigSpec.ConfigValue<Double> floatingTextScale;
    public final ModConfigSpec.ConfigValue<Boolean> fovKickEnabled;
    public final ModConfigSpec.ConfigValue<Boolean> hitPlayerScreenFx;
    public final ModConfigSpec.ConfigValue<Boolean> nearMissFeedback;
    public final ModConfigSpec.ConfigValue<Long> nearMissCooldownMs;

    // 调试
    public final ModConfigSpec.ConfigValue<Boolean> commandEnabled;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        CONFIG = b.configure(BlackFlashConfig::new).getLeft();
        SPEC = b.build();
    }

    private BlackFlashConfig(ModConfigSpec.Builder b) {
        b.comment("黑闪核心机制").push("trigger");
        enabled = b.comment("总开关").define("enabled", true);
        chancePerHit = b.comment("每档概率增量（0.0001 = 0.01%）").define("chancePerHit", 0.0001);
        chanceCap = b.comment("概率上限（0~1）").define("chanceCap", 1.0);
        requireCrit = b.comment("必须是暴击（跳跃斩）").define("requireCrit", true);
        requireFullCharge = b.comment("蓄力必须 > 90%").define("requireFullCharge", true);
        requireDamageDealt = b.comment("必须真正造成伤害").define("requireDamageDealt", true);
        pvpEnabled = b.comment("允许对其他玩家触发").define("pvpEnabled", true);
        b.pop();

        b.comment("伤害与击退").push("damage");
        emptyHandFloor = b.comment("空手伤害保底基数").define("emptyHandFloor", 2.0);
        damageCap = b.comment("伤害上限，0 = 不封顶").define("damageCap", 0);
        armorApplies = b.comment("黑闪伤害是否经过护甲（v1 恒为 false：注入点在护甲减免之后）")
                .define("armorApplies", false);
        blackflashKnockback = b.comment("额外击退强度（原版冲刺攻击约 0.5）").define("blackflashKnockback", 0.3);
        b.pop();

        b.comment("无我境界").push("muga");
        mugaMultiplier = b.comment("无我概率倍率").define("mugaMultiplier", 1.2);
        mugaDurationMs = b.comment("无我持续（毫秒）").define("mugaDurationMs", 20000L);
        mugaMaxStacks = b.comment("无我叠加上限，0 = 无限").define("mugaMaxStacks", 0);
        mugaAddsProficiency = b.comment("无我是否计入熟练度").define("mugaAddsProficiency", false);
        b.pop();

        b.comment("连击").push("streak");
        streakWindowMs = b.comment("连击时间窗（毫秒）").define("streakWindowMs", 10000L);
        streakBreakOnCritMiss = b.comment("暴击命中但未触发时是否清零连击").define("streakBreakOnCritMiss", true);
        b.pop();

        b.comment("演出").push("visual");
        flashStartMs = b.comment("闪电起爆时长（毫秒）").define("flashStartMs", 100);
        flashSpreadMs = b.comment("闪电蔓延时长（毫秒）").define("flashSpreadMs", 1000);
        flashFadeMs = b.comment("闪电退散时长（毫秒）").define("flashFadeMs", 600);
        floatingTextMs = b.comment("飘字总时长（毫秒）").define("floatingTextMs", 1400);
        floatingTextScale = b.comment("飘字大小倍率（1.0 = 原版字体大小）").define("floatingTextScale", 1.5);
        fovKickEnabled = b.comment("FOV 收缩开关").define("fovKickEnabled", true);
        hitPlayerScreenFx = b.comment("被击中玩家屏幕特效开关").define("hitPlayerScreenFx", true);
        nearMissFeedback = b.comment("“差一点”微反馈开关").define("nearMissFeedback", true);
        nearMissCooldownMs = b.comment("微反馈节流（毫秒）").define("nearMissCooldownMs", 1000L);
        b.pop();

        b.comment("调试").push("debug");
        commandEnabled = b.comment("/blackflash 指令开关").define("commandEnabled", true);
        b.pop();
    }
}
