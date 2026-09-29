package com.leyuan.blackflash.logic;

import com.leyuan.blackflash.BlackFlash;
import com.leyuan.blackflash.config.BlackFlashConfig;
import net.minecraft.network.chat.Component;

/**
 * 概率表：
 *   基础概率 = chancePerHit × (已打出次数 + 1)，上限 chanceCap；
 *   无我境界按 mugaMultiplier 做当次乘算（不影响熟练度本身）。
 */
public final class ChanceTable {
    private ChanceTable() {}

    public static double baseChance(int count) {
        double c = BlackFlashConfig.CONFIG.chancePerHit.get() * (count + 1);
        return Math.min(c, BlackFlashConfig.CONFIG.chanceCap.get());
    }

    public static double withMuga(int count, int mugaStacks) {
        double base = baseChance(count);
        double mult = Math.pow(BlackFlashConfig.CONFIG.mugaMultiplier.get(), mugaStacks);
        return Math.min(base * mult, BlackFlashConfig.CONFIG.chanceCap.get());
    }

    /** 段位：初入 / 小成(100) / 大成(1000) / 巅峰(6000) / 圆满(9999) */
    public static Component tier(int count) {
        String key =
                count >= 9999 ? "text.black_flash.tier.perfect" :
                count >= 6000 ? "text.black_flash.tier.summit" :
                count >= 1000 ? "text.black_flash.tier.master" :
                count >= 100  ? "text.black_flash.tier.decent" :
                                "text.black_flash.tier.novice";
        return Component.translatable(key);
    }
}
