/*
 * 动力系统编译时常量 —— 发动机、变速箱、换挡系统全部参数。
 *
 * ====== 架构说明 ======
 *
 * 油门线性扭矩模型（2026-06-27 简化）：
 *   RPM = IDLE + throttle × (MAX - IDLE)
 *   扭矩 = TORQUE_MIN + throttle × (TORQUE_MAX - TORQUE_MIN)  ← 油门线性，与RPM解耦
 *   扭矩不再依赖 RPM 曲线——踩多少油门就有多少扭矩，符合游戏直觉。
 *
 * 空档：变速箱断开，扭矩不输出到轮端。
 * 在档：TransmissionModel.computeOutput() 纯数学变换
 *   扭矩b = 扭矩a × 齿比  转速b = RPM / 齿比
 *   换挡真空期 6 tick → 动力中断
 *
 * 轮胎是唯一的扭矩限幅器。摩擦圆截掉的部分 = 打滑/空转。
 */
package com.hainabaichuan75.iac_p.content.blocks.cockpit;

/**
 * 动力系统所有编译时常量的集中存放处。
 * <p>
 * 修改后重新编译即可生效，无需运行时修改。
 */
public final class PowertrainConstants {

    // ====================================================================
    //  时间步长
    // ====================================================================
    /**
     * 引擎物理时间步长（秒/tick）。Minecraft 固定 20 TPS → 0.05s。
     */
    public static final double DT = 1.0 / 20.0;

    // ====================================================================
    //  发动机参数
    // ====================================================================
    /**
     * 发动机怠速转速（RPM）。无油门时稳定在此转速。
     */
    public static final double ENGINE_IDLE_RPM = 800.0;

    /**
     * 发动机红线转速（RPM）。最大转速上限。
     */
    public static final double ENGINE_MAX_RPM = 6000.0;

    /**
     * 发动机最大扭矩（Nm）。100% 油门时的输出扭矩。 与车辆质量解耦——轻车加速快、重车加速慢，天然产生驾驶差异。 当前值 5.0 Nm × 齿比
     * 4.0 × 主减速比 14.0 = 280 Nm 轮端扭矩 / 4 轮 = 70 Nm/轮。 配合摩擦系数 1.2 和
     * MIN_IMPULSE_MULTIPLIER=30，轻车可打滑、重车稳步走。
     */
    public static final double TORQUE_MAX = 5.0;

    /**
     * 发动机最小扭矩（Nm）。0% 油门时的输出扭矩。 车辆静止怠速时仍有此扭矩输出，使起步平顺不窜动。 当前值 4.0 Nm = 峰值的
     * 80%，与旧扭矩曲线怠速点持平。
     */
    public static final double TORQUE_MIN = 4.0;

    // ====================================================================
    //  连续油门参数
    // ====================================================================
    /**
     * 油门位置变化率（/tick）。每秒 0→1 需要 40 tick（约 2 秒）。
     */
    public static final double THROTTLE_RATE = 0.025;

    /**
     * S 键主动减油门速率（/tick）。1→0 约 1.25 秒。
     */
    public static final double THROTTLE_BRAKE_RATE = 0.04;

    // ====================================================================
    //  变速箱齿比
    // ====================================================================
    /**
     * 前进档齿比（1档到5档）。5 档为 1.0 直接档。
     */
    public static final double[] GEAR_RATIOS = {4.0, 2.5, 1.6, 1.2, 1.0};

    /**
     * 倒车档齿比（负值表示反向旋转）。
     */
    public static final double REVERSE_RATIO = -3.5;

    /**
     * 主减速比。所有档位的齿比都要乘以此值才是最终传动比。 取值 14.0 使极速约 120 km/h。
     */
    public static final double FINAL_DRIVE_RATIO = 14.0;

    /**
     * 前进档数量
     */
    public static final int NUM_FORWARD_GEARS = GEAR_RATIOS.length;

    // ====================================================================
    //  换挡参数
    // ====================================================================
    /**
     * 换挡耗时（tick）。6 tick ≈ 300ms，足够产生动力中断感但不会令人烦躁。
     */
    public static final int SHIFT_TIME_TICKS = 6;

    // ====================================================================
    //  辅助方法
    // ====================================================================
    /**
     * @return 指定档位的齿比绝对值，空档/倒车返回 0
     */
    public static double getRatioForGear(int gear) {
        if (gear <= 0) {
            return 0;
        }
        if (gear > NUM_FORWARD_GEARS) {
            return 0;
        }
        return GEAR_RATIOS[gear - 1];
    }

    /**
     * @return 当前齿比（含符号），空档返回 0
     */
    public static double getCurrentRatio(int currentGear) {
        if (currentGear == 0) {
            return 0;
        }
        if (currentGear == -1) {
            return REVERSE_RATIO;
        }
        return GEAR_RATIOS[currentGear - 1];
    }

    /**
     * @return 档位的人类可读名称
     */
    public static String gearName(int gear) {
        return switch (gear) {
            case -1 ->
                "R";
            case 0 ->
                "N";
            default ->
                String.valueOf(gear);
        };
    }

    /**
     * @return 有效传动比 = |齿比| × 主减速比
     */
    public static double getEffectiveRatio(int gear) {
        if (gear == 0) {
            return 0;
        }
        double ratio = getCurrentRatio(gear);
        return Math.abs(ratio) * FINAL_DRIVE_RATIO;
    }

    private PowertrainConstants() {
    }
}
