package com.hainabaichuan75.iac_p.part;

/**
 * 转向轮 —— 能绕 Y 轴（天向）改变牵引方向的轮子。
 * <p>
 * 在基接口 {@link WheelPart} 之上增加转向输入数据通道。
 * 输入为归一化标量 {@code [-1, 1]}，物理 System 读取后将其映射为实际偏转角。
 * <p>
 * 车辆的前轮通常是 {@code SteeringWheel + DriveWheel}，
 * 纯转向轮（如拖车前轴）可只实现本接口。
 *
 * @see WheelPart
 * @see DriveWheel
 */
public interface SteeringWheel extends WheelPart {

    // ==================================================================
    //  转向输入（归一化标量）
    // ==================================================================

    /**
     * @return 当前转向输入，{@code [-1, 1]} 范围归一化标量。
     * {@code 0 = 回正}，正值 = 右转，负值 = 左转。
     */
    double getSteeringInput();

    /**
     * 设置当前转向输入。
     * <p>
     * 由 {@code SteeringSystem} 在逻辑 tick 中写入，
     * 物理 System 读取后将其映射为实际偏转角，然后旋转
     * {@code axialNormal} 获得偏转后的牵引方向。
     * <p>
     * 值为 {@code [-1, 1]} 范围内的归一化标量：
     * <ul>
     *   <li>{@code 0 = 回正}</li>
     *   <li>正值 = 右转（x+ 方向）</li>
     *   <li>负值 = 左转（x- 方向）</li>
     * </ul>
     *
     * @param steeringInput 转向输入标量 {@code [-1, 1]}
     */
    void setSteeringInput(double steeringInput);
}
