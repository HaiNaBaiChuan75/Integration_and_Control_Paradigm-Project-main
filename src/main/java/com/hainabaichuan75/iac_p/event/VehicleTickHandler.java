package com.hainabaichuan75.iac_p.event;

import com.hainabaichuan75.iac_p.vehicle.System;
import com.hainabaichuan75.iac_p.vehicle.Systems;
import com.hainabaichuan75.iac_p.vehicle.VehiclePartBlockEntity;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;

/**
 * 每游戏 tick 驱动 {@link Systems} 的入口。
 * <p>
 * 通过监听 {@link LevelTickEvent.Pre}（在 SubLevelPhysicsSystem.tick() 之前触发），
 * 遍历所有活跃 SubLevel 并对每个 SubLevel 运行已注册的 {@link System}。
 * 这使得 Systems 不再依赖特定的方块实体来触发。
 *
 * <h3>时序</h3>
 * <pre>
 * LevelTickEvent.Pre    ← Systems.onSubLevelTick()    逻辑计算
 *   SubLevelPhysicsSystem.tick()
 *     sable$tick()       ← BE 读取 Systems 计算结果   执行本地行为
 *     pipeline.tick()
 *     tickPipelinePhysics()
 *       sable$physicsTick()  ← BE 物理                施加弹簧力等
 * </pre>
 */
@EventBusSubscriber
public class VehicleTickHandler {

    @SubscribeEvent
    public static void onPreLevelTick(LevelTickEvent.Pre event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer((ServerLevel) level);
        if (container == null) return;

        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;

            // 懒收集——只有该 SubLevel 里有载具部件时才跑 Systems
            List<VehiclePartBlockEntity> parts = Systems.collectParts(subLevel);
            if (parts.isEmpty()) continue;

            for (System system : Systems.SYSTEMS) {
                system.onSubLevelTick(subLevel, parts);
            }
        }
    }
}
