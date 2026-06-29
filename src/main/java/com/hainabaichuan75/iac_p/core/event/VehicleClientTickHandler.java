package com.hainabaichuan75.iac_p.core.event;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.core.system.VehicleClientSystem;
import com.hainabaichuan75.iac_p.core.system.VehicleSystems;
import com.hainabaichuan75.iac_p.core.vehicle.VehiclePartBlockEntity;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;

/**
 * 客户端载具 Tick 事件分发器。<p>
 * 在 {@link LevelTickEvent.Pre} 事件的客户端侧驱动所有注册的 {@link VehicleClientSystem}。
 * 使用与服务器端相同的事件模式，通过 {@code isClientSide()} 守卫仅在客户端执行。
 */
@EventBusSubscriber
public class VehicleClientTickHandler {

    @SubscribeEvent
    public static void onPreLevelTick(LevelTickEvent.Pre event) {
        Level level = event.getLevel();
        if (!level.isClientSide()) return;

        ClientSubLevelContainer container = ClientSubLevelContainer.getContainer((ClientLevel) level);
        if (container == null) return;
        for (ClientSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;

            // 懒收集——只有该 SubLevel 里有载具部件时才跑 Systems
            List<VehiclePartBlockEntity> parts = VehicleSystems.collectParts(subLevel);
            if (parts.isEmpty()) continue;

            for (VehicleClientSystem system : VehicleSystems.CLIENT_SYSTEMS) {
                try {
                    system.onClientTick(subLevel, parts);
                } catch (Exception e) {
                    IACP.LOGGER.warn("[VehicleClientSystem: {}] onClientTick: ", system.getClass().getSimpleName(), e);
                }
            }
        }
    }
}
