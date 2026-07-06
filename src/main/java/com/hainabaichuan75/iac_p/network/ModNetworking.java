package com.hainabaichuan75.iac_p.network;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.network.packets.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 网络管理器 —— 注册所有自定义数据包并处理收发。
 */
@EventBusSubscriber(modid = IACP.MODID)
public class ModNetworking {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1.0");
        registrar.playToClient(
                MountedStateS2CPacket.TYPE,
                MountedStateS2CPacket.STREAM_CODEC,
                MountedStateS2CPacket::handle
        );
        registrar.playToServer(
                DismountC2SPacket.TYPE,
                DismountC2SPacket.STREAM_CODEC,
                DismountC2SPacket::handle
        );
        registrar.playToServer(
                VehicleKeyConfigC2SPacket.TYPE,
                VehicleKeyConfigC2SPacket.STREAM_CODEC,
                VehicleKeyConfigC2SPacket::handle
        );
        registrar.playToServer(
                VehicleControlC2SPacket.TYPE,
                VehicleControlC2SPacket.STREAM_CODEC,
                VehicleControlC2SPacket::handle
        );
        registrar.playToServer(
                TireConfigC2SPacket.TYPE,
                TireConfigC2SPacket.STREAM_CODEC,
                TireConfigC2SPacket::handle
        );
        registrar.playToServer(
                GrindstoneConfigC2SPacket.TYPE,
                GrindstoneConfigC2SPacket.CODEC,
                GrindstoneConfigC2SPacket::handle
        );
        registrar.playToServer(
                AnchorConfigC2SPacket.TYPE,
                AnchorConfigC2SPacket.CODEC,
                AnchorConfigC2SPacket::handle
        );
        registrar.playToClient(
                AnchorDataS2CPacket.TYPE,
                AnchorDataS2CPacket.CODEC,
                AnchorDataS2CPacket::handle
        );
        registrar.playToServer(
                DebugGearToggleC2SPacket.TYPE,
                DebugGearToggleC2SPacket.STREAM_CODEC,
                DebugGearToggleC2SPacket::handle
        );
        registrar.playToServer(
                DebugSwivelToggleC2SPacket.TYPE,
                DebugSwivelToggleC2SPacket.STREAM_CODEC,
                DebugSwivelToggleC2SPacket::handle
        );
        registrar.playToServer(
                WeaponFireC2SPacket.TYPE,
                WeaponFireC2SPacket.STREAM_CODEC,
                WeaponFireC2SPacket::handle
        );
        registrar.playToClient(
                WeaponSoundS2CPacket.TYPE,
                WeaponSoundS2CPacket.STREAM_CODEC,
                WeaponSoundS2CPacket::handle
        );
        registrar.playToServer(
                PhysicsAssembleC2SPacket.TYPE,
                PhysicsAssembleC2SPacket.STREAM_CODEC,
                PhysicsAssembleC2SPacket::handle
        );

    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
