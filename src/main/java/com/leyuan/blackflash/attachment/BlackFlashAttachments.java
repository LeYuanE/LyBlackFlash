package com.leyuan.blackflash.attachment;

import com.leyuan.blackflash.BlackFlash;
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * 玩家状态（NeoForge Data Attachment）。
 *
 * count / streak 持久化且死亡不清零；无我与时间窗字段只活在内存里，重登自然失效。
 */
public final class BlackFlashAttachments {
    private BlackFlashAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BlackFlash.MOD_ID);

    /** 已打出黑闪次数（熟练度）：同步 + 持久化 + 死亡保留 */
    public static final Supplier<AttachmentType<Integer>> COUNT =
            ATTACHMENTS.register("black_flash_count", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).copyOnDeath().build());

    /** 当前连击：同步 + 持久化 */
    public static final Supplier<AttachmentType<Integer>> STREAK =
            ATTACHMENTS.register("black_flash_streak", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).copyOnDeath().build());

    /** 连击窗口起点（游戏刻，仅服务端） */
    public static final Supplier<AttachmentType<Long>> STREAK_AT =
            ATTACHMENTS.register("black_flash_streak_at", () -> AttachmentType.builder(() -> 0L).build());

    /** 无我境界结束时刻（游戏刻）：同步、不持久化 */
    public static final Supplier<AttachmentType<Long>> MUGA_UNTIL =
            ATTACHMENTS.register("black_flash_muga_until", () -> AttachmentType.builder(() -> 0L)
                    .sync(ByteBufCodecs.VAR_LONG).build());

    /** 无我境界层数：同步、不持久化 */
    public static final Supplier<AttachmentType<Integer>> MUGA_STACKS =
            ATTACHMENTS.register("black_flash_muga_stacks", () -> AttachmentType.builder(() -> 0)
                    .sync(ByteBufCodecs.VAR_INT).build());

    /** 当日（游戏日）黑闪次数与所属天号：为 daily_5 成就服务 */
    public static final Supplier<AttachmentType<Integer>> DAILY_COUNT =
            ATTACHMENTS.register("black_flash_daily_count", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());
    public static final Supplier<AttachmentType<Long>> DAILY_DAY =
            ATTACHMENTS.register("black_flash_daily_day", () -> AttachmentType.builder(() -> -1L)
                    .serialize(Codec.LONG).build());

    /** /blackflash force 标记（0/1），仅服务端 */
    public static final Supplier<AttachmentType<Integer>> FORCE =
            ATTACHMENTS.register("black_flash_force", () -> AttachmentType.builder(() -> 0).build());
}
