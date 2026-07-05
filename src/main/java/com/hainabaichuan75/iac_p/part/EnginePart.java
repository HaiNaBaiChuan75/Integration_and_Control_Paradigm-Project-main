package com.hainabaichuan75.iac_p.part;

import com.hainabaichuan75.iac_p.ecs.part.Part;
import com.hainabaichuan75.iac_p.system.TorqueDistributionSystem;

/**
 * 引擎 —— 动力源的扭矩与转速数据契约。
 * <p>
 * 定义 {@code TorqueDistributionSystem} 与引擎之间的数据通道：
 * 当前扭矩由 System 基于油门输入计算后写入，
 * 最大扭矩和最大转速为引擎的规格参数（只读）。
 * <p>
 * <b>为什么用扭矩而非功率</b>：
 * 扭矩是驱动链的原始物理量，与 {@link DriveWheel#setTorqueInput(double)} 类型一致，
 * 功率 = 扭矩 × 角速度，是导出量。在扭矩分配这一层，用扭矩无需额外换算。
 *
 * @see DriveWheel
 * @see TorqueDistributionSystem
 */
public interface EnginePart extends Part {

    // ==================================================================
    //  运行时状态（由 TorqueDistributionSystem 写入）
    // ==================================================================

    /**
     * @return 当前输出扭矩（Nm），正 = 前进方向
     */
    double getTorque();

    /**
     * 更新当前输出扭矩。
     * <p>
     * 由 {@code TorqueDistributionSystem} 在逻辑 tick 中
     * 基于油门输入和引擎扭矩曲线计算后写入。
     *
     * @param torque 扭矩（Nm）
     */
    void setTorque(double torque);

    // ==================================================================
    //  规格参数（只读，引擎固有属性）
    // ==================================================================

    /**
     * @return 最大扭矩（Nm），引擎铭牌参数
     */
    double getMaxTorque();

    /**
     * @return 最大转速（RPM），即红线转速
     */
    double getMaxRpm();
}
