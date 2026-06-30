package com.hainabaichuan75.iac_p.network.packets;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.events.PhysicsAssembleHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 物理装配触发信号（客户端 → 服务器，无负载）。
 * <p>
 * 玩家按住 Ctrl + 鼠标右键时发送此信号。
 * 服务端从玩家眼睛重新发射 3 格射线（含 SubLevel 感知），
 * 命中驾驶舱则装配/拆解。
 */
public record PhysicsAssembleC2SPacket() implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(IACP.MODID, "physics_assemble");
    public static final Type<PhysicsAssembleC2SPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, PhysicsAssembleC2SPacket> STREAM_CODEC =
            StreamCodec.unit(new PhysicsAssembleC2SPacket());

    @Override
    public Type<PhysicsAssembleC2SPacket> type() {
        return TYPE;
    }

    /**
     * 服务端处理：从玩家眼睛发射射线，尝试装配或拆解。
     */
    public static void handle(final PhysicsAssembleC2SPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                PhysicsAssembleHandler.handleAssembleSignal(serverPlayer);
            }
        });
    }
}
