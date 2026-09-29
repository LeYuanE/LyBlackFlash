package com.leyuan.blackflash.network;

import com.leyuan.blackflash.BlackFlash;
import com.leyuan.blackflash.BlackFlash;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S→C 演出包：攻击者 id、目标 id、命中点、随机种子。
 * 种子必须由服务端下发，否则各客户端生成的闪电形状不同步。
 */
public record BlackFlashEffectPayload(int attackerId, int targetId,
                                      double x, double y, double z, long seed) implements CustomPacketPayload {

    public static final Type<BlackFlashEffectPayload> TYPE = new Type<>(BlackFlash.rl("effect"));

    public static final StreamCodec<FriendlyByteBuf, BlackFlashEffectPayload> STREAM_CODEC =
            StreamCodec.of(BlackFlashEffectPayload::encode, BlackFlashEffectPayload::decode);

    private static void encode(FriendlyByteBuf buf, BlackFlashEffectPayload p) {
        buf.writeVarInt(p.attackerId());
        buf.writeVarInt(p.targetId());
        buf.writeDouble(p.x());
        buf.writeDouble(p.y());
        buf.writeDouble(p.z());
        buf.writeLong(p.seed());
    }

    private static BlackFlashEffectPayload decode(FriendlyByteBuf buf) {
        return new BlackFlashEffectPayload(
                buf.readVarInt(), buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
