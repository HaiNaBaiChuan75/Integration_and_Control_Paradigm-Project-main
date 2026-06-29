package com.hainabaichuan75.iac_p.core.system;

import com.hainabaichuan75.iac_p.core.vehicle.VehiclePartBlockEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.List;

/**
 * 载具物理 Tick 系统。<p>
 * 在 {@link dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent} 阶段驱动，
 * 用于物理步进前的载具逻辑，如施加力、弹簧阻尼、悬挂计算。
 *
 * @see VehicleTickSystem 逻辑 Tick 系统
 * @see VehicleSystems    注册表
 */
public interface VehiclePhysicsSystem {

    /**
     * 在每个 SubLevel 的物理 tick 中被调用（在 Rapier 物理引擎步进之前）。
     *
     * @param subLevel 当前载具所在的 SubLevel
     * @param handle   该 SubLevel 的刚体句柄，用于施加力
     * @param timeStep 本次物理步进的时间步长（秒）
     * @param parts    该 SubLevel 中所有的载具部件
     */
    default void onSubLevelPhysicsTick(ServerSubLevel subLevel, List<VehiclePartBlockEntity> parts,
                                       RigidBodyHandle handle, double timeStep) {}
}
