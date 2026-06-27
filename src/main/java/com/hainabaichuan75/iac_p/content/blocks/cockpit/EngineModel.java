/*
 * 发动机模型 —— 油门线性扭矩模型。
 *
 * 2026-06-27 简化：移除 RPM 扭矩曲线，扭矩仅与油门线性相关。
 *
 *   RPM   = IDLE + throttle × (MAX - IDLE)
 *   扭矩  = TORQUE_MIN + throttle × (TORQUE_MAX - TORQUE_MIN)
 *
 * 扭矩与 RPM 完全解耦——踩多少油门就有多少扭矩，符合游戏直觉。
 * 发动机不模拟熄火（油门直控模式下 RPM 由油门决定，不会跌到熄火阈值以下）。
 */
package com.hainabaichuan75.iac_p.content.blocks.cockpit;

/**
 * 发动机计算工具类。
 * <p>
 * 所有方法均为纯静态函数，不持有状态。状态由 CockpitBlockEntity 管理。
 */
public final class EngineModel {

    // ==================================================================
    //  油门直控模式 — 油门线性扭矩
    // ==================================================================
    /**
     * 油门直控模式的发动机状态结果。
     *
     * @param rpm 目标转速（RPM）= IDLE + throttle × (MAX - IDLE)
     * @param engineTorque 发动机当前输出扭矩（Nm），油门线性
     */
    public record ThrottleControlledResult(
            double rpm,
            double engineTorque
            ) {

    }

    /**
     * 油门直控模式：油门直接决定转速和扭矩，两者都与 RPM 解耦。
     *
     * <p>
     * 油门 100% 已包含内部损耗，不再需要单独摩擦项。
     *
     * <ul>
     * <li>RPM = IDLE + throttle × (MAX - IDLE) ← 油门直接定位</li>
     * <li>Torque = TORQUE_MIN + throttle × (TORQUE_MAX - TORQUE_MIN) ←
     * 油门线性</li>
     * </ul>
     *
     * @param throttleLevel 油门位置 [0.0, 1.0]
     * @return 油门直控结果
     */
    public static ThrottleControlledResult computeThrottleControlledRun(double throttleLevel) {
        double rpm = PowertrainConstants.ENGINE_IDLE_RPM
                + throttleLevel * (PowertrainConstants.ENGINE_MAX_RPM - PowertrainConstants.ENGINE_IDLE_RPM);
        double torque = PowertrainConstants.TORQUE_MIN
                + throttleLevel * (PowertrainConstants.TORQUE_MAX - PowertrainConstants.TORQUE_MIN);
        return new ThrottleControlledResult(rpm, torque);
    }

    // ==================================================================
    //  油门控制
    // ==================================================================
    /**
     * 计算油门位置的双向调整。
     *
     * <ul>
     * <li>direction > 0（Home 按下）→ 柔和加速</li>
     * <li>direction < 0（End 按下）→ 主动减油</li>
     *   <li>dir
     * ection = 0（无输入）→ 保持当前油门，无自行衰减</li>
     * </ul>
     *
     * @param throttleLevel 当前油门位置
     * @param direction 油门方向：+1=加油, -1=减油, 0=保持不变
     * @return 调整后的油门位置
     */
    public static double updateThrottle(double throttleLevel, int direction) {
        if (direction > 0) {
            return Math.min(1.0, throttleLevel + PowertrainConstants.THROTTLE_RATE);
        } else if (direction < 0) {
            return Math.max(0.0, throttleLevel - PowertrainConstants.THROTTLE_BRAKE_RATE);
        } else {
            return throttleLevel; // 无自行衰减，保持当前值
        }
    }

    private EngineModel() {
    }
}
