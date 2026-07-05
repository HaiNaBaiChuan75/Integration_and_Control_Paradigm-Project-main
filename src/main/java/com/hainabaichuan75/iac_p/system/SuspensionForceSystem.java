package com.hainabaichuan75.iac_p.system;

import com.hainabaichuan75.iac_p.ecs.part.Part;
import com.hainabaichuan75.iac_p.ecs.system.VehiclePhysicsSystem;
import com.hainabaichuan75.iac_p.part.WheelPart;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;

import java.util.List;

/**
 * 悬挂弹簧物理 System —— 根据悬挂压缩量向轮毂施加弹性力。
 * <p>
 * <b>职责</b>：
 * <ol>
 *   <li>读取 {@link WheelPart#getSuspensionCompression()} 和 {@link WheelPart#getSuspensionStiffness()}</li>
 *   <li>计算弹簧力：{@code F = k × x}（胡克定律），方向沿局部 Y 向上</li>
 *   <li>在轮毂世界位置施加冲量到刚体</li>
 * </ol>
 * <p>
 * <b>数据流</b>：
 * <pre>
 * SuspensionSystem (20Hz) → 写入 compression
 *   → SuspensionForceSystem (物理 tick ~100Hz) 读取 compression
 *     → F = stiffness × compression, 方向向上
 *     → ForceTotal.applyImpulseAtPoint(hubWorldPos, impulse)
 * </pre>
 * <p>
 * compression 由 20Hz 逻辑 tick 更新，物理 tick 直接复用最近值。
 * 1-5 物理 tick 的延迟对弹簧力影响可忽略。
 */
public class SuspensionForceSystem implements VehiclePhysicsSystem {

    @Override
    public void onPhysicsTick(ServerSubLevel subLevel, List<? extends Part> parts, RigidBodyHandle handle,
                              double timeStep) {
        ForceTotal forces = new ForceTotal();

        for (Part part : parts) {
            if (part instanceof WheelPart wheel) {
                double compression = wheel.getSuspensionCompression();
                if (compression <= 0.0) continue;

                double stiffness = wheel.getSuspensionStiffness();
                // 弹簧力 = 刚度 × 压缩量（胡克定律），方向向上
                double impulseMag = stiffness * compression * timeStep;

                // 将局部向上方向 (0,1,0) 变换到世界空间
                var worldUp = subLevel.logicalPose().transformNormal(new Vector3d(0, 1, 0));

                // 冲量向量 = 方向 × 冲量大小
                Vector3d impulse = worldUp.mul(impulseMag);

                // 在轮毂位置施加冲量
                var hubPos = wheel.getSuspensionAttachmentInWorld();
                forces.applyImpulseAtPoint(subLevel, new Vector3d(hubPos.x(), hubPos.y(), hubPos.z()), impulse);
            }
        }

        handle.applyForcesAndReset(forces);
    }
}
