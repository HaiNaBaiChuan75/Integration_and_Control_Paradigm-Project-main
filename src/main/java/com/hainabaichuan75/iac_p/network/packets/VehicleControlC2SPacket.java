package com.hainabaichuan75.iac_p.network.packets;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.block.cockpit.CockpitBlockEntity;
import com.hainabaichuan75.iac_p.block.suspension_test.SuspensionTestBlockEntity;
import com.hainabaichuan75.iac_p.events.PlayerMountTracker;
import com.hainabaichuan75.iac_p.events.SubLevelScanner;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 载具实时控制输入数据包（客户端 → 服务器）。
 * <p>
 * 当玩家骑乘载具时，客户端每 tick 扫描载具所在 SubLevel 内的所有悬挂测试方块，
 * 检查每个方块配置的自定义按键是否被按下，然后将此包发送到服务端。
 * <p>
 * 设计原则：客户端只检测按键按下/抬起状态，服务端执行物理动作。
 * 仅在有状态变化时发送，以减少网络开销。
 */
public class VehicleControlC2SPacket implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            IACP.MODID, "vehicle_control"
    );
    public static final Type<VehicleControlC2SPacket> TYPE = new Type<>(ID);

    private final List<Entry> entries;

    /**
     * 原始油门方向：+1=踩油门(W), -1=松油门(S), 0=无输入。
     * 与悬挂解耦的直接控制信号，不经过智能键映射。
     */
    private final int throttleDirection;

    /**
     * 是否有有效的瞄准目标
     */
    private final boolean hasAim;
    /**
     * 瞄准目标世界坐标
     */
    private final float aimX, aimY, aimZ;

    public record Entry(BlockPos blockPos, boolean forward, boolean backward, boolean left, boolean right,
                        boolean brake) {}

    public VehicleControlC2SPacket(List<Entry> entries, int throttleDirection, boolean hasAim, float aimX, float aimY
            , float aimZ) {
        this.entries = entries;
        this.throttleDirection = throttleDirection;
        this.hasAim = hasAim;
        this.aimX = aimX;
        this.aimY = aimY;
        this.aimZ = aimZ;
    }

    public List<Entry> entries() { return entries; }
    public int throttleDirection() { return throttleDirection;}

    public boolean hasAim() {return hasAim;}

    public float aimX() {return aimX;}

    public float aimY() {return aimY;}

    public float aimZ() { return aimZ; }

    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleControlC2SPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VehicleControlC2SPacket decode(RegistryFriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<Entry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        entries.add(new Entry(
                                buf.readBlockPos(),
                                buf.readBoolean(),
                                buf.readBoolean(),
                                buf.readBoolean(),
                                buf.readBoolean(),
                                buf.readBoolean()
                        ));
                    }
                    int throttleDir = buf.readVarInt();
                    boolean hasAim = buf.readBoolean();
                    float aimX = hasAim ? buf.readFloat() : 0;
                    float aimY = hasAim ? buf.readFloat() : 0;
                    float aimZ = hasAim ? buf.readFloat() : 0;
                    return new VehicleControlC2SPacket(entries, throttleDir, hasAim, aimX, aimY, aimZ);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, VehicleControlC2SPacket packet) {
                    buf.writeVarInt(packet.entries.size());
                    for (Entry e : packet.entries) {
                        buf.writeBlockPos(e.blockPos);
                        buf.writeBoolean(e.forward);
                        buf.writeBoolean(e.backward);
                        buf.writeBoolean(e.left);
                        buf.writeBoolean(e.right);
                        buf.writeBoolean(e.brake);
                    }
                    buf.writeVarInt(packet.throttleDirection);
                    buf.writeBoolean(packet.hasAim);
                    if (packet.hasAim) {
                        buf.writeFloat(packet.aimX);
                        buf.writeFloat(packet.aimY);
                        buf.writeFloat(packet.aimZ);
                    }
                }
            };

    @Override
    public Type<VehicleControlC2SPacket> type() {
        return TYPE;
    }

    /**
     * 服务端处理：将每个方块的控制输入写入对应的 BlockEntity。
     * 仅在玩家处于骑乘状态时处理。
     */
    public static void handle(final VehicleControlC2SPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                if (!PlayerMountTracker.isMounted(serverPlayer)) return;

                ServerLevel level = serverPlayer.serverLevel();
                for (Entry entry : packet.entries) {
                    if (level.getBlockEntity(entry.blockPos) instanceof SuspensionTestBlockEntity be) {
                        be.applyControlInput(
                                entry.forward,
                                entry.backward,
                                entry.left,
                                entry.right,
                                entry.brake
                        );
                    }
                }

                // ── 控制信号转发到座舱的 PlayerCommandReceiver ──
                var mountData = PlayerMountTracker.getMountData(serverPlayer);
                if (mountData != null) {
                    CockpitBlockEntity cockpit = findCockpitInSubLevel(level, mountData.subLevelUUID());
                    if (cockpit != null) {
                        // 油门 + 转向：Controller 约定 z- = 前, z+ = 后
                        // throttleDirection +1 = W(前进) → z = -1
                        cockpit.setMovementIntent(new org.joml.Vector3d(0, 0, -packet.throttleDirection));
                        // 刹车：任一悬挂刹车即整体刹车
                        boolean anyBrake = false;
                        for (Entry entry : packet.entries) {
                            if (entry.brake) {
                                anyBrake = true;
                                break;
                            }
                        }
                        cockpit.setBrake(anyBrake);
                        // 瞄准目标
                        if (packet.hasAim) {
                            cockpit.setAimTarget(new org.joml.Vector3d(packet.aimX, packet.aimY, packet.aimZ));
                        } else {
                            cockpit.setAimTarget(null);
                        }
                    }
                }
            }
        });
    }

    // ====================================================================
    //  工具：在 SubLevel 内找驾驶舱
    // ====================================================================

    /**
     * 在指定 SubLevel 中查找驾驶舱 BlockEntity。
     * 使用 SubLevelScanner 统一遍历。
     */
    private static CockpitBlockEntity findCockpitInSubLevel(ServerLevel level, UUID subLevelUUID) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        SubLevel subLevel = container.getSubLevel(subLevelUUID);
        if (subLevel == null) return null;

        CockpitBlockEntity[] result = {null};
        SubLevelScanner.forEachBlock(subLevel, level, (worldPos, state, be) -> {
            if (result[0] != null) return;
            if (be instanceof CockpitBlockEntity cockpit) {
                result[0] = cockpit;
            }
        });
        return result[0];
    }
}
