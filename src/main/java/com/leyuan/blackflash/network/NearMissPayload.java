package com.leyuan.blackflash.network;

import com.leyuan.blackflash.BlackFlash;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S→C 微反馈包（仅发给触发者）：这一下是「差一点」的跳跃斩。
 */
public record NearMissPayload(int attackerId) implements CustomPacketPayload {

    public static final Type<NearMissPayload> TYPE = new Type<>(BlackFlash.rl("near_miss"));

    public static final StreamCodec<FriendlyByteBuf, NearMissPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> buf.writeVarInt(p.attackerId()),
                           buf -> new NearMissPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
