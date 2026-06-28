package com.hainabaichuan75.iac_p.vehicle;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.List;

public interface System {
    default void onSubLevelTick(ServerSubLevel subLevel, List<VehiclePartBlockEntity> parts) {}

    default void onSubLevelPhysicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep, List<VehiclePartBlockEntity> parts) {}
}
