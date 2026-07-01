package com.hainabaichuan75.iac_p.core.dispatch;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.core.part.PartBlockEntity;
import com.hainabaichuan75.iac_p.core.system.VehicleClientSystem;
import com.hainabaichuan75.iac_p.core.system.VehiclePhysicsSystem;
import com.hainabaichuan75.iac_p.core.system.VehicleSystemRegistry;
import com.hainabaichuan75.iac_p.core.system.VehicleTickSystem;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * NeoForge 事件 → VehicleSystem 调用的桥梁。
 * <p>
 * 将 NeoForge 的 tick 事件路由到已注册的 VehicleSystem：
 * <ul>
 *   <li>{@link ServerTickEvent.Post} → {@link VehicleTickSystem#onTick}</li>
 *   <li>{@link ForgeSablePostPhysicsTickEvent} → {@link VehiclePhysicsSystem#onPhysicsTick}</li>
 *   <li>{@link LevelTickEvent.Pre}（客户端）→ {@link VehicleClientSystem#onTick}</li>
 * </ul>
 * <p>
 * <b>设计原则</b>：
 * <ul>
 *   <li>每个 SubLevel 独立调度——一个载具的 System 崩溃不影响其他载具</li>
 *   <li>内置性能分析——超过 100μs 的 System 自动记录慢查询日志</li>
 *   <li>空 System 列表时零开销——快速返回</li>
 * </ul>
 * <p>
 * 注册方式：在 {@link IACP} 构造器中调用
 * {@code NeoForge.EVENT_BUS.register(new VehicleSystemDispatcher())}。
 */
public class VehicleSystemDispatcher {

    // ============================================================
    //  逻辑 Tick（服务端 20Hz）
    // ============================================================
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        List<VehicleTickSystem> systems = VehicleSystemRegistry.getTickSystems();
        if (systems.isEmpty()) return;

        // 遍历所有已加载的 ServerLevel
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) continue;

            for (SubLevel sl : container.getAllSubLevels()) {
                if (sl.isRemoved()) continue;
                if (!(sl instanceof ServerSubLevel serverSL)) continue;

                List<PartBlockEntity> parts = VehicleSystemRegistry.collectParts(serverSL);
                if (parts.isEmpty()) continue;

                for (VehicleTickSystem system : systems) {
                    long start = System.nanoTime();
                    try {
                        system.onTick(serverSL, parts);
                    } catch (Exception e) {
                        IACP.LOGGER.error("[System] {} 异常: {}", system.getClass().getSimpleName(), e.getMessage());
                    }
                    long elapsed = System.nanoTime() - start;
                    if (elapsed > 100_000) { // >100μs 慢查询
                        IACP.LOGGER.warn("[Perf] 慢 VehicleTickSystem: {} took {}μs",
                                system.getClass().getSimpleName(), elapsed / 1000);
                    }
                }
            }
        }
    }

    // ============================================================
    //  物理 Tick（Sable 物理步进后 ~100Hz）
    // ============================================================
    @SubscribeEvent
    public void onPostPhysicsTick(ForgeSablePostPhysicsTickEvent event) {
        List<VehiclePhysicsSystem> systems = VehicleSystemRegistry.getPhysicsSystems();
        if (systems.isEmpty()) return;

        var physicsSystem = event.getPhysicsSystem();
        ServerLevel level = physicsSystem.getLevel();

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        for (SubLevel sl : container.getAllSubLevels()) {
            if (sl.isRemoved()) continue;
            if (!(sl instanceof ServerSubLevel serverSL)) continue;

            List<PartBlockEntity> parts = VehicleSystemRegistry.collectParts(serverSL);
            if (parts.isEmpty()) continue;

            RigidBodyHandle handle = RigidBodyHandle.of(serverSL);
            double timeStep = 0.01;

            for (VehiclePhysicsSystem system : systems) {
                long start = System.nanoTime();
                try {
                    system.onPhysicsTick(serverSL, parts, handle, timeStep);
                } catch (Exception e) {
                    IACP.LOGGER.error("[System] {} 物理异常: {}", system.getClass().getSimpleName(), e.getMessage());
                }
                long elapsed = System.nanoTime() - start;
                if (elapsed > 100_000) {
                    IACP.LOGGER.warn("[Perf] 慢 VehiclePhysicsSystem: {} took {}μs",
                            system.getClass().getSimpleName(), elapsed / 1000);
                }
            }
        }
    }

    // ============================================================
    //  客户端 Tick（20Hz）
    // ============================================================
    @SubscribeEvent
    public void onClientTick(LevelTickEvent.Pre event) {
        Level level = event.getLevel();
        if (!level.isClientSide()) return;

        List<VehicleClientSystem> systems = VehicleSystemRegistry.getClientSystems();
        if (systems.isEmpty()) return;

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        for (SubLevel sl : container.getAllSubLevels()) {
            if (sl.isRemoved()) continue;
            if (!(sl instanceof ClientSubLevel clientSL)) continue;

            List<PartBlockEntity> parts = VehicleSystemRegistry.collectParts(clientSL);
            if (parts.isEmpty()) continue;

            for (VehicleClientSystem system : systems) {
                long start = System.nanoTime();
                try {
                    system.onTick(clientSL, parts);
                } catch (Exception e) {
                    IACP.LOGGER.error("[System] 客户端 {} 异常: {}", system.getClass().getSimpleName(), e.getMessage());
                }
                long elapsed = System.nanoTime() - start;
                if (elapsed > 100_000) {
                    IACP.LOGGER.warn("[Perf] 慢 VehicleClientSystem: {} took {}μs",
                            system.getClass().getSimpleName(), elapsed / 1000);
                }
            }
        }
    }

}
