package com.hainabaichuan75.iac_p.vehicle;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.events.PhysicsAssembleHandler;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 载具注册表 —— 同时作为 Sable 的 {@link SubLevelObserver}，
 * 自动跟踪 SubLevel 的创建和销毁，维护与之一一对应的 {@link Vehicle}。
 * <p>
 * <b>生命周期映射</b>：
 * <pre>
 *   SubLevel 创建（装配/分裂/加载）  →  onSubLevelAdded   → 注册 Vehicle
 *   SubLevel 销毁（拆解/越界/质量零） →  onSubLevelRemoved → 移除 Vehicle
 * </pre>
 * <p>
 * 注册通过 {@link #registerObserver} 方法，在 {@link PhysicsAssembleHandler} 初始化时完成。
 */
public class VehicleRegistry implements SubLevelObserver {

    /** 单例 */
    public static final VehicleRegistry INSTANCE = new VehicleRegistry();

    /** 是否已注册到 Sable 容器 */
    private static boolean registered = false;

    /** UUID → Vehicle 映射 */
    private final Map<UUID, Vehicle> vehicles = new ConcurrentHashMap<>();

    // ==================================================================
    //  构造
    // ==================================================================

    private VehicleRegistry() {
    }

    // ==================================================================
    //  SubLevelObserver 实现
    // ==================================================================

    /**
     * SubLevel 被创建或加载时调用。
     * <p>
     * 注意：SubLevel 分裂时，原 SubLevel 不触发此回调（UUID 不变），
     * 仅新分裂出的 SubLevel 触发 —— 原 Vehicle 在注册表中不受影响。
     */
    @Override
    public void onSubLevelAdded(SubLevel subLevel) {
        UUID uuid = subLevel.getUniqueId();
        if (uuid == null) return; // 安全兜底

        vehicles.computeIfAbsent(uuid, id -> {
            IACP.LOGGER.info("[VehicleRegistry] 注册载具: {}", id);
            return new Vehicle(id);
        });
    }

    /**
     * SubLevel 被移除时调用（拆解/越界/质量为零等）。
     */
    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        UUID uuid = subLevel.getUniqueId();
        if (uuid == null) return;

        Vehicle removed = vehicles.remove(uuid);
        if (removed != null) {
            IACP.LOGGER.info("[VehicleRegistry] 移除载具: {} (原因: {})", uuid, reason);
        }
    }

    // ==================================================================
    //  查询
    // ==================================================================

    /**
     * 根据 UUID 获取对应的 Vehicle。
     *
     * @param subLevelUUID SubLevel UUID
     * @return Vehicle 实例，未注册时返回 null
     */
    @Nullable
    public static Vehicle get(UUID subLevelUUID) {
        return INSTANCE.vehicles.get(subLevelUUID);
    }

    /**
     * 根据 SubLevel 获取对应的 Vehicle。
     *
     * @param subLevel Sable SubLevel
     * @return Vehicle 实例，未注册时返回 null
     */
    @Nullable
    public static Vehicle get(SubLevel subLevel) {
        return subLevel != null ? get(subLevel.getUniqueId()) : null;
    }

    /**
     * 判断指定的 SubLevel UUID 是否已注册为载具。
     */
    public static boolean exists(UUID subLevelUUID) {
        return INSTANCE.vehicles.containsKey(subLevelUUID);
    }

    // ==================================================================
    //  生命周期
    // ==================================================================

    /**
     * 向 Sable 的 SubLevelContainer 注册此观察者。
     * <p>
     * 由 {@code PhysicsAssembleHandler} 在首次装配/拆解时懒初始化。
     * 多次调用安全（只会注册一次）。
     *
     * @param container Sable SubLevel 容器
     */
    public static void registerObserver(dev.ryanhcode.sable.api.sublevel.SubLevelContainer container) {
        if (registered) return;
        if (container == null) return;

        container.addObserver(INSTANCE);
        registered = true;
        IACP.LOGGER.info("[VehicleRegistry] 已注册到 SubLevelContainer");
    }

    /** 重置注册状态（主要用于测试 / 服务端重启） */
    public static void reset() {
        INSTANCE.vehicles.clear();
        registered = false;
    }
}
