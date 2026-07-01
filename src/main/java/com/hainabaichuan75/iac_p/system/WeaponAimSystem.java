package com.hainabaichuan75.iac_p.system;

import com.hainabaichuan75.iac_p.ecs.part.*;
import com.hainabaichuan75.iac_p.ecs.system.VehicleTickSystem;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;

import java.util.List;

/**
 * 武器瞄准 System —— 将控制器的瞄准目标点解算为各武器的 yaw/pitch 角度。
 * <p>
 * 来源：{@link com.hainabaichuan75.iac_p.block.shotgun.ShotgunAimController} 和
 * {@link com.hainabaichuan75.iac_p.block.machine_gun.MachineGunAimController} 中的瞄准解算。
 * <p>
 * <b>数据流</b>：
 * <ol>
 *   <li>查找 {@link Controller} 获取瞄准目标点世界坐标</li>
 *   <li>遍历 {@link WeaponMount}，将世界坐标转换到 Part 局部空间</li>
 *   <li>计算 yaw/pitch 角度</li>
 *   <li>写入 {@link WeaponMount#setTargetYaw(double)} / {@link WeaponMount#setTargetPitch(double)}</li>
 * </ol>
 * <p>
 * <b>注意</b>：该 System 运行在逻辑 Tick（20Hz），比原先 AimController 的"收到网络包立即执行"
 * 模式多 0~50ms 延迟。若需要更低延迟的伺服响应，网络包 handler 可以直接调用 Part
 * 的 {@code driveImmediate()} 方法做超前更新，System 作为冗余保持。
 */
public class WeaponAimSystem implements VehicleTickSystem {

    @Override
    public void onTick(@NotNull ServerSubLevel subLevel, @NotNull List<PartBlockEntity> parts) {
        // 1. 找到主控输入源
        Controller ctrl = findPrimaryController(parts);
        if (ctrl == null) return;

        var aimTarget = ctrl.getAimTarget();
        if (aimTarget == null) return;

        // 2. 遍历武器挂载点，计算瞄准角度
        for (PartBlockEntity part : parts) {
            if (!(part instanceof WeaponMount mount)) continue;
            if (!(part instanceof PartBlockEntity pbe)) continue;

            try {
                // 坐标转换：将世界坐标目标点转换到 Part 局部空间
                Vector3d targetLocal = pbe.partLogicalPose()
                        .transformPositionInverse(new Vector3d(aimTarget));

                // 角度解算（Minecraft z- = 前方，x+ = 右方）
                double yaw = Math.toDegrees(Math.atan2(targetLocal.x(), targetLocal.z()));
                double pitch = Math.toDegrees(Math.atan2(
                        -targetLocal.y(),
                        Math.sqrt(targetLocal.x() * targetLocal.x() + targetLocal.z() * targetLocal.z())
                ));

                // 写入目标角度（Part 内部只做平滑插值）
                mount.setTargetYaw(yaw);
                mount.setTargetPitch(pitch);
            } catch (Exception e) {
                // 单个武器瞄准异常不影响其他武器
                continue;
            }
        }
    }

    /**
     * 从 parts 列表中查找第一个主控 Controller。
     */
    private static Controller findPrimaryController(List<PartBlockEntity> parts) {
        for (PartBlockEntity part : parts) {
            if (part instanceof Controller ctrl) {
                return ctrl;
            }
        }
        return null;
    }
}
