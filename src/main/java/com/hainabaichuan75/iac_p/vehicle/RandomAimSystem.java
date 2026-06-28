package com.hainabaichuan75.iac_p.vehicle;

import com.hainabaichuan75.iac_p.vehicle.cabin.CabinBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.util.Mth;
import org.joml.Vector3d;

import java.util.List;

public class RandomAimSystem implements System {
    @Override
    public void onSubLeveTick(ServerSubLevel subLevel, CabinBlockEntity cabinBlockEntity,
                              List<VehiclePartBlockEntity> parts) {

        float p = subLevel.getLevel().getGameTime() / 200f;
        Vector3d relativeTarget = new Vector3d(10, 2 * Mth.sin(p / 1.414f), 0).rotateAxis(p, 0, 1, 0);

        for (VehiclePartBlockEntity vehiclePartBlockEntity : parts) {
            if (vehiclePartBlockEntity instanceof Aimable aimable) {
                aimable.aimAt((vehiclePartBlockEntity.getAbsPosition(subLevel).add(relativeTarget, new Vector3d())));
            }
        }
    }
}
