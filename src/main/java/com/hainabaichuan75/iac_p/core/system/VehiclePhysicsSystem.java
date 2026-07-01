package com.hainabaichuan75.iac_p.core.system;

import com.hainabaichuan75.iac_p.core.part.PartBlockEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;

/**
 * 载具物理 Tick System — Sable 步进频率（~100Hz）。
 * <p>
 * 在 Sable 物理步进前通过 {@link VehicleSystemDispatcher} 调度。
 * 适合：悬挂弹簧力与阻尼、轮胎驱动力与抓地力、引擎扭矩输出。
 * <p>
 * <b>注意</b>：此 System 的调用频率高于逻辑 Tick（20Hz），
 * 且可能在独立物理线程上调用。System 实现应保持无锁/无阻塞。
 */
@FunctionalInterface
public interface VehiclePhysicsSystem {

    /**
     * 每物理步进调用一次（约 100Hz）。
     *
     * @param subLevel  当前 SubLevel（服务端）
     * @param parts     该 SubLevel 内收集到的所有 Part
     * @param handle    刚体句柄，用于施加力/扭矩
     * @param timeStep  本步进的物理时间步长（秒）
     */
    void onPhysicsTick(ServerSubLevel subLevel, List<PartBlockEntity> parts,
                       RigidBodyHandle handle, double timeStep);
}
