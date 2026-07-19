package com.hainabaichuan75.iac_p.ecs.system;

import com.hainabaichuan75.iac_p.ecs.part.Part;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;

/**
 * 载具物理 System——Sable 步进频率（~100Hz）。
 * <p>
 * 在 Sable 物理步进前由
 * {@link com.hainabaichuan75.iac_p.ecs.dispatch.VehicleSystemDispatcher VehicleSystemDispatcher} 调度。
 * 适合：悬挂弹簧力与阻尼、轮胎驱动力与抓地力、引擎扭矩输出。
 * <p>
 */
@Deprecated(since = "1.0", forRemoval = true)
@FunctionalInterface
public interface VehiclePhysicsSystem {

    /**
     * 每物理步进前调用一次（约 100Hz，取决于 Sable 配置）。
     *
     * @param subLevel 当前 SubLevel（服务端），isRemoved() 已预先过滤
     * @param parts    该 SubLevel 内收集到的所有 Part，已过滤空列表
     * @param handle   刚体句柄，用于施加力/扭矩，不可为 null
     * @param timeStep 本步进的物理时间步长（秒）
     */
    void onPhysicsTick(ServerSubLevel subLevel, List<? extends Part> parts,
                       RigidBodyHandle handle, double timeStep);
}
