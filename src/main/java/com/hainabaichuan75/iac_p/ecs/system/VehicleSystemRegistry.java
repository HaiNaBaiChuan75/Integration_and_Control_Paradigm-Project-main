package com.hainabaichuan75.iac_p.ecs.system;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.ecs.part.Part;
import com.hainabaichuan75.iac_p.system.*;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * VehicleSystem 注册表——所有 System 在此注册，调度器从此读取。
 * <p>
 * <b>线程安全</b>：注册仅在 Mod 构造阶段
 * （{@link com.hainabaichuan75.iac_p.IACP#IACP IACP 构造器} /
 *  {@link com.hainabaichuan75.iac_p.IACPClient#IACPClient IACPClient 构造器}）
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
     * 收集指定 SubLevel 中的所有 {@link Part}。
     * <p>
     * 使用 Sable 的 {@code getBlockEntityActors()} 遍历，
     * 只迭代实际注册的 BlockEntity Actor，避免 O(chunk_volume) 扫描。
     *
     * @param subLevel 目标 SubLevel
     * @return Part 列表（可能为空，不可为 null）
     */
    @NotNull
    public static List<Part> collectParts(@NotNull SubLevel subLevel) {
        List<Part> parts = new ArrayList<>();
        if (subLevel.getPlot() == null) return parts;

        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof Part part) {
                parts.add(part);
            }
        }
        return parts;
    }

    // ============================================================
    //  服务端 System 注册入口（在 IACP 构造器中调用一次）
    // ============================================================
    /**
     * 注册所有服务端内置 System（{@link VehicleTickSystem} 和 {@link VehiclePhysicsSystem}）。
     * <p>
     * 调用位置：{@link com.hainabaichuan75.iac_p.IACP#IACP IACP 构造器}。
     * 在此方法中添加新的服务端 System 注册，避免分散在各处。
     * <p>
     * <b>客户端专用 System 不在此注册</b>——它们在
     * {@link #registerClientSystems()} 中，在 {@code IACPClient} 构造器中调用。
     */
    public static void registerServerSystems() {
        register(new SteeringSystem());            // 转向：最先运行
        register(new TorqueDistributionSystem());   // 扭矩分配：依赖转向结果
        register(new SuspensionSystem());            // 悬挂压缩：轮下射线 → 压缩量（20Hz 节约射线）
        register(new WeaponAimSystem());             // 瞄准：独立

        // ── 物理 System（按 Sable 步进频率 ~100Hz 执行）──
        register(new SuspensionForceSystem());       // 悬挂弹簧力：压缩 → F = k×x
        register(new TractionForceSystem());         // 牵引力：扭矩 → F = τ/r
    }

    // ============================================================
    //  客户端 System 注册入口（在 IACPClient 构造器中调用一次）
    // ============================================================

    /**
     * 注册所有客户端内置 System（{@link VehicleClientSystem}）。
     * <p>
     * 调用位置：{@link com.hainabaichuan75.iac_p.IACPClient#IACPClient IACPClient 构造器}。
     * 在此方法中添加新的客户端 System 注册。
     */
    public static void registerClientSystems() {
        register(new AxisRenderSystem());
    }

    private VehicleSystemRegistry() {}
}
