package com.leyuan.blackflash.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.leyuan.blackflash.BlackFlash;
import com.leyuan.blackflash.attachment.BlackFlashAttachments;
import com.leyuan.blackflash.config.BlackFlashConfig;
import com.leyuan.blackflash.logic.ChanceTable;
import com.leyuan.blackflash.logic.GrowthManager;
import com.leyuan.blackflash.logic.StatusSyncHandler;
import com.leyuan.blackflash.network.BlackFlashEffectPayload;
import com.leyuan.blackflash.network.BlackFlashHitPayload;
import com.leyuan.blackflash.network.NetworkHandler;
import net.minecraft.world.phys.Vec3;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 调试指令（需要 OP 等级 2，可用配置整体禁用）：
 *   /blackflash set <n>   设定已打出次数
 *   /blackflash force     下一次暴击必定触发
 *   /blackflash preview [seed]  在前方预览演出，不造成伤害或改变成长
 *   /blackflash reset     清零熟练度与连击
 *   /blackflash stats     显示次数 / 概率 / 段位 / 连击
 */
@EventBusSubscriber(modid = BlackFlash.MOD_ID)
public final class BlackFlashCommand {
    private BlackFlashCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!BlackFlashConfig.CONFIG.commandEnabled.get()) return;
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("blackflash")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 999999))
                                .executes(BlackFlashCommand::setCount)))
                .then(Commands.literal("force").executes(BlackFlashCommand::forceNext))
                .then(Commands.literal("preview")
                        .executes(ctx -> preview(ctx, player(ctx).getRandom().nextLong()))
                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                .executes(ctx -> preview(ctx, LongArgumentType.getLong(ctx, "seed")))))
                .then(Commands.literal("reset").executes(BlackFlashCommand::reset))
                .then(Commands.literal("stats").executes(BlackFlashCommand::stats)));
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static int setCount(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = player(ctx);
        int n = IntegerArgumentType.getInteger(ctx, "count");
        p.setData(BlackFlashAttachments.COUNT, n);
        StatusSyncHandler.send(p);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.blackflash.set",
                n, formatPercent(ChanceTable.baseChance(n))), true);
        return 1;
    }

    private static int forceNext(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = player(ctx);
        p.setData(BlackFlashAttachments.FORCE, 1);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.blackflash.force"), false);
        return 1;
    }

    private static int preview(CommandContext<CommandSourceStack> ctx, long seed)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = player(ctx);
        Vec3 hit = p.getEyePosition().add(p.getLookAngle().scale(3));
        NetworkHandler.sendToNearbyPlayers(p.serverLevel(),
                new BlackFlashEffectPayload(p.getId(), -1, hit.x, hit.y, hit.z, seed));
        p.connection.send(new BlackFlashHitPayload(p.getUUID(), p.level().dimension().location(), 1, 0, false, true));
        ctx.getSource().sendSuccess(() -> Component.translatable("command.blackflash.preview", seed), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = player(ctx);
        p.setData(BlackFlashAttachments.COUNT, 0);
        p.setData(BlackFlashAttachments.STREAK, 0);
        p.setData(BlackFlashAttachments.STREAK_AT, 0L);
        p.setData(BlackFlashAttachments.DAILY_COUNT, 0);
        p.setData(BlackFlashAttachments.FORCE, 0);
        p.setData(BlackFlashAttachments.MUGA_STACKS, 0);
        p.setData(BlackFlashAttachments.MUGA_UNTIL, 0L);
        GrowthManager.forgetPlayer(p.getUUID());
        StatusSyncHandler.send(p, true);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.blackflash.reset"), true);
        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = player(ctx);
        int count = p.getData(BlackFlashAttachments.COUNT);
        int streak = GrowthManager.liveStreak(p);
        int stacks = GrowthManager.mugaStacks(p);
        ctx.getSource().sendSuccess(() -> Component.literal(
                String.join("\n",
                        Component.translatable("command.blackflash.stats.count", count).getString(),
                        Component.translatable("command.blackflash.stats.chance",
                                formatPercent(ChanceTable.withMuga(count, stacks))).getString(),
                        Component.translatable("command.blackflash.stats.tier",
                                ChanceTable.tier(count).getString()).getString(),
                        Component.translatable("command.blackflash.stats.streak", streak).getString())), false);
        return 1;
    }

    /** 概率显示为百分比字符串（保留两位小数） */
    private static String formatPercent(double chance) {
        return String.format("%.2f%%", chance * 100.0);
    }
}
