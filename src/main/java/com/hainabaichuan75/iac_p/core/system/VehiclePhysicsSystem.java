package com.hainabaichuan75.iac_p.core.system;

import com.hainabaichuan75.iac_p.core.part.PartBlockEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public interface VehiclePhysicsSystem {
    default void onPhysicsTick(@NotNull ServerSubLevel subLevel, @NotNull List<PartBlockEntity> parts,
                               @NotNull RigidBodyHandle handle, double timeStep) {}
}
