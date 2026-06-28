package com.hainabaichuan75.iac_p.vehicle;

import com.hainabaichuan75.iac_p.test_system.RandomAimSystem;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.ArrayList;
import java.util.List;

public class Systems {
    public static final List<System> SYSTEMS = new ArrayList<>();

    private Systems() {}

    public static void registerAll() {
        registerSystem(new RandomAimSystem());
    }

    public static void registerSystem(System system) {
        SYSTEMS.add(system);
    }

    /**
     * 从 SubLevel 中收集所有 {VehiclePartBlockEntity}。
     */
    public static List<VehiclePartBlockEntity> collectParts(ServerSubLevel subLevel) {
        var parts = new ArrayList<VehiclePartBlockEntity>();
        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof VehiclePartBlockEntity vp) {
                parts.add(vp);
            }
        }
        return parts;
    }
}
