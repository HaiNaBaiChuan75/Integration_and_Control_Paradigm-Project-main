package com.hainabaichuan75.iac_p.vehicle;

import com.hainabaichuan75.iac_p.vehicle.cabin.CabinBlockEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.List;

public interface System {
    default void onSubLeveTick(ServerSubLevel subLevel, CabinBlockEntity cabinBlockEntity,
                               List<VehiclePartBlockEntity> parts) {}

    ;

    default void onSubLevelPhysicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep,
                                       CabinBlockEntity cabinBlockEntity, List<VehiclePartBlockEntity> parts) {}
}
