package com.hainabaichuan75.iac_p.core.system;

import com.hainabaichuan75.iac_p.core.vehicle.VehiclePartBlockEntity;
import com.hainabaichuan75.iac_p.test_system.RandomAimVehicleSystem;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.SubLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * 载具系统注册表。<p>
 * 维护三个独立列表以分别注册和调度：
 * <ul>
 *   <li>{@link #TICK_SYSTEMS} —— 普通 tick 系统（20Hz 逻辑）</li>
 *   <li>{@link #PHYSICS_SYSTEMS} —— 物理 tick 系统（Sable 步进前）</li>
 *   <li>{@link #CLIENT_SYSTEMS} —— 客户端 tick 系统（输入/HUD 等）</li>
 * </ul>
 */
public class VehicleSystems {
    public static final List<VehicleTickSystem> TICK_SYSTEMS = new ArrayList<>();
    public static final List<VehiclePhysicsSystem> PHYSICS_SYSTEMS = new ArrayList<>();
    public static final List<VehicleClientSystem> CLIENT_SYSTEMS = new ArrayList<>();

    private VehicleSystems() {}

    /**
     * 注册所有内建系统。
     * 在 {@link com.hainabaichuan75.iac_p.IACP} 构造时调用。
     */
    public static void registerAll() {
        registerTickSystem(new RandomAimVehicleSystem());
        registerClientSystem(new AxisRenderSystem());
    }

    public static void registerTickSystem(VehicleTickSystem system) {
        TICK_SYSTEMS.add(system);
    }

    public static void registerPhysicsSystem(VehiclePhysicsSystem system) {
        PHYSICS_SYSTEMS.add(system);
    }

    public static void registerClientSystem(VehicleClientSystem system) {
        CLIENT_SYSTEMS.add(system);
    }

    /**
     * 从 SubLevel 中收集所有{@link VehiclePartBlockEntity}。
     */
    public static List<VehiclePartBlockEntity> collectParts(SubLevel subLevel) {
        var parts = new ArrayList<VehiclePartBlockEntity>();
        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof VehiclePartBlockEntity vp) {
                parts.add(vp);
            }
        }
        return parts;
    }
}
