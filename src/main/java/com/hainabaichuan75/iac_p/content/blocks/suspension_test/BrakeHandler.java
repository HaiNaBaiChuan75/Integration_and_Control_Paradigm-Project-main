/*
 * 手刹物理处理器 —— 计算轮子抱死时的滑动摩擦力。
 *
 * 职责：
 *   1. 从 physicsTick 中提取手刹模式（braking=true）的物理计算
 *   2. 输入：总速度、摩擦系数、法向冲量、刹车强度常量
 *   3. 输出：纵向力和侧向力分量（沿总速度反方向）
 *
 * 物理模型：
 *   - 轮子抱死，不再滚动
 *   - 车辆依靠轮胎-地面滑动摩擦减速
 *   - 驱动力 → 切断
 *   - 滚动阻力 → 切断（轮子不转，无滚动摩擦）
 *   - 侧滑阻尼 → 切断（轮子不转，无回正力矩）
 *   - 摩擦力沿总速度反方向，而非仅纵向
 *   - 摩擦力幅值 = BRAKE_STRENGTH × μ × springImpulse
 *
 * ⚠ 关键：必须用 springImpulse（真实法向冲量），不能用 frictionBasis
 *   （被 MIN_IMPULSE_MULTIPLIER 膨胀了 ~51 倍）。
 *
 * 用法：
 *   BrakeHandler.Result result = BrakeHandler.compute(
 *       forwardSpeed, lateralSpeed, mu, springImpulse, BRAKE_STRENGTH);
 *   // result.longForce, result.latForce
 */
package com.hainabaichuan75.iac_p.content.blocks.suspension_test;

/**
 * 手刹物理处理器。
 * <p>
 * 纯函数：给定运动状态和摩擦参数，输出沿总速度反方向的滑动摩擦力分量。
 */
public final class BrakeHandler {

    private BrakeHandler() {}

    /**
     * 手刹模式的计算结果。
     *
     * @param longForce  纵向力（沿前进方向，负值=减速）
     * @param latForce   侧向力（沿侧滑方向，负值=减速）
     */
    public record Result(double longForce, double latForce) {}

    /**
     * 计算手刹（轮子抱死）时的滑动摩擦力。
     *
     * @param forwardSpeed  前进速度（m/s），沿 fwdD 方向
     * @param lateralSpeed  侧向速度（m/s），沿 sideD 方向
     * @param mu            综合摩擦系数（轮胎系数 × 地面系数）
     * @param springImpulse 弹簧静载冲量（Ns）= |弹簧压缩 × 刚度 × dt|
     * @param brakeStrength 刹车强度系数（通常 ≈ 0.5）
     * @return 纵向力和侧向力的冲量分量
     */
    public static Result compute(
            double forwardSpeed, double lateralSpeed,
            double mu, double springImpulse, double brakeStrength) {

        double totalSpeed = Math.sqrt(forwardSpeed * forwardSpeed + lateralSpeed * lateralSpeed);
        if (totalSpeed > 1e-8) {
            // 使用弹簧静载冲量（真实法向力），而非膨胀后的 frictionBasis
            double brakeMag = brakeStrength * mu * springImpulse;
            // 沿总速度反方向分解摩擦力
            double longForce = -(forwardSpeed / totalSpeed) * brakeMag;
            double latForce = -(lateralSpeed / totalSpeed) * brakeMag;
            return new Result(longForce, latForce);
        }
        // totalSpeed ≈ 0：车辆已静止，无需额外力
        return new Result(0.0, 0.0);
    }
}
