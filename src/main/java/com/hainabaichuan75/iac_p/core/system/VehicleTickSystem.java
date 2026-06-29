package com.hainabaichuan75.iac_p.core.system;

import com.hainabaichuan75.iac_p.core.vehicle.VehiclePartBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.List;

/**
 * 载具逻辑 Tick 系统。<p>
 * 在 {@link net.neoforged.neoforge.event.tick.LevelTickEvent.Pre} 阶段驱动，
 * 用于每 tick（20Hz）的载具逻辑，如瞄准、控制、状态更新。
 *
 * @see VehiclePhysicsSystem 物理 Tick 系统
 * @see VehicleSystems      注册表
 */
public interface VehicleTickSystem {

    /**
     * 在每个 SubLevel 的普通 tick 中被调用（20Hz）。
     *
     * @param subLevel 当前载具所在的 SubLevel
     * @param parts    该 SubLevel 中所有的载具部件
     */
    default void onSubLevelTick(ServerSubLevel subLevel, List<VehiclePartBlockEntity> parts) {}
}
