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
 * 悬挂弹簧物理 System (V2) —— 根据悬挂压缩量向轮毂施加弹性力。
 * <p>
 * <b>与 V1 区别</b>：弹簧方向使用 {@link WheelDef#suspensionDirection()}（可配置），
 * 而非硬编码的天向，使倒置或倾斜悬挂也能正确工作。
 * <p>
 * <b>数据流</b>：
 * <pre>
 * SuspensionSystem (20Hz) → 写入 WheelState.suspensionCompression
 *   → SuspensionForceSystem (物理 tick ~100Hz)
 *     → F = stiffness × compression，沿 suspensionDirection
 *     → ForceTotal.applyImpulseAtPoint()
 * </pre>
 */
public class SuspensionForceSystem implements PhysicsSystem {

    @Override
    public void onPhysicsTick(@NotNull ServerSubLevel subLevel, @NotNull List<? extends Part> parts,
                              @NotNull RigidBodyHandle handle, double timeStep) {
        ForceTotal forces = new ForceTotal();

        for (var entry : View.find(parts, WheelDef.KEY, WheelState.KEY)) {
            if (!(entry instanceof Views2(var defView, var stateView))) continue;
            var wd = defView.get();
            double compression = stateView.get().suspensionCompression();
            if (compression <= 0.0) continue;

            // 弹簧力 = 刚度 × 压缩量（胡克定律）
            double impulseMag = wd.suspensionStiffness() * compression * timeStep;

            // 局部悬挂方向 → 世界空间
            Vector3d worldSuspDir = subLevel.logicalPose().transformNormal(new Vector3d(wd.suspensionDirection()));
            Vector3d impulse = worldSuspDir.mul(impulseMag);

            // 轮毂 SubLevel 局部坐标
            Vector3d localPos = JOMLConversion.atCenterOf(stateView.part().getBlockEntity().getBlockPos());
            localPos.add(wd.mountPoint().x(), wd.mountPoint().y(), wd.mountPoint().z());

            forces.applyImpulseAtPoint(subLevel, localPos, impulse);
        }

        handle.applyForcesAndReset(forces);
    }
}
