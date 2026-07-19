package com.hainabaichuan75.iac_p.vehicle.provider;

/**
 * 云台能力 —— 使 BE 可被指定目标朝向，并报告当前角度与机械限位。
 */
public interface GimbalProvider {

    void setTargetYaw(double yaw);

    void setTargetPitch(double pitch);

    double getYaw();

    double getPitch();

    double getMaxYaw();

    double getMinYaw();

    double getMaxPitch();

    double getMinPitch();
}
