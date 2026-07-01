package com.hainabaichuan75.iac_p.ecs.part;

/**
 * 车轮部件 —— 悬挂/车轮状态的纯数据接口。
 * <p>
 * System 通过 {@code instanceof WheelPart} 找到车轮，读取物理状态、写入驱动扭矩。
 * <p>
 * <b>纯数据约束</b>：只暴露状态 getter/setter，不包含悬挂力计算或轮胎模型。
 */
public interface WheelPart {

    // ===== 物理状态 =====
    /** @return 本轮当前实际轮端 RPM，由物理速度推算 */
    double getCurrentWheelRpm();

    /** @return 每轮可用扭矩（Nm），由 EnginePowerSystem 写入 */
    double getTorqueInput();
    /** 设置轮端扭矩输入（由 EnginePowerSystem 写入） */
    void setTorqueInput(double torque);

    /** @return 当前实际转向角（弧度） */
    double getSteeringAngle();
    /** 设置转向角（由 SteeringSystem 写入） */
    void setSteeringAngle(double angle);

    /** @return 轮子半径（米），默认 0.5 */
    double getWheelRadius();

    // ===== 控制输入 =====
    /** @return 刹车是否激活 */
    boolean isBraking();
    /** @return 是否有前进油门 */
    boolean isThrottleForward();
    /** @return 是否有后退油门 */
    boolean isThrottleBackward();
    /** @return 是否有任何方向油门输入 */
    boolean hasThrottle();

    /** @return WASD 写入的目标转向角（弧度），尚未经过速度自适应 */
    double getTargetSteeringYaw();

    /** @return 当前匀速转向插值角（弧度），即 chasingYaw */
    double getChasingYaw();

    // ===== 抓地状态 =====
    /** @return 当前轮是否抓地（true=抓地, false=打滑） */
    boolean isGripping();

    /** @return 摩擦需求比（0.5=有余量, 2.0=打滑） */
    double getFrictionDemandRatio();

    /** @return 轮子是否离地 */
    boolean isLifted();

    // ===== 悬挂状态 =====
    /** @return 当前悬挂伸展量 */
    double getExtension();
    /** @return 上一 tick 悬挂伸展量（用于 partialTick 插值） */
    double getLastExtension();

    // ===== 引擎负载报告 =====
    /** @return P 控制器原始力需求（摩擦圆约束前） */
    double getPControllerDemand();
    /** @return 滚动阻力幅值 */
    double getRollingResistanceMag();
    /** @return 本轮消耗的轮端扭矩（Nm） */
    double getConsumedWheelTorque();

    // ===== 轮胎参数 =====
    /** @return 当前胎压（Pa） */
    double getNominalPressure();
    /** 设置胎压 */
    void setNominalPressure(double pressure);

    // ===== 视觉状态 =====
    /** @return 当前轮子旋转角（度） */
    double getAngle();
    /** @return 上一 tick 轮子旋转角 */
    double getLastAngle();
    /** @return 轮子角速度 */
    double getAngVel();

    /** @return 是否为横移轮（NS 朝向，Q/E 控制） */
    boolean isStrafeWheel();
}
