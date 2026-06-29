package com.hainabaichuan75.iac_p.core.system;

import com.hainabaichuan75.iac_p.core.vehicle.VehiclePartBlockEntity;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;

import java.util.List;

public interface VehicleClientSystem {
    default void onClientTick(ClientSubLevel subLevel, List<VehiclePartBlockEntity> parts) {}
}
