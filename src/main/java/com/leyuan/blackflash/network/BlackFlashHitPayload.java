package com.leyuan.blackflash.network;

import com.leyuan.blackflash.BlackFlash;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * S→C 仅攻击者接收的命中反馈；预览不代表真实成长。
 *
 * @param mugaActivated 本次命中是否让无我「从无到有」。客户端只在这一刻播放激活脉冲，
 *                      已处于无我时的叠层/刷新不重复播放
 */
public record BlackFlashHitPayload(UUID playerId, ResourceLocation dimension,
                                  int streak, int mugaStacks, boolean mugaActivated,
                                  boolean preview) implements CustomPacketPayload {
    public static final Type<BlackFlashHitPayload> TYPE = new Type<>(BlackFlash.rl("hit"));

    public static final StreamCodec<FriendlyByteBuf, BlackFlashHitPayload> STREAM_CODEC =
            StreamCodec.of(BlackFlashHitPayload::encode, BlackFlashHitPayload::decode);

    private static void encode(FriendlyByteBuf buf, BlackFlashHitPayload p) {
        buf.writeUUID(p.playerId());
        buf.writeResourceLocation(p.dimension());
        buf.writeVarInt(p.streak());
        buf.writeVarInt(p.mugaStacks());
        buf.writeBoolean(p.mugaActivated());
        buf.writeBoolean(p.preview());
    }

    private static BlackFlashHitPayload decode(FriendlyByteBuf buf) {
        return new BlackFlashHitPayload(buf.readUUID(), buf.readResourceLocation(),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
