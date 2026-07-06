package com.hainabaichuan75.iac_p.network.packets;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.events.ServerMountHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 下车请求数据包（客户端 → 服务器）。
 * <p>
 * 客户端按下下车键（默认 Shift）时发送此包，服务端响应执行下车逻辑。
 * 下车不依赖右键射线检测——无论在什么位置，只要处于骑乘状态即可下车。
 */
public record DismountC2SPacket() implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(IACP.MODID, "dismount");
    public static final Type<DismountC2SPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, DismountC2SPacket> STREAM_CODEC =
            StreamCodec.unit(new DismountC2SPacket());

    @Override
    public Type<DismountC2SPacket> type() {
        return TYPE;
    }

    /**
     * 服务端处理：执行下车逻辑。
     */
    public static void handle(final DismountC2SPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                ServerMountHandler.handleDismount(serverPlayer);
            }
        });
    }
}
