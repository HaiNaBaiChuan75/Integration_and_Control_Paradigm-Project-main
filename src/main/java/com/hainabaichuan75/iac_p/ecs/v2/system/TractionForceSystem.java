package com.hainabaichuan75.iac_p.ecs.v2.system;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View.Views2;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import com.hainabaichuan75.iac_p.ecs.v2.api.system.PhysicsSystem;
import com.hainabaichuan75.iac_p.ecs.v2.component.WheelDef;
import com.hainabaichuan75.iac_p.ecs.v2.component.WheelState;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;

import java.util.List;

/**
 * 牵引力物理 System (V2) —— 根据驱动轮扭矩施加推进力。
 * <p>
 * <b>职责</b>：
 * <ol>
 *   <li>读取 {@link WheelState#torque} 和 {@link WheelDef#radius()}</li>
 *   <li>计算牵引力：{@code F = τ / r}，方向沿车轮滚动方向</li>
 *   <li>在轮毂位置施加冲量到刚体</li>
 * </ol>
 * <p>
 * <b>数据流</b>：
 * <pre>
 * TorqueDistributionSystem (20Hz) → 写入 WheelState.torque
 *   → TractionForceSystem (物理 tick ~100Hz)
 *     → F = torque / radius, 沿滚动方向
 *     → ForceTotal.applyImpulseAtPoint()
 * </pre>
 * <p>
 * <b>滚动方向</b>：由 {@link WheelDef#mountDirection()} 和局部天向的叉积推导。
 * 暂不包含摩擦圆限幅（后续增加）。
 */
public class TractionForceSystem implements PhysicsSystem {

    /**
     * 滚动方向与车辆前进方向一致性的最小阈值
     */
    private static final double FWD_ALIGNMENT_THRESHOLD = 0.1;

    /**
     * SubLevel 局部前方（z-）
     */
    private static final Vector3d LOCAL_FORWARD = new Vector3d(0, 0, -1);

    @Override
    public void onPhysicsTick(@NotNull ServerSubLevel subLevel, @NotNull List<? extends Part> parts,
                              @NotNull RigidBodyHandle handle, double timeStep) {
        ForceTotal forces = new ForceTotal();

        for (var entry : View.find(parts, WheelDef.KEY, WheelState.KEY)) {
            if (!(entry instanceof Views2(var defView, var stateView))) continue;
            var wd = defView.get();
            double torque = stateView.get().torque();
            if (torque == 0.0) continue;
            if (wd.radius() <= 0.0) continue;

            double forceMag = torque / wd.radius();

            // ── 滚动方向（SubLevel 局部空间） ──────────────────
            // rollDir = up × axial（右手定则，垂直于轴向和天向）
            Vector3d rollDir = new Vector3d(0, 1, 0).cross(wd.mountDirection(), new Vector3d());

            // 对齐到车辆前方 (z-)
            if (rollDir.dot(LOCAL_FORWARD) < 0) {
                rollDir.negate();
            }

            // 退化轴向回退（轴向指天时叉积近零）
            if (Math.abs(rollDir.dot(LOCAL_FORWARD)) < FWD_ALIGNMENT_THRESHOLD) {
                rollDir.set(LOCAL_FORWARD);
            }

            rollDir.normalize();

            // ── 变换到世界空间并施加冲量 ──────────────────────
            Vector3d worldDir = subLevel.logicalPose().transformNormal(rollDir);
            Vector3d impulse = worldDir.mul(-forceMag * timeStep);

            // 轮毂 SubLevel 局部坐标
            Vector3d localPos = JOMLConversion.atCenterOf(stateView.part().getBlockEntity().getBlockPos());
            localPos.add(wd.mountPoint().x(), wd.mountPoint().y(), wd.mountPoint().z());

            forces.applyImpulseAtPoint(subLevel, localPos, impulse);
        }

        handle.applyForcesAndReset(forces);
    }
}
