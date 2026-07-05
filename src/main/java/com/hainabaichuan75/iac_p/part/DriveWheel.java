package com.hainabaichuan75.iac_p.part;

/**
 * 驱动轮 —— 能接收引擎动力的轮子。
 * <p>
 * 在基接口 {@link WheelPart} 之上增加扭矩输入数据通道。
 * {@code EnginePowerSystem} 写入分配给该轮的扭矩，
 * {@code SuspensionPhysicsSystem} 读取用于推进力计算。
 * <p>
 * 车辆的前轮通常是 {@code SteeringWheel + DriveWheel}，
 * 纯驱动轮（如卡车后轴）可只实现本接口。
 *
 * @see WheelPart
 * @see SteeringWheel
 */
public interface DriveWheel extends WheelPart {

    // ==================================================================
    //  扭矩通道
    // ==================================================================

    /**
     * @return 引擎分配给本轮的扭矩（Nm），正 = 前进，负 = 后退
     */
    double getTorqueInput();

    /**
     * 设置引擎分配给本轮的扭矩。
     * <p>
     * 由 {@code EnginePowerSystem} 在逻辑 tick 中写入
     * （含差速器偏置分配），
     * {@code SuspensionPhysicsSystem} 在物理 tick 中读取
     * 用于推进力计算。
     *
     * @param torque 扭矩值（Nm）
     */
    void setTorqueInput(double torque);
}
