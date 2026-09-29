package com.leyuan.blackflash;

import com.leyuan.blackflash.advancement.BlackFlashTrigger;
import com.leyuan.blackflash.attachment.BlackFlashAttachments;
import com.leyuan.blackflash.client.BlackFlashClient;
import com.leyuan.blackflash.config.BlackFlashConfig;
import com.leyuan.blackflash.network.NetworkHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Ly黑闪 主入口。
 *
 * 改编自《咒术回战》的"黑闪"：跳跃斩（原版暴击）命中时以极低概率触发，
 * 伤害替换为 max(攻击力,2)^2.5，附带黑心红边闪电演出，并永久提升后续概率。
 */
@Mod(BlackFlash.MOD_ID)
public final class BlackFlash {
    public static final String MOD_ID = "black_flash";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 音效注册（1.21 起声音需要注册 SoundEvent，文件映射见 sounds.json） */
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_IMPACT =
            SOUNDS.register("impact", () -> SoundEvent.createVariableRangeEvent(rl("impact")));

    /** 成就触发器：black_flash:landed，每次黑闪结算时在服务端触发 */
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, MOD_ID);
    public static final DeferredHolder<CriterionTrigger<?>, BlackFlashTrigger> FLASH_TRIGGER =
            TRIGGERS.register("landed", BlackFlashTrigger::new);

    public BlackFlash(IEventBus modBus) {
        BlackFlashAttachments.ATTACHMENTS.register(modBus);
        SOUNDS.register(modBus);
        TRIGGERS.register(modBus);

        // 模组总线事件：网络包注册（两侧）与 HUD 层注册（仅客户端）
        modBus.register(NetworkHandler.class);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.register(BlackFlashClient.class);
        }

        ModLoadingContext.get().getActiveContainer()
                .registerConfig(ModConfig.Type.COMMON, BlackFlashConfig.SPEC);

        LOGGER.info("[black_flash] Ly黑闪 v1 已加载");
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
