package com.hainabaichuan75.iac_p.core.dispatch;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.core.part.PartBlockEntity;
import com.hainabaichuan75.iac_p.core.system.VehicleClientSystem;
import com.hainabaichuan75.iac_p.core.system.VehiclePhysicsSystem;
import com.hainabaichuan75.iac_p.core.system.VehicleSystemRegistry;
import com.hainabaichuan75.iac_p.core.system.VehicleTickSystem;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@EventBusSubscriber
public class VehicleSystemDispatcher {
    @SubscribeEvent
    public static void onPreLevelTick(@NotNull LevelTickEvent.Pre event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            ClientSubLevelContainer container = ClientSubLevelContainer.getContainer((ClientLevel) level);
            if (container == null) return;
            for (ClientSubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel.isRemoved()) continue;
                List<PartBlockEntity> parts = VehicleSystemRegistry.collectParts(subLevel);
                if (parts.isEmpty()) continue;

                for (VehicleClientSystem system : VehicleSystemRegistry.CLIENT_SYSTEMS) {
                    try {
                        system.onTick(subLevel, parts);
                    } catch (Exception e) {
                        IACP.LOGGER.warn("[VehicleClientSystem: {}] onClientTick: ",
                                system.getClass().getSimpleName(), e);
                    }
                }
            }
        } else {

            ServerSubLevelContainer container = ServerSubLevelContainer.getContainer((ServerLevel) level);
            if (container == null) return;

            for (ServerSubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel.isRemoved()) continue;

                List<PartBlockEntity> parts = VehicleSystemRegistry.collectParts(subLevel);
                if (parts.isEmpty()) continue;

                for (VehicleTickSystem system : VehicleSystemRegistry.TICK_SYSTEMS) {
                    try {
                        system.onTick(subLevel, parts);
                    } catch (Exception e) {
                        IACP.LOGGER.warn("[VehicleTickSystem: {}] onSubLevelTick: ",
                                system.getClass().getSimpleName(), e);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPrePhysicsTick(@NotNull ForgeSablePrePhysicsTickEvent event) {
        SubLevelPhysicsSystem physicsSystem = event.getPhysicsSystem();
        ServerLevel level = physicsSystem.getLevel();
        double timeStep = event.getTimeStep();

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) return;

        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;

            List<PartBlockEntity> parts = VehicleSystemRegistry.collectParts(subLevel);
            if (parts.isEmpty()) continue;

            var handle = physicsSystem.getPhysicsHandle(subLevel);

            for (VehiclePhysicsSystem system : VehicleSystemRegistry.PHYSICS_SYSTEMS) {
                try {
                    system.onPhysicsTick(subLevel, parts, handle, timeStep);
                } catch (Exception e) {
                    IACP.LOGGER.warn("[VehiclePhysicsSystem: {}] onSubLevelPhysicsTick: ",
                            system.getClass().getSimpleName(), e);
                }
            }
        }
    }

}
