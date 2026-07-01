package com.hainabaichuan75.iac_p.network.packets;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.events.PhysicsAssembleHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;

/**
 * 物理装配触发信号（客户端 → 服务器）。
 * <p>
 * 玩家按住 Ctrl + 鼠标右键时发送此信号。
 * 携带客户端 mc.hitResult 的命中位置（如有），服务端直接使用该位置处理装配，
 * 无需重复射线检测；未命中时 hitPos=null，服务端回退到 SubLevel 感知检测（拆解）。
 */
public record PhysicsAssembleC2SPacket(@Nullable BlockPos hitPos) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(IACP.MODID, "physics_assemble");
    public static final Type<PhysicsAssembleC2SPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, PhysicsAssembleC2SPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PhysicsAssembleC2SPacket decode(RegistryFriendlyByteBuf buf) {
                    boolean hasPos = buf.readBoolean();
                    BlockPos pos = hasPos ? buf.readBlockPos() : null;
                    return new PhysicsAssembleC2SPacket(pos);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, PhysicsAssembleC2SPacket packet) {
                    buf.writeBoolean(packet.hitPos != null);
                    if (packet.hitPos != null) {
                        buf.writeBlockPos(packet.hitPos);
                    }
                }
            };

    @Override
    public Type<PhysicsAssembleC2SPacket> type() {
        return TYPE;
    }

    /**
     * 服务端处理：将命中位置传递给装配处理器，直接处理或回退到 SubLevel 检测。
     */
    public static void handle(final PhysicsAssembleC2SPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                PhysicsAssembleHandler.handleAssembleSignal(serverPlayer, packet.hitPos());
            }
        });
    }
}
