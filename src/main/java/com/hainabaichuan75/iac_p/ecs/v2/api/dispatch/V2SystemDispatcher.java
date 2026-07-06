package com.hainabaichuan75.iac_p.ecs.v2.api.dispatch;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import com.hainabaichuan75.iac_p.ecs.v2.api.system.ClientSystem;
import com.hainabaichuan75.iac_p.ecs.v2.api.system.PhysicsSystem;
import com.hainabaichuan75.iac_p.ecs.v2.api.system.TickSystem;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * NeoForge 事件 → V2 VehicleSystem 调用的调度桥梁。
 * 将三种 NeoForge 事件路由到 {@link V2SystemRegistry} 中已注册的 V2 System。
 * <p>
 * <b>与 V1 的关系</b>：此调度器与 {@code VehicleSystemDispatcher} 完全独立，
 * 分别驱动 V1 和 V2 的 System 管道。两者注册在同一个 NeoForge 事件总线上，
 * 各自独立执行，互不阻塞。
 * <p>
 * <b>设计保证</b>：
 * <ul>
 *   <li><b>SubLevel 隔离</b>——每个 SubLevel 独立调度，一个载具的 System 崩溃不影响其他载具</li>
 *   <li><b>慢查询检测</b>——超过 100μs 的 System 自动记录性能警告</li>
 *   <li><b>空列表零开销</b>——System 列表为空时直接返回，不遍历 Level/SubLevel</li>
 *   <li><b>异常隔离</b>——每个 System 调用包装在 try-catch 中，防止单个异常中断整条调度链</li>
 * </ul>
 */
@EventBusSubscriber
public class V2SystemDispatcher {

    // ============================================================
    //  逻辑 Tick（服务端 20Hz）
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        List<TickSystem> systems = V2SystemRegistry.getTickSystems();
        if (systems.isEmpty()) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) continue;

            for (SubLevel sl : container.getAllSubLevels()) {
                if (sl.isRemoved()) continue;
                if (!(sl instanceof ServerSubLevel serverSL)) continue;

                List<Part> parts = V2SystemRegistry.collectParts(serverSL);
                if (parts.isEmpty()) continue;

                for (TickSystem system : systems) {
                    long start = System.nanoTime();
                    try {
                        system.onTick(serverSL, parts);
                    } catch (Exception e) {
                        IACP.LOGGER.error("[V2System] {} 异常: {}", system.getClass().getSimpleName(), e.getMessage());
                    }
                    long elapsed = System.nanoTime() - start;
                    if (elapsed > 100_000) { // >100μs 慢查询
                        IACP.LOGGER.warn("[V2Perf] 慢 TickSystem: {} took {}μs", system.getClass().getSimpleName(),
                                elapsed / 1000);
                    }
                }
            }
        }
    }

    // ============================================================
    //  物理 Tick（Sable 物理步进前 ~100Hz）
    // ============================================================

    @SubscribeEvent
    public static void onPrePhysicsTick(ForgeSablePrePhysicsTickEvent event) {
        List<PhysicsSystem> systems = V2SystemRegistry.getPhysicsSystems();
        if (systems.isEmpty()) return;

        var physicsSystem = event.getPhysicsSystem();
        ServerLevel level = physicsSystem.getLevel();

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        for (SubLevel sl : container.getAllSubLevels()) {
            if (sl.isRemoved()) continue;
            if (!(sl instanceof ServerSubLevel serverSL)) continue;

            List<Part> parts = V2SystemRegistry.collectParts(serverSL);
            if (parts.isEmpty()) continue;

            RigidBodyHandle handle = RigidBodyHandle.of(serverSL);
            double timeStep = 0.01;

            for (PhysicsSystem system : systems) {
                long start = System.nanoTime();
                try {
                    system.onPhysicsTick(serverSL, parts, handle, timeStep);
                } catch (Exception e) {
                    IACP.LOGGER.error("[V2System] {} 物理异常: {}", system.getClass().getSimpleName(), e.getMessage());
                }
                long elapsed = System.nanoTime() - start;
                if (elapsed > 100_000) {
                    IACP.LOGGER.warn("[V2Perf] 慢 PhysicsSystem: {} took {}μs", system.getClass().getSimpleName(),
                            elapsed / 1000);
                }
            }
        }
    }

    // ============================================================
    //  客户端 Tick（20Hz）
    // ============================================================

    @SubscribeEvent
    public static void onClientTick(LevelTickEvent.Pre event) {
        Level level = event.getLevel();
        if (!level.isClientSide()) return;

        List<ClientSystem> systems = V2SystemRegistry.getClientSystems();
        if (systems.isEmpty()) return;

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        for (SubLevel sl : container.getAllSubLevels()) {
            if (sl.isRemoved()) continue;
            if (!(sl instanceof ClientSubLevel clientSL)) continue;

            List<Part> parts = V2SystemRegistry.collectParts(clientSL);
            if (parts.isEmpty()) continue;

            for (ClientSystem system : systems) {
                long start = System.nanoTime();
                try {
                    system.onTick(clientSL, parts);
                } catch (Exception e) {
                    IACP.LOGGER.error("[V2System] 客户端 {} 异常: {}", system.getClass().getSimpleName(), e.getMessage());
                }
                long elapsed = System.nanoTime() - start;
                if (elapsed > 100_000) {
                    IACP.LOGGER.warn("[V2Perf] 慢 ClientSystem: {} took {}μs", system.getClass().getSimpleName(),
                            elapsed / 1000);
                }
            }
        }
    }
}
