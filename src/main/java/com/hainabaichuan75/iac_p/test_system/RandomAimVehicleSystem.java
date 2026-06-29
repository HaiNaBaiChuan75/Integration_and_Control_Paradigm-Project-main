package com.hainabaichuan75.iac_p.test_system;

import com.hainabaichuan75.iac_p.core.part.PartBlockEntity;
import com.hainabaichuan75.iac_p.core.system.VehicleTickSystem;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;

import java.util.List;

public class RandomAimVehicleSystem implements VehicleTickSystem {
    @Override
    public void onTick(@NotNull ServerSubLevel subLevel, @NotNull List<PartBlockEntity> parts) {

        float p = subLevel.getLevel().getGameTime() / 80f;
        Vector3d relativeTarget = new Vector3d(10, 2 * Mth.sin(p / 1.414f), 0).rotateAxis(p, 0, 1, 0);

        for (PartBlockEntity partBlockEntity : parts) {
            if (partBlockEntity instanceof Aimable aimable) {
                aimable.aimAt((partBlockEntity.getCenterInWorld().add(relativeTarget, new Vector3d())));
            }
        }
    }
}
