package com.hainabaichuan75.iac_p.core.system;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.core.part.PartBlockEntity;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * VehicleSystem 注册表 — 所有 System 在此注册，调度器从此读取。
 * <p>
 * 线程安全：注册仅在 Mod 构造阶段完成，运行时只读。
 * <p>
 * 设计原则：
 * <ul>
 * <li><b>注册与调度分离</b>：注册表只负责收集，调度器只负责执行</li>
 * <li><b>渐进采用</b>：现有 BE 无需立即迁移，新功能按 System 写即可</li>
 * <li><b>轻量</b>：无框架依赖，纯 Java 接口 + NeoForge 事件</li>
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
     * 调用位置：{@link IACP#IACP} 构造器中。
     * 在此方法中添加新的 System 注册，而不是分散在各处。
     */
    public static void registerAll() {
        // 在此处添加所有 System 的注册
        // register(new WeaponAimSystem());
        // register(new SuspensionPhysicsSystem());
        // register(new SpeedHudSystem());

        // ⚠️ AxisRenderSystem 使用 Minecraft 客户端类，需在客户端侧注册
        // 见 IACPClient.java 中的对应注册
    }

    private VehicleSystemRegistry() {}
}
