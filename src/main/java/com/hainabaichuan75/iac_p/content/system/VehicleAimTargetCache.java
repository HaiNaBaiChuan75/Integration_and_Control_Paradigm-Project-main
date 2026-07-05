package com.hainabaichuan75.iac_p.content.system;

import org.joml.Vector3dc;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每辆车的瞄准目标点缓存。
 * <p>
 * 写入方：{@code VehicleControlC2SPacket.handle()}（客户端→服务端的命中坐标）
 * 读取方：WeaponAimSystem（每逻辑 tick 读取）
 * <p>
 * 使用 JOML {@link Vector3dc} 与 {@code PartBlockEntity.partLogicalPose()} 坐标体系一致。
 * <p>
 * 使用 ConcurrentHashMap 保证线程安全。
 */
public final class VehicleAimTargetCache {

    private static final Map<UUID, Vector3dc> TARGETS = new ConcurrentHashMap<>();

    /**
     * 更新目标点。
     *
     * @param vehicleUUID 载具 SubLevel UUID
     * @param target      目标点世界坐标（JOML 向量）
     */
    public static void setTarget(UUID vehicleUUID, Vector3dc target) {
        TARGETS.put(vehicleUUID, target);
    }

    /**
     * 读取目标点。
     *
     * @param vehicleUUID 载具 SubLevel UUID
     * @return 目标点世界坐标，无目标时返回 null
     */
    @Nullable
    public static Vector3dc getTarget(UUID vehicleUUID) {
        return TARGETS.get(vehicleUUID);
    }

    /**
     * 清除目标点（下车时调用）。
     */
    public static void clearTarget(UUID vehicleUUID) {
        TARGETS.remove(vehicleUUID);
    }

    private VehicleAimTargetCache() {}
}
