/*
 * 悬挂系统编译时常量 —— 所有悬挂/轮胎/转向的硬编码参数集中管理。
 *
 * 修改后重新编译即可生效，无需修改运行时 NBT 或配置文件。
 * 渲染器通过此类提供的静态方法获取视觉偏移常量。
 *
 * 坐标约定（方块局部坐标系，旋转到 facing 方向后）：
 *   X = 侧向（垂直于 facing 的水平方向）
 *   Y = 垂直（向上为正）
 *   Z = 纵深（沿 facing 方向向外为正）
 * 单位：格（1 格 = 1.0，如 7/16 = 0.4375）
 */
package com.hainabaichuan75.iac_p.block.suspension_test;

/**
 * 悬挂系统所有编译时常量和渲染器访问器的集中存放处。 不从任何地方继承或实现——纯粹是常量容器。
 */
public final class SuspensionConstants {

    // ====================================================================
    //  视觉偏移常量 —— 修改后重编即可生效
    // ====================================================================
    // ---- 弹簧（两个端点） ----
    /**
     * 弹簧在方块侧的附着点（X=侧向, Y=垂直, Z=纵深）
     */
    public static final double SPRING_BLOCK_X = 7.0 / 16.0;
    public static final double SPRING_BLOCK_Y = 7.0 / 16.0;
    public static final double SPRING_BLOCK_Z = 0.0;

    /**
     * 弹簧在轮子侧的附着点（X=侧向, Y=垂直, Z=纵深偏移量）
     */
    public static final double SPRING_WHEEL_X = 0.0;
    public static final double SPRING_WHEEL_Y = -2.0 / 16.0;
    public static final double SPRING_WHEEL_Z = 12.0 / 16.0;

    // ---- 转向轴/望远镜管（两个端点） ----
    /**
     * 转向轴在方块侧的支点
     */
    public static final double PIVOT_BLOCK_X = 0.0;
    public static final double PIVOT_BLOCK_Y = -6.0 / 16.0;
    public static final double PIVOT_BLOCK_Z = 0.0;

    /**
     * 转向轴在轮子侧的连接点（相对轮轴）
     */
    public static final double PIVOT_WHEEL_X = 0.0;
    public static final double PIVOT_WHEEL_Y = 0.0;
    public static final double PIVOT_WHEEL_Z = 0.0;

    // ---- 轮轴 ----
    /**
     * 轮轴支点在方块侧的位置
     */
    public static final double WHEEL_PIVOT_X = 0.0;
    public static final double WHEEL_PIVOT_Y = 0.0;
    public static final double WHEEL_PIVOT_Z = 10.0 / 16.0;

    /**
     * 轮子最终渲染位置（相对方块侧）
     */
    public static final double WHEEL_POS_X = 0.0;
    public static final double WHEEL_POS_Y = 0.0;
    public static final double WHEEL_POS_Z = 22.0 / 16.0;

    // ====================================================================
    //  力学参数常量
    // ====================================================================
    // ---- 悬挂弹簧参数 ----
    //
    //  线性弹簧-阻尼器模型：
    //    F_spring = springK × 压缩量
    //    F_damp   = dampingC × 压缩速度
    //    F_total  = F_spring + F_damp（以冲量形式施加到引擎）
    //
    //  质量自适应：轻车线性缩放保持柔顺，重车封顶防止触底。
    //  有效刚度(N/m) = min(nm, MASS_THRESHOLD) × STIFFNESS_PER_NM
    //  有效阻尼(N·s/m) = min(nm, MASS_THRESHOLD) × DAMPING_PER_NM
    //
    //  当前值效果：
    //    轻车(nm=1)→ springK=400 N/m, dampingC=10 N·s/m（柔软）
    //    重车(nm≥5)→ springK=2000 N/m, dampingC=50 N·s/m（硬朗）
    /**
     * 弹簧刚度基数（N/m per nm 单位质量）。
     */
    public static final double SPRING_STIFFNESS_PER_NM = 400.0;
    /**
     * 阻尼系数基数（N·s/m per nm 单位质量）。
     */
    public static final double DAMPING_COEFF_PER_NM = 10.0;
    /**
     * 质量自适应悬挂的过渡阈值（nm 单位质量）。
     */
    public static final double SUSPENSION_MASS_THRESHOLD = 5.0;
    /**
     * 悬挂最大伸展长度（格）。
     */
    public static final double MAX_EXT = 0.65;
    /**
     * 无轮子时的悬挂长度（格）。
     */
    public static final double NO_WHEEL_EXT = 0.5;

    // ---- 轮胎与地面摩擦 ----
    /**
     * 轮胎摩擦系数。最终摩擦力 = TIRE_FRICTION_COEFFICIENT × 地面摩擦系数 × 法向冲量 2026-06-26: 0.9
     * → 1.2（配合摩擦圆启用 + 上游转向，弥补轻车抓地不足）
     */
    public static final double TIRE_FRICTION_COEFFICIENT = 1.2;

    /**
     * 最小摩擦基数乘数。 摩擦基数 = max(弹簧静载冲量, nm × dt × MIN_IMPULSE_MULTIPLIER) 06-08 从
     * 500 → 30：消除 45g 摩擦预算，可感受抓地边界。
     */
    public static final double MIN_IMPULSE_MULTIPLIER = 30;

    // ---- 刹车参数 ----
    /**
     * 刹车强度倍数（0.0 = 无刹车，1.0 = 完全利用摩擦预算刹车）
     */
    public static final double BRAKE_STRENGTH = 0.5;

    // ---- 侧偏参数 ----
    /**
     * 侧滑阻尼系数（仅极低速时使用）。6.0 × 0.05 = 每 tick 衰减 30%。
     */
    public static final double SIDE_SLIP_DAMPING = 6.0;

    // ---- 转向参数 ----
    /**
     * 最大转向角（度）。左正右负。
     */
    public static final double MAX_STEERING_ANGLE = 30.0;
    /**
     * 最小转向角（度）。高速时限制到此值防止失控。
     */
    public static final double MIN_STEERING_ANGLE = 10.0;
    /**
     * 转向速率（度/tick）。
     */
    public static final double STEERING_SPEED = 10;
    /**
     * 是否自动归正。true = 无转向输入时自动回中。
     */
    public static final boolean AUTO_CENTER = true;

    // ---- 差速器参数 ----
    /**
     * 差速器滚动半径比。控制转弯时内外轮 RPM 差异幅度。
     */
    public static final double DIFFERENTIAL_RATIO = 0.37;

    // ---- 载荷转移参数 ----
    /**
     * 估算半轮距（格）。
     */
    public static final double HALF_TRACK = 1.0;

    // ====================================================================
    //  轮胎物理参数 —— 编译时默认值（真实 SI 单位）
    // ====================================================================
    /**
     * 胎面宽度（米）。典型值：0.20~0.35 m
     */
    public static final double DEFAULT_TREAD_WIDTH = 0.25;
    /**
     * 胎体刚度（N/m）。侧壁抵抗形变的能力
     */
    public static final double DEFAULT_CARCASS_STIFFNESS = 200000.0;
    /**
     * 最大安全胎压（Pa）。超过此值爆胎 ≈ 3.5 bar
     */
    public static final double DEFAULT_MAX_PRESSURE = 350000.0;
    /**
     * 标称胎压（Pa）。空载推荐充气压力 ≈ 2.2 bar
     */
    public static final double DEFAULT_NOMINAL_PRESSURE = 220000.0;
    /**
     * 胎内容积（m³）。约 50 升
     */
    public static final double DEFAULT_TIRE_VOLUME = 0.05;
    /**
     * 基础滚动阻力系数（无量纲）。06-08 从 0.015 改为 0.035（移除 /0.4 残骸后补偿）。
     */
    public static final double DEFAULT_CRR_BASE = 0.035;
    /**
     * 形变附加滚动阻力系数（无量纲）。
     */
    public static final double DEFAULT_CRR_DEFORMATION_GAIN = 0.08;

    // ====================================================================
    //  按键绑定默认值
    // ====================================================================
    public static final String DEFAULT_KEY_FORWARD = "key.keyboard.w";
    public static final String DEFAULT_KEY_BACKWARD = "key.keyboard.s";
    public static final String DEFAULT_KEY_LEFT = "key.keyboard.a";
    public static final String DEFAULT_KEY_RIGHT = "key.keyboard.d";
    public static final String DEFAULT_KEY_BRAKE = "key.keyboard.space";

    // ====================================================================    //  预计算常量（避免 hot path 重复运行时转换）
    // ==================================================================
    /**
     * MAX_STEERING_ANGLE 的弧度值。预计算避免每 tick 重复 Math.toRadians()。
     */
    public static final double MAX_STEERING_ANGLE_RAD = Math.toRadians(MAX_STEERING_ANGLE);
    /**
     * -MAX_STEERING_ANGLE 的弧度值。
     */
    public static final double MINUS_MAX_STEERING_ANGLE_RAD = Math.toRadians(-MAX_STEERING_ANGLE);
    /**
     * STEERING_SPEED 的弧度值。预计算避免每 tick 重复 Math.toRadians()。
     */
    public static final double STEERING_SPEED_RAD = Math.toRadians(STEERING_SPEED);
    /**
     * 2π（预计算避免重复 Math.PI * 2.0）。
     */
    public static final double TWO_PI = Math.PI * 2.0;
    /**
     * RPM → rad/s 转换因子：2π / 60。
     */
    public static final double RPM_TO_RAD_PER_S = Math.PI * 2.0 / 60.0;
    /**
     * RPM → rad/tick 转换因子：2π / 60 / 20（1 tick = 1/20 秒）。
     */
    public static final double RPM_TO_RAD_PER_TICK = Math.PI * 2.0 / 60.0 / 20.0;

    // ==================================================================    //  渲染器访问器（供 SuspensionTestRenderer 使用）
    // ====================================================================
    // 弹簧
    public static double springBlockX() {
        return SPRING_BLOCK_X;
    }

    public static double springBlockY() {
        return SPRING_BLOCK_Y;
    }

    public static double springBlockZ() {
        return SPRING_BLOCK_Z;
    }

    public static double springWheelX() {
        return SPRING_WHEEL_X;
    }

    public static double springWheelY() {
        return SPRING_WHEEL_Y;
    }

    public static double springWheelZ() {
        return SPRING_WHEEL_Z;
    }

    // 转向轴
    public static double pivotBlockX() {
        return PIVOT_BLOCK_X;
    }

    public static double pivotBlockY() {
        return PIVOT_BLOCK_Y;
    }

    public static double pivotBlockZ() {
        return PIVOT_BLOCK_Z;
    }

    public static double pivotWheelX() {
        return PIVOT_WHEEL_X;
    }

    public static double pivotWheelY() {
        return PIVOT_WHEEL_Y;
    }

    public static double pivotWheelZ() {
        return PIVOT_WHEEL_Z;
    }

    // 轮轴
    public static double wheelPivotX() {
        return WHEEL_PIVOT_X;
    }

    public static double wheelPivotY() {
        return WHEEL_PIVOT_Y;
    }

    public static double wheelPivotZ() {
        return WHEEL_PIVOT_Z;
    }

    public static double wheelPosX() {
        return WHEEL_POS_X;
    }

    public static double wheelPosY() {
        return WHEEL_POS_Y;
    }

    public static double wheelPosZ() {
        return WHEEL_POS_Z;
    }

    // ---- 力学参数访问器 ----
    /**
     * @return 轮胎摩擦系数
     */
    public static double tireFrictionCoefficient() {
        return TIRE_FRICTION_COEFFICIENT;
    }

    /**
     * @return 最大转向角（度）
     */
    public static double maxSteeringAngle() {
        return MAX_STEERING_ANGLE;
    }

    /**
     * @return 转向速率（度/tick）
     */
    public static double steeringSpeed() {
        return STEERING_SPEED;
    }

    /**
     * @return 是否启用自动归正
     */
    public static boolean autoCenter() {
        return AUTO_CENTER;
    }

    private SuspensionConstants() {
    }
}
