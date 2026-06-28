package com.hainabaichuan75.iac_p.vehicle;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.List;

public interface System {
    /**
     * 游戏 tick（20Hz），在物理 step 之前调用。
     */
    default void onSubLevelTick(ServerSubLevel subLevel, List<VehiclePartBlockEntity> parts) {}

    /** 物理 tick（~60Hz），在物理 step 中调用。 */
    default void onSubLevelPhysicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep, List<VehiclePartBlockEntity> parts) {}
}
