package com.hainabaichuan75.iac_p.event;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.vehicle.System;
import com.hainabaichuan75.iac_p.vehicle.Systems;
import com.hainabaichuan75.iac_p.vehicle.VehiclePartBlockEntity;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;

@EventBusSubscriber
public class VehiclePhysicsTickHandler {

    @SubscribeEvent
    public static void onPrePhysicsTick(ForgeSablePrePhysicsTickEvent event) {
        SubLevelPhysicsSystem physicsSystem = event.getPhysicsSystem();
        ServerLevel level = physicsSystem.getLevel();
        double timeStep = event.getTimeStep();

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) return;

        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;

            List<VehiclePartBlockEntity> parts = Systems.collectParts(subLevel);
            if (parts.isEmpty()) continue;

            var handle = physicsSystem.getPhysicsHandle(subLevel);

            for (System system : Systems.SYSTEMS) {
                try {
                    system.onSubLevelPhysicsTick(subLevel, handle, timeStep, parts);
                } catch (Exception e) {
                    IACP.LOGGER.warn("[System: {}] onSubLevelPhysicsTick: ", system.getClass().getSimpleName(), e);
                }
            }
        }
    }
}
