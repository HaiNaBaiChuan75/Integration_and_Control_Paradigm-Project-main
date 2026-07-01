package com.hainabaichuan75.iac_p.network.packets;

import java.util.UUID;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.core.part.PartQuery;
import com.hainabaichuan75.iac_p.content.blocks.machine_gun.MachineGunAimController;
import com.hainabaichuan75.iac_p.content.blocks.machine_gun.MachineGunBaseBlockEntity;
import com.hainabaichuan75.iac_p.content.blocks.shotgun.ShotgunBaseBlockEntity;
import com.hainabaichuan75.iac_p.content.blocks.turret.TurretTestBlockEntity;
import com.hainabaichuan75.iac_p.events.PlayerMountTracker;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 机枪瞄准数据包（客户端 → 服务器）。
 * <p>
 * 客户端发送：
 * <ul>
 * <li>命中点世界坐标 (hitX, hitY, hitZ) — 用于旧机枪/霰弹枪的局部空间角度计算</li>
 * <li>摄像机实际朝向 (cameraYaw, cameraPitch) — 用于 TurretTest 平行模式</li>
 * </ul>
 * <p>
 * <b>摄像机朝向说明</b>：在轨道摄像机模式下，{@code player.getYRot()} 控制的是
 * 摄像机在球面上的<b>位置</b>而非实际朝向。必须从 {@code Camera.getYRot()/getXRot()} 获取被
 * CameraMixin 强制设定后的实际朝向角度。
 */
public record MachineGunTargetC2SPacket(
        float hitX,
        float hitY,
        float hitZ,
        float cameraYaw,
        float cameraPitch
        ) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(IACP.MODID, "machine_gun_target");
    public static final Type<MachineGunTargetC2SPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineGunTargetC2SPacket> STREAM_CODEC
            = new StreamCodec<>() {
        @Override
        public MachineGunTargetC2SPacket decode(RegistryFriendlyByteBuf buf) {
            return new MachineGunTargetC2SPacket(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, MachineGunTargetC2SPacket packet) {
            buf.writeFloat(packet.hitX);
            buf.writeFloat(packet.hitY);
            buf.writeFloat(packet.hitZ);
            buf.writeFloat(packet.cameraYaw);
            buf.writeFloat(packet.cameraPitch);
        }
    };

    @Override
    public Type<MachineGunTargetC2SPacket> type() {
        return TYPE;
    }

    // ==================================================================
    //  工具：世界 → 载具局部空间变换
    // ==================================================================
    /**
     * 将世界坐标变换到载具局部空间。
     *
     * @param vPose 载具 SubLevel 位姿
     * @param vOrientInv 预计算的载具旋转逆四元数
     * @param wx 世界 X
     * @param wy 世界 Y
     * @param wz 世界 Z
     * @return 载具局部空间中的 Vector3d
     */
    private static Vector3d worldToLocal(Pose3dc vPose,
            Quaterniond vOrientInv, double wx, double wy, double wz) {
        return new Vector3d(
                wx - vPose.position().x(),
                wy - vPose.position().y(),
                wz - vPose.position().z()
        ).rotate(vOrientInv);
    }

    /**
     * 驱动单座机枪瞄准目标点 —— 在载具局部空间中计算。
     * <p>
     * 全部坐标变换到载具局部空间后计算，结果天然是载具相对角度：
     * <ul>
     * <li>方向机 Yaw = {@code -atan2(dx_local, dz_local)} ← 局部 XZ 平面俯视投影</li>
     * <li>高低机 Pitch = {@code atan2(dy_local, sqrt(dx²+dz²))} ← 局部侧面投影</li>
     * </ul>
     * 载具翻转时，局部空间的「水平面」随载具旋转，角度计算始终正确。
     */
    private static void driveTurretAtTarget(MachineGunBaseBlockEntity tb,
            Vector3d hitLocal, Vector3d turretLocal) {
        double dx = hitLocal.x - turretLocal.x;
        double dy = hitLocal.y - turretLocal.y;
        double dz = hitLocal.z - turretLocal.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        // ---- 方向机：载具局部 XZ 平面俯视投影 ----
        float turretYaw = horiz < 0.001 ? 0f
                : (float) -Math.toDegrees(Math.atan2(dx, dz));

        // ---- 高低机：载具局部侧面投影 ----
        float turretPitch = (float) Math.toDegrees(Math.atan2(dy, Math.max(horiz, 0.001)));

        MachineGunAimController.driveAnglesImmediate(tb, turretYaw, turretPitch);
    }

    /**
     * 驱动单座霰弹枪瞄准目标点 —— 与机枪相同的局部空间计算。
     */
    private static void driveShotgunAtTarget(ShotgunBaseBlockEntity sb,
            Vector3d hitLocal, Vector3d weaponLocal) {
        double dx = hitLocal.x - weaponLocal.x;
        double dy = hitLocal.y - weaponLocal.y;
        double dz = hitLocal.z - weaponLocal.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        float yaw = horiz < 0.001 ? 0f
                : (float) -Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(dy, Math.max(horiz, 0.001)));

        com.hainabaichuan75.iac_p.content.blocks.shotgun.ShotgunAimController.driveAnglesImmediate(sb, yaw, pitch);
    }

    /**
     * 驱动 TurretTest 瞄准目标点 —— 在载具局部空间中计算角度。
     * <p>
     * 复用旧架构（机枪/霰弹枪）的 worldToLocal + atan2 方案：
     * <ol>
     * <li>{@code hitLocal} 是命中点在载具局部空间中的坐标（相对载具原点的小数值）</li>
     * <li>武器位置相对于载具原点的偏移很小（几格），远小于目标距离（几十~几百格）， 直接用 {@code hitLocal}
     * 作为从武器到目标的方向向量精度足够</li>
     * <li>在载具局部空间中 {@code atan2(dx, dz)} 得到炮塔相对角度（GeckoLib 使用 CCW+，与 atan2 一致），
     * 天然排除载具偏航影响</li>
     * </ol>
     *
     * @param tt 炮塔 BlockEntity
     * @param hitLocal 命中点在载具局部空间中的坐标
     */
    private static void driveTurretTestAtTarget(TurretTestBlockEntity tt,
            Vector3d hitLocal) {
        // 直接用 hitLocal 作为从载具原点到目标的方向向量
        // （武器相对载具原点的偏移仅几格，忽略不计）
        double horiz = Math.sqrt(hitLocal.x * hitLocal.x + hitLocal.z * hitLocal.z);

        // ---- 方向机：载具局部 XZ 平面俯视投影 ----
        // 注意：GeckoLib 的 bone.setRotY() 使用标准右手系（CCW+），
        // 与 MC 的 CW+ 约定相反。因此直接使用 atan2（CCW+）不加负号。
        // 模型默认朝向 Z-（与 atan2 的 Z+ 零位相反），故取反向量即等效 +180°。
        float turretYaw = horiz < 0.001 ? 0f
                : (float) Math.toDegrees(Math.atan2(-hitLocal.x, -hitLocal.z));

        // ---- 高低机：载具局部侧面投影 ----
        float turretPitch = (float) Math.toDegrees(Math.atan2(hitLocal.y, Math.max(horiz, 0.001)));

        tt.driveImmediate(turretYaw, turretPitch);
    }

    /**
     * 服务端处理：将命中点 + 每座机枪坐标变换到载具局部空间后计算角度。
     * <p>
     * 载具局部空间计算的优势：
     * <ul>
     * <li>角度天然是载具相对角度，无需手动减载具偏航</li>
     * <li>载具翻转（上下颠倒、侧翻）时，局部「水平面」跟随载具，角度计算正确</li>
     * <li>方向机只看局部 XZ 平面，高低机只看局部侧面投影，互不干扰</li>
     * </ul>
     */
    public static void handle(final MachineGunTargetC2SPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!PlayerMountTracker.isMounted(player)) {
                return;
            }

            ServerLevel level = player.serverLevel();
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                return;
            }

            var mountData = PlayerMountTracker.getMountData(player);
            if (mountData == null) {
                return;
            }

            UUID vehicleUUID = mountData.subLevelUUID();
            float hitX = packet.hitX;
            float hitY = packet.hitY;
            float hitZ = packet.hitZ;

            SubLevel vehicleSL = container.getSubLevel(vehicleUUID);
            if (vehicleSL == null || vehicleSL.isRemoved()) {
                return;
            }

            // 预计算载具位姿和逆四元数
            var vPose = vehicleSL.logicalPose();
            if (vPose == null) {
                return;
            }
            var vOrientInv = new Quaterniond(vPose.orientation()).conjugate();

            //  换命中点到载具局部空间（只需做一次）
            var hitLocal = worldToLocal(vPose, vOrientInv, hitX, hitY, hitZ);

            //  --- 通过 PartQuery 实时扫描 ----
            // 机枪（MachineGun）
            var machineGuns = PartQuery.findPartsByUUID(level, vehicleUUID, MachineGunBaseBlockEntity.class);
            for (MachineGunBaseBlockEntity tb : machineGuns) {
                if (!tb.isAssembled()) {
                    continue;
                }
                SubLevel gsSL = container.getSubLevel(tb.getGrindstoneSubLevelId());
                if (gsSL == null || gsSL.isRemoved()) {
                    continue;
                }
                var gsPose = gsSL.logicalPose();
                if (gsPose == null) {
                    continue;
                }
                var turretLocal = worldToLocal(vPose, vOrientInv,
                        gsPose.position().x(), gsPose.position().y(), gsPose.position().z());
                driveTurretAtTarget(tb, hitLocal, turretLocal);
            }

            // TurretTest（Crossout 风格炮塔测试块）
            var turretTests = PartQuery.findPartsByUUID(level, vehicleUUID, TurretTestBlockEntity.class);
            for (TurretTestBlockEntity tt : turretTests) {
                // 直接用 hitLocal（命中点在载具局部空间中的坐标）计算方向。
                // 不引入 weaponLocal 的原因是 plot 底层坐标（大数目）与 hitWorld
                // （玩家附近坐标）相减后旋转，会产生数值敏感的方向抖动。
                // 武器相对载具原点的偏移量仅几格，远小于目标距离，忽略不计。
                driveTurretTestAtTarget(tt, hitLocal);
            }

            // 霰弹枪（Shotgun）
            var shotguns = PartQuery.findPartsByUUID(level, vehicleUUID, ShotgunBaseBlockEntity.class);
            for (ShotgunBaseBlockEntity sb : shotguns) {
                if (!sb.isAssembled()) {
                    continue;
                }
                SubLevel gsSL = container.getSubLevel(sb.getGrindstoneSubLevelId());
                if (gsSL == null || gsSL.isRemoved()) {
                    continue;
                }
                var gsPose = gsSL.logicalPose();
                if (gsPose == null) {
                    continue;
                }
                var weaponLocal = worldToLocal(vPose, vOrientInv,
                        gsPose.position().x(), gsPose.position().y(), gsPose.position().z());
                driveShotgunAtTarget(sb, hitLocal, weaponLocal);
            }

            // 如果扫描找到武器条目，跳过回退扫描
            if (!machineGuns.isEmpty() || !turretTests.isEmpty() || !shotguns.isEmpty()) {
                return;
            }

            // ---- 回退：chunk 扫描（用地毯位置近似机枪位置） ----
            LevelPlot plot = vehicleSL.getPlot();
            if (plot == null) {
                return;
            }

            for (PlotChunkHolder chunk : plot.getLoadedChunks()) {
                var localBounds = chunk.getBoundingBox();
                if (localBounds == null || localBounds == BoundingBox3i.EMPTY) {
                    continue;
                }
                int cMinX = chunk.getPos().getMinBlockX();
                int cMinZ = chunk.getPos().getMinBlockZ();
                for (int x = localBounds.minX(); x <= localBounds.maxX(); x++) {
                    for (int y = localBounds.minY(); y <= localBounds.maxY(); y++) {
                        for (int z = localBounds.minZ(); z <= localBounds.maxZ(); z++) {
                            BlockPos wp = new BlockPos(x + cMinX, y, z + cMinZ);
                            BlockEntity be = level.getBlockEntity(wp);
                            if (be instanceof MachineGunBaseBlockEntity tb) {
                                if (tb.isAssembled()) {
                                    var weaponLocal = worldToLocal(vPose, vOrientInv,
                                            wp.getX() + 0.5, wp.getY() + 0.5, wp.getZ() + 0.5);
                                    driveTurretAtTarget(tb, hitLocal, weaponLocal);
                                }
                            } else if (be instanceof ShotgunBaseBlockEntity sb) {
                                if (sb.isAssembled()) {
                                    var weaponLocal = worldToLocal(vPose, vOrientInv,
                                            wp.getX() + 0.5, wp.getY() + 0.5, wp.getZ() + 0.5);
                                    driveShotgunAtTarget(sb, hitLocal, weaponLocal);
                                }
                            }
                        }
                    }
                }
            }
        });
    }
}
