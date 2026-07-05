package com.hainabaichuan75.iac_p.system;

import com.hainabaichuan75.iac_p.ecs.part.Part;
import com.hainabaichuan75.iac_p.ecs.system.VehicleTickSystem;
import com.hainabaichuan75.iac_p.part.AimingMount;
import com.hainabaichuan75.iac_p.part.Controller;
import com.hainabaichuan75.iac_p.part.field.YawPitch;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

/**
 * 武器瞄准 System —— 将控制器的瞄准目标解算到各武器挂载点的角度。
 * <p>
 * <b>职责</b>：
 * <ol>
 *   <li>从 {@link Controller} 读取世界坐标瞄准目标点</li>
 *   <li>将目标点变换到每个 {@link AimingMount} 的局部空间</li>
 *   <li>计算 yaw/pitch 角度并写入 {@code mount.setAngles()}</li>
 * </ol>
 * <p>
 * <b>数据流</b>：
 * <pre>
 * 网络包 → Controller.getAimTarget() 字段
 *   → WeaponAimSystem.onTick()
 *     → partLogicalPose().transformPositionInverse(worldPos) → local 空间方向向量
 *     → YawPitch.from(localDir) → 角度
 *     → AimingMount.setAngles(angles)
 * </pre>
 * <p>
 * System 不介入网络层——目标点由网络包写入 Controller 字段后，System 只读不写。
 * Part 自身的 {@code sable$tick()} 做角度平滑插值或伺服马达更新。
 * <p>
 * <b>多控制器选举</b>：按 {@code BlockPos} 字典序（x→y→z）选最小者。
 */
public class WeaponAimSystem implements VehicleTickSystem {

    @Override
    public void onTick(ServerSubLevel subLevel, List<? extends Part> parts) {
        // ================================================================
        //  1. 找到主控制器输入源
        // ================================================================
        Controller ctrl = ControllerElection.findPrimary(parts);
        if (ctrl == null) return;

        Vector3dc aimTarget = ctrl.getAimTarget();
        if (aimTarget == null) return;

        // ================================================================
        //  2. 遍历所有武器挂载点，计算角度
        // ================================================================
        for (Part part : parts) {
            if (part instanceof AimingMount mount) {
                // 将世界坐标瞄准点变换到 mount 局部空间
                // transformPositionInverse 将世界 → Part-local 坐标
                Vector3d localDir = mount.partLogicalPose().transformPositionInverse(new Vector3d(aimTarget));

                // 计算方向角度 —— YawPitch.from() 使用 atan2(-x, -z) 约定
                // 0° = 前方 (z-)，正值 = 右转 (CW+)
                YawPitch angles = YawPitch.from(localDir);
                mount.setAngles(angles);
            }
        }
    }

}
