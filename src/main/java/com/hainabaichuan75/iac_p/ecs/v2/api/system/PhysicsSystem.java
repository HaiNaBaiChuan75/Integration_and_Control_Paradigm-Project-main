package com.hainabaichuan75.iac_p.ecs.v2.api.system;

import com.hainabaichuan75.iac_p.ecs.v2.api.dispatch.V2SystemDispatcher;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.List;

/**
 * 载具物理 System——ECS 中的 <b>系统（System）</b>，Sable 步进频率（~100Hz）。
 * <p>
 * 在 Sable 物理步进前由
 * {@link V2SystemDispatcher} 调度。
 * 适合：悬挂弹簧力与阻尼、轮胎驱动力与抓地力、引擎扭矩输出。
 * <p>
 * <b>约束</b>：System 必须保持无状态——运行时数据保留在 Part 或 SubLevel 上，
 * 不在 System 内部持有可变字段。
 * <p>
 * <b>与 V1 的关系</b>：此接口操作 {@link Part}（v2），
 * 与操作 {@code ecs.part.Part} 的 {@code VehiclePhysicsSystem} 并行存在，
 * 两者互不干扰。
 */
@Deprecated(since = "1.0", forRemoval = true)
@FunctionalInterface
public interface PhysicsSystem {

    /**
     * 每物理步进前调用一次（约 100Hz，取决于 Sable 配置）。
     *
     * @param subLevel 当前 SubLevel（服务端），isRemoved() 已预先过滤
     * @param parts    该 SubLevel 内收集到的所有 V2 Part，已过滤空列表
     * @param handle   刚体句柄，用于施加力/扭矩，不可为 null
     * @param timeStep 本步进的物理时间步长（秒）
     */
    void onPhysicsTick(ServerSubLevel subLevel, List<? extends Part> parts, RigidBodyHandle handle, double timeStep);
}
