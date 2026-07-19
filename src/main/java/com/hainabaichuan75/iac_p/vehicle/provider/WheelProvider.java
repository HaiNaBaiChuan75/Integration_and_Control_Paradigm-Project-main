package com.hainabaichuan75.iac_p.vehicle.provider;

/**
 * 轮子能力 —— 使 BE 可被驱动转向与刹车，并报告运动状态。
 */
public interface WheelProvider {

    void setTorque(double torque);

    void setSteeringAngle(double angle);

    void setBrake(double intensity);

    double getAngularVelocity();

    double getSuspensionCompression();
}
