package com.leyuan.blackflash.network;

import com.leyuan.blackflash.BlackFlash;
import com.leyuan.blackflash.status.BlackFlashStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** S→C 个人状态；玩家与维度标识用于丢弃旧世界的数据包。 */
public record BlackFlashStatusPayload(UUID playerId, ResourceLocation dimension,
                                     BlackFlashStatus status, boolean resetVisuals) implements CustomPacketPayload {
    public static final Type<BlackFlashStatusPayload> TYPE = new Type<>(BlackFlash.rl("status"));

    public static final StreamCodec<FriendlyByteBuf, BlackFlashStatusPayload> STREAM_CODEC =
            StreamCodec.of(BlackFlashStatusPayload::encode, BlackFlashStatusPayload::decode);

    private static void encode(FriendlyByteBuf buf, BlackFlashStatusPayload p) {
        buf.writeUUID(p.playerId());
        buf.writeResourceLocation(p.dimension());
        BlackFlashStatus s = p.status();
        buf.writeVarInt(s.count());
        buf.writeVarInt(s.dailyCount());
        buf.writeVarInt(s.streak());
        buf.writeVarInt(s.mugaStacks());
        buf.writeLong(s.streakRemainingTicks());
        buf.writeLong(s.streakDurationTicks());
        buf.writeLong(s.mugaRemainingTicks());
        buf.writeLong(s.mugaDurationTicks());
        buf.writeLong(s.serverGameTime());
        buf.writeDouble(s.baseChance());
        buf.writeDouble(s.effectiveChance());
        buf.writeDouble(s.mugaMultiplier());
        buf.writeBoolean(p.resetVisuals());
    }

    private static BlackFlashStatusPayload decode(FriendlyByteBuf buf) {
        UUID playerId = buf.readUUID();
        ResourceLocation dimension = buf.readResourceLocation();
        BlackFlashStatus status = new BlackFlashStatus(
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new BlackFlashStatusPayload(playerId, dimension, status, buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
