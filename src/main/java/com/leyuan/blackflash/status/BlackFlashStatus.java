package com.leyuan.blackflash.status;

/** 服务端个人状态快照；不依赖 Minecraft 或客户端类。 */
public record BlackFlashStatus(int count, int dailyCount, int streak, int mugaStacks,
                               long streakRemainingTicks, long streakDurationTicks,
                               long mugaRemainingTicks, long mugaDurationTicks,
                               long serverGameTime, double baseChance, double effectiveChance,
                               double mugaMultiplier) {}
