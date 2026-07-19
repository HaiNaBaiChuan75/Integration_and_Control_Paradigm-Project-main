package com.hainabaichuan75.iac_p.vehicle;

import java.util.UUID;

/**
 * 载具 —— 与 Sable SubLevel 一一对应的运行时对象。
 * <p>
 * 生命周期由 {@link VehicleRegistry}（作为 {@code SubLevelObserver}）自动管理：
 * 当 Sable 创建/加载一个 SubLevel 时自动注册 Vehicle；当 SubLevel 销毁时自动清理。
 * <p>
 * SubLevel UUID 是 Vehicle 的唯一标识，在构造后不可变更。
 * 其余字段为载具的运行时属性，后续由对应的 System 或 System 以外的逻辑填充更新。
 */
public class Vehicle {

    /** 关联的 Sable SubLevel UUID —— 一经绑定不可变 */
    private final UUID subLevelUUID;

    // ====== 运行时属性（后续由 System 更新） ======
    private double totalWeight;
    private double maximumSpeed;
    private double maximumMovingSpeed;
    private int totalElectricity;

    /**
     * @param subLevelUUID 关联的 Sable SubLevel UUID，非 null
     */
    public Vehicle(UUID subLevelUUID) {
        if (subLevelUUID == null) {
            throw new IllegalArgumentException("subLevelUUID must not be null");
        }
        this.subLevelUUID = subLevelUUID;
    }

    // ==================================================================
    //  身份标识
    // ==================================================================

    /** 关联的 Sable SubLevel UUID */
    public UUID getSubLevelUUID() {
        return subLevelUUID;
    }

    // ==================================================================
    //  属性访问器
    // ==================================================================

    public double getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(double totalWeight) {
        this.totalWeight = totalWeight;
    }

    public double getMaximumSpeed() {
        return maximumSpeed;
    }

    public void setMaximumSpeed(double maximumSpeed) {
        this.maximumSpeed = maximumSpeed;
    }

    public double getMaximumMovingSpeed() {
        return maximumMovingSpeed;
    }

    public void setMaximumMovingSpeed(double maximumMovingSpeed) {
        this.maximumMovingSpeed = maximumMovingSpeed;
    }

    public int getTotalElectricity() {
        return totalElectricity;
    }

    public void setTotalElectricity(int totalElectricity) {
        this.totalElectricity = totalElectricity;
    }

    @Override
    public String toString() {
        return "Vehicle{" +
                "uuid=" + subLevelUUID +
                '}';
    }
}
