package com.leyuan.blackflash.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * 把世界时间喂给 {@link FxClock} 的桥接层。
 *
 * <p>单独一个类，是为了让 {@link FxClock} 保持纯 Java、可被离线单测直接编译执行；
 * 任何引用 Minecraft 类型的东西都留在这一层。
 */
final class FxTick {
    private FxTick() {}

    /**
     * 每帧 / 每刻调用一次。只在客户端线程调用（渲染事件与 HUD 层都在客户端线程）。
     *
     * <p>未进入世界（主菜单、加载中）时不推进时钟，因此 HUD 演出不会在主菜单里
     * 被世界时间的一次跳变带偏。
     */
    static void feed() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        double worldTicks = level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(true);
        FxClock.advance(worldTicks, 0, true);
    }
}
