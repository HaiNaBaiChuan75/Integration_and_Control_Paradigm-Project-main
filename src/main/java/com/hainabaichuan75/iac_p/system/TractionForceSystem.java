package com.hainabaichuan75.iac_p.system;

import com.hainabaichuan75.iac_p.ecs.part.Part;
import com.hainabaichuan75.iac_p.ecs.system.VehiclePhysicsSystem;
import com.hainabaichuan75.iac_p.part.DriveWheel;
import com.hainabaichuan75.iac_p.part.WheelPart;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

/**
 * 牵引力物理 System —— 根据驱动轮扭矩施加推进力。
 * <p>
 * <b>职责</b>：
 * <ol>
 *   <li>读取 {@link DriveWheel#getTorqueInput()} 和 {@link WheelPart#getRadius()}</li>
 *   <li>计算牵引力：{@code F = τ / r}，方向沿车轮滚动方向</li>
 *   <li>在轮毂世界位置施加冲量到刚体</li>
 * </ol>
 * <p>
 * <b>数据流</b>：
 * <pre>
 * TorqueDistributionSystem (20Hz) → 写入 torqueInput
 *   → TractionForceSystem (物理 tick ~100Hz) 读取 torqueInput
 *     → F = torqueInput / radius, 沿滚动方向
 *     → ForceTotal.applyImpulseAtPoint(hubWorldPos, impulse)
 * </pre>
 * <p>
 * <b>滚动方向</b>：由 {@link WheelPart#getAxialNormal()} 和局部天向的叉积推导。
 * 自动适应车轮转向——当轴向随 SteeringSystem 旋转时，滚动方向同步偏转。
 * <p>
 * 扭矩来自 20Hz 逻辑 tick，物理 tick 直接复用最近值。
 * 暂不包含摩擦圆限幅（后续增加）。
 */
public class TractionForceSystem implements VehiclePhysicsSystem {

    /**
     * 滚动方向与车辆前进方向一致性的最小阈值
     */
    private static final double FWD_ALIGNMENT_THRESHOLD = 0.1;

    @Override
    public void onPhysicsTick(ServerSubLevel subLevel, List<? extends Part> parts, RigidBodyHandle handle,
                              double timeStep) {
        ForceTotal forces = new ForceTotal();

        for (Part part : parts) {
            if (part instanceof DriveWheel wheel) {
                double torque = wheel.getTorqueInput();
                if (torque == 0.0) continue;

                double radius = wheel.getRadius();
                if (radius <= 0.0) continue;

                double forceMag = torque / radius;

                // ── 计算滚动方向（SubLevel 局部空间） ──────────────
                // 滚动方向 = up × axialNormal（叉积，垂直于轴向和天向）
                Vector3dc axial = wheel.getAxialNormal();
                Vector3d rollDir = new Vector3d(0, 1, 0).cross(axial, new Vector3d());

                // 确保滚动方向指向车辆前方 (Z-)
                if (rollDir.dot(Part.FORWARD) < 0) {
                    rollDir.negate();
                }

                // 若滚动方向与前进方向几乎垂直（退化轴向，如轴向指天），
                // 回退到直向前方
                if (Math.abs(rollDir.dot(Part.FORWARD)) < FWD_ALIGNMENT_THRESHOLD) {
                    rollDir.set(Part.FORWARD);
                }

                rollDir.normalize();

                // ── 变换到世界空间并施加冲量 ──────────────────────
                var worldDir = subLevel.logicalPose().transformNormal(rollDir);
                Vector3d impulse = worldDir.mul(forceMag * timeStep);

                var hubPos = wheel.getSuspensionAttachmentInWorld();
                forces.applyImpulseAtPoint(subLevel, new Vector3d(hubPos.x(), hubPos.y(), hubPos.z()), impulse);
            }
        }

        handle.applyForcesAndReset(forces);
    }
}
