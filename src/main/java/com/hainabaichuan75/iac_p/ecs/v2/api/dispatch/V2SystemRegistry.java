package com.hainabaichuan75.iac_p.ecs.v2.api.dispatch;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import com.hainabaichuan75.iac_p.ecs.v2.api.system.ClientSystem;
import com.hainabaichuan75.iac_p.ecs.v2.api.system.PhysicsSystem;
import com.hainabaichuan75.iac_p.ecs.v2.api.system.TickSystem;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * V2 System 注册表——所有 V2 System 在此注册，调度器从此读取。
 * <p>
 * <b>线程安全</b>：注册仅在 Mod 构造阶段
 * （{@link com.hainabaichuan75.iac_p.IACP#IACP IACP 构造器} /
 * {@link com.hainabaichuan75.iac_p.IACPClient#IACPClient IACPClient 构造器}）
 * 完成，运行时只读。无需同步机制。
 * <p>
 * <b>与 V1 的关系</b>：此注册表与 {@code VehicleSystemRegistry} 完全独立，
 * 分别管理 V1 和 V2 的 System 与 Part 生命周期。两者互不干扰，可共存于同一个 NeoForge 事件总线。
 * <p>
 * <b>设计原则</b>：
 * <ul>
 * <li><b>注册与调度分离</b>——注册表只负责收集，调度器只负责执行</li>
 * <li><b>渐进采用</b>——现有 BE 无需立即迁移，新功能按 V2 System 接口编写即可</li>
 * <li><b>轻量</b>——纯 Java 接口 + 静态列表，无框架依赖</li>
 * </ul>
 */
public final class V2SystemRegistry {

    private static final List<TickSystem> TICK_SYSTEMS = new ArrayList<>();
    private static final List<PhysicsSystem> PHYSICS_SYSTEMS = new ArrayList<>();
    private static final List<ClientSystem> CLIENT_SYSTEMS = new ArrayList<>();

    // ============================================================
    //  只读视图
    // ============================================================

    @NotNull
    public static List<TickSystem> getTickSystems() {
        return Collections.unmodifiableList(TICK_SYSTEMS);
    }

    @NotNull
    public static List<PhysicsSystem> getPhysicsSystems() {
        return Collections.unmodifiableList(PHYSICS_SYSTEMS);
    }

    @NotNull
    public static List<ClientSystem> getClientSystems() {
        return Collections.unmodifiableList(CLIENT_SYSTEMS);
    }

    // ============================================================
    //  注册方法
    // ============================================================

    public static void register(@NotNull TickSystem system) {
        TICK_SYSTEMS.add(system);
        IACP.LOGGER.debug("Registered V2 TickSystem: {}", system.getClass().getSimpleName());
    }

    public static void register(@NotNull PhysicsSystem system) {
        PHYSICS_SYSTEMS.add(system);
        IACP.LOGGER.debug("Registered V2 PhysicsSystem: {}", system.getClass().getSimpleName());
    }

    public static void register(@NotNull ClientSystem system) {
        CLIENT_SYSTEMS.add(system);
        IACP.LOGGER.debug("Registered V2 ClientSystem: {}", system.getClass().getSimpleName());
    }

    // ============================================================
    //  部件收集（遍历 Sable 内部 actor 列表，O(actors)）
    // ============================================================

    /**
     * 收集指定 SubLevel 中的所有 V2 {@link Part}。
     * <p>
     * 使用 Sable 的 {@code getBlockEntityActors()} 遍历，
     * 只迭代实际注册的 BlockEntity Actor，避免 O(chunk_volume) 扫描。
     * <p>
     * 仅收集实现了 {@link Part}（v2）的 actor，与 V1 的 Part 继承树相互独立，
     * 不会误采。
     *
     * @param subLevel 目标 SubLevel
     * @return V2 Part 列表（可能为空，不可为 null）
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
     * 注册所有服务端内置 V2 System（{@link TickSystem} 和 {@link PhysicsSystem}）。
     * <p>
     * 调用位置：{@link com.hainabaichuan75.iac_p.IACP#IACP IACP 构造器}。
     * 在此方法中添加新的服务端 V2 System 注册，避免分散在各处。
     * <p>
     * <b>客户端专用 System 不在此注册</b>——它们在
     * {@link #registerClientSystems()} 中，在 {@code IACPClient} 构造器中调用。
     * <p>
     * 当前无内置 V2 System，留空等待后续迁移。即使为空，调度器也会自动跳过空列表。
     */
    public static void registerV2ServerSystems() {
        // 在此添加 V2 服务端 System 注册
        // 例如：register(new V2SteeringSystem());
        // 当前无内置 V2 System，留空等待后续迁移
    }

    // ============================================================
    //  客户端 System 注册入口（在 IACPClient 构造器中调用一次）
    // ============================================================

    /**
     * 注册所有客户端内置 V2 System（{@link ClientSystem}）。
     * <p>
     * 调用位置：{@link com.hainabaichuan75.iac_p.IACPClient#IACPClient IACPClient 构造器}。
     * 在此方法中添加新的客户端 V2 System 注册。
     * <p>
     * 当前无内置 V2 System，留空等待后续迁移。即使为空，调度器也会自动跳过空列表。
     */
    public static void registerClientSystems() {
        // 在此添加 V2 客户端 System 注册
        // 例如：register(new V2AxisRenderSystem());
        // 当前无内置 V2 System，留空等待后续迁移
    }

    private V2SystemRegistry() {}
}
