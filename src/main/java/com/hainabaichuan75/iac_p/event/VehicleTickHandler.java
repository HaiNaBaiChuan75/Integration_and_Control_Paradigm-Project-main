package com.hainabaichuan75.iac_p.event;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.vehicle.VehiclePartBlockEntity;
import com.hainabaichuan75.iac_p.vehicle.VehicleSystems;
import com.hainabaichuan75.iac_p.vehicle.VehicleTickSystem;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;

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
            List<VehiclePartBlockEntity> parts = VehicleSystems.collectParts(subLevel);
            if (parts.isEmpty()) continue;

            for (VehicleTickSystem system : VehicleSystems.TICK_SYSTEMS) {
                try {
                    system.onSubLevelTick(subLevel, parts);
                } catch (Exception e) {
                    IACP.LOGGER.warn("[VehicleTickSystem: {}] onSubLevelTick: ", system.getClass().getSimpleName(), e);
                }
            }
        }
    }
}
