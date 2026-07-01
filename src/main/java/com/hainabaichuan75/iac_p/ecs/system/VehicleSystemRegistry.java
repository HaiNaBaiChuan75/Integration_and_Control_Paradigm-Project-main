package com.hainabaichuan75.iac_p.ecs.system;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.ecs.part.PartBlockEntity;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * VehicleSystem 注册表——所有 System 在此注册，调度器从此读取。
 * <p>
 * <b>线程安全</b>：注册仅在 Mod 构造阶段（{@link com.hainabaichuan75.iac_p.IACP#IACP IACP 构造器}）
 * 完成，运行时只读。无需同步机制。
 * <p>
 * <b>设计原则</b>：
 * <ul>
 * <li><b>注册与调度分离</b>——注册表只负责收集，调度器只负责执行</li>
 * <li><b>渐进采用</b>——现有 BE 无需立即迁移，新功能按 System 接口编写即可</li>
 * <li><b>轻量</b>——纯 Java 接口 + 静态列表，无框架依赖</li>
 * </ul>
 */
public final class VehicleSystemRegistry {

    private static final List<VehicleTickSystem> TICK_SYSTEMS = new ArrayList<>();
    private static final List<VehiclePhysicsSystem> PHYSICS_SYSTEMS = new ArrayList<>();
    private static final List<VehicleClientSystem> CLIENT_SYSTEMS = new ArrayList<>();

    // ============================================================
    //  只读视图
    // ============================================================
    public static List<VehicleTickSystem> getTickSystems() {
        return Collections.unmodifiableList(TICK_SYSTEMS);
    }

    public static List<VehiclePhysicsSystem> getPhysicsSystems() {
        return Collections.unmodifiableList(PHYSICS_SYSTEMS);
    }

    public static List<VehicleClientSystem> getClientSystems() {
        return Collections.unmodifiableList(CLIENT_SYSTEMS);
    }

    // ============================================================
    //  注册方法
    // ============================================================
    public static void register(VehicleTickSystem system) {
        TICK_SYSTEMS.add(system);
        IACP.LOGGER.debug("Registered VehicleTickSystem: {}", system.getClass().getSimpleName());
    }

    public static void register(VehiclePhysicsSystem system) {
        PHYSICS_SYSTEMS.add(system);
        IACP.LOGGER.debug("Registered VehiclePhysicsSystem: {}", system.getClass().getSimpleName());
    }

    public static void register(VehicleClientSystem system) {
        CLIENT_SYSTEMS.add(system);
        IACP.LOGGER.debug("Registered VehicleClientSystem: {}", system.getClass().getSimpleName());
    }

    // ============================================================
    //  部件收集（遍历 Sable 内部 actor 列表，O(actors)）
    // ============================================================
    /**
     * 收集指定 SubLevel 中的所有 {@link PartBlockEntity}。
     * <p>
     * 使用 Sable 的 {@code getBlockEntityActors()} 遍历，
     * 只迭代实际注册的 BlockEntity Actor，避免 O(chunk_volume) 扫描。
     *
     * @param subLevel 目标 SubLevel
     * @return PartBlockEntity 列表（可能为空，不可为 null）
     */
    @NotNull
    public static List<PartBlockEntity> collectParts(@NotNull SubLevel subLevel) {
        List<PartBlockEntity> parts = new ArrayList<>();
        if (subLevel.getPlot() == null) return parts;

        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof PartBlockEntity part) {
                parts.add(part);
            }
        }
        return parts;
    }

    // ============================================================
    //  批量注册入口（在 Mod 构造器中调用一次）
    // ============================================================
    /**
     * 注册所有内置 System。
     * <p>
     * 调用位置：{@link com.hainabaichuan75.iac_p.IACP#IACP IACP 构造器}中。
     * 在此方法中添加新的 System 注册，避免分散在各处。
     * <p>
     * 客户端专用 System（如 {@link com.hainabaichuan75.iac_p.system.AxisRenderSystem}）
     * 不能在服务端注册——其注册在 {@code IACPClient} 中完成。
     */
    public static void registerAll() {
        // 所有 System 在此统一注册
        // register(new WeaponAimSystem());
        // register(new SuspensionPhysicsSystem());
        // register(new SpeedHudSystem());
    }

    private VehicleSystemRegistry() {}
}
