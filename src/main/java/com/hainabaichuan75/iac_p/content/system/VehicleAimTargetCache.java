package com.hainabaichuan75.iac_p.content.system;

import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每辆车的瞄准目标点缓存。
 * <p>
 * 写入方：{@code MachineGunTargetC2SPacket.handle()}（客户端→服务端的命中坐标）
 * 读取方：{@link WeaponAimSystem#onTick}（每逻辑 tick 读取）
 * <p>
 * 使用 ConcurrentHashMap 保证线程安全。
 */
public final class VehicleAimTargetCache {

    private static final Map<UUID, Vec3> TARGETS = new ConcurrentHashMap<>();

    /**
     * 更新目标点。
     *
     * @param vehicleUUID 载具 SubLevel UUID
     * @param target      目标点世界坐标
     */
    public static void setTarget(UUID vehicleUUID, Vec3 target) {
        TARGETS.put(vehicleUUID, target);
    }

    /**
     * 读取目标点。
     *
     * @param vehicleUUID 载具 SubLevel UUID
     * @return 目标点世界坐标，无目标时返回 null
     */
    @Nullable
    public static Vec3 getTarget(UUID vehicleUUID) {
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
