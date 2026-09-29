package com.leyuan.blackflash.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * 自定义成就触发器 black_flash:landed。
 *
 * 每次黑闪结算时触发，携带 {次数, 连击, 当日次数, 是否空手} 四个数值，
 * 6 个成就 JSON 用 MinMaxBounds 条件从同一触发器里筛各自需要的场景。
 */
public class BlackFlashTrigger extends SimpleCriterionTrigger<BlackFlashTrigger.Instance> {

    public static final Codec<Instance> INSTANCE_CODEC = RecordCodecBuilder.create(in -> in.group(
            MinMaxBounds.Ints.CODEC.optionalFieldOf("count", MinMaxBounds.Ints.ANY).forGetter(Instance::count),
            MinMaxBounds.Ints.CODEC.optionalFieldOf("streak", MinMaxBounds.Ints.ANY).forGetter(Instance::streak),
            MinMaxBounds.Ints.CODEC.optionalFieldOf("daily", MinMaxBounds.Ints.ANY).forGetter(Instance::daily),
            Codec.BOOL.optionalFieldOf("bare_hand", false).forGetter(Instance::bareHand)
    ).apply(in, Instance::new));

    @Override
    public Codec<Instance> codec() {
        return INSTANCE_CODEC;
    }

    public void trigger(ServerPlayer player, int count, int streak, int daily, boolean bareHand) {
        super.trigger(player, instance -> instance.matches(count, streak, daily, bareHand));
    }

    public record Instance(MinMaxBounds.Ints count, MinMaxBounds.Ints streak,
                           MinMaxBounds.Ints daily, boolean bareHand) implements SimpleCriterionTrigger.SimpleInstance {

        @Override
        public Optional<net.minecraft.advancements.critereon.ContextAwarePredicate> player() {
            return Optional.empty();
        }

        public boolean matches(int countValue, int streakValue, int dailyValue, boolean bareHandValue) {
            return count.matches(countValue)
                    && streak.matches(streakValue)
                    && daily.matches(dailyValue)
                    && (!bareHand || bareHandValue);
        }
    }
}
