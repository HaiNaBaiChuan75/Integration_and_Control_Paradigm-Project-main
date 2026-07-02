package com.hainabaichuan75.iac_p.part;

/**
 * 发动机部件 —— 引擎状态的纯数据接口。
 * <p>
 * System 通过 {@code instanceof EnginePart} 找到引擎，读写 RPM/扭矩。
 * <p>
 * <b>纯数据约束</b>：只暴露状态 getter/setter，引擎计算在 {@code EnginePowerSystem} 中。
 */
public interface EnginePart {

    /** @return 发动机当前转速（RPM） */
    double getRpm();
    /** 设置发动机转速 */
    void setRpm(double rpm);

    /** @return 发动机当前输出扭矩（Nm） */
    double getTorque();
    /** 设置发动机输出扭矩 */
    void setTorque(double torque);

    /** @return 发动机是否已熄火 */
    boolean isStalled();
    /** 设置熄火状态 */
    void setStalled(boolean stalled);

    /** @return 当前油门位置 [0.0, 1.0] */
    double getThrottleLevel();
}
