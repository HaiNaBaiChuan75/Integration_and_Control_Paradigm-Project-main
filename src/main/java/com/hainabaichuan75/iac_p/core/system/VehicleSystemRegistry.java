package com.hainabaichuan75.iac_p.core.system;

import com.hainabaichuan75.iac_p.core.api.system.AxisRenderSystem;
import com.hainabaichuan75.iac_p.core.part.PartBlockEntity;
import com.hainabaichuan75.iac_p.test_system.RandomAimVehicleSystem;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class VehicleSystemRegistry {
    public static final List<VehicleTickSystem> TICK_SYSTEMS = new ArrayList<>();
    public static final List<VehiclePhysicsSystem> PHYSICS_SYSTEMS = new ArrayList<>();
    public static final List<VehicleClientSystem> CLIENT_SYSTEMS = new ArrayList<>();

    private VehicleSystemRegistry() {}

    /**
     * 在 {@link com.hainabaichuan75.iac_p.IACP} 构造时调用。
     */
    public static void registerAll() {
        registerTickSystem(new RandomAimVehicleSystem());
        registerClientSystem(new AxisRenderSystem());
    }

    public static void registerTickSystem(@NotNull VehicleTickSystem system) {
        TICK_SYSTEMS.add(system);
    }

    public static void registerPhysicsSystem(@NotNull VehiclePhysicsSystem system) {
        PHYSICS_SYSTEMS.add(system);
    }

    public static void registerClientSystem(@NotNull VehicleClientSystem system) {
        CLIENT_SYSTEMS.add(system);
    }

    @NotNull
    public static List<PartBlockEntity> collectParts(@NotNull SubLevel subLevel) {
        var parts = new ArrayList<PartBlockEntity>();
        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof PartBlockEntity vp) {
                parts.add(vp);
            }
        }
        return parts;
    }
}
