package com.hainabaichuan75.iac_p.ecs.part;

/**
 * 变速箱部件 —— 档位/传动状态的纯数据接口。
 * <p>
 * System 通过 {@code instanceof TransmissionPart} 找到变速箱，读写档位状态。
 * <p>
 * <b>纯数据约束</b>：变速箱算法在 {@code TransmissionModel} 和 {@code EnginePowerSystem} 中。
 */
public interface TransmissionPart {

    /** @return 当前档位：-1=R, 0=N, 1~5=前进档 */
    int getGear();
    /** 设置档位 */
    void setGear(int gear);

    /** @return 是否正在换挡（动力中断期间） */
    boolean isShifting();
    /** 设置换挡状态 */
    void setShifting(boolean shifting);

    /** @return 换挡倒计时（tick），归零时完成换挡 */
    int getShiftingTimer();
    /** 设置换挡倒计时 */
    void setShiftingTimer(int ticks);

    /** @return 换挡目标档位 */
    int getTargetShiftGear();
    /** 设置换挡目标档位 */
    void setTargetShiftGear(int gear);

    /** @return 降档自动补油目标 RPM（Rev-Match） */
    double getRevMatchTargetRpm();
    /** 设置 Rev-Match 目标 RPM */
    void setRevMatchTargetRpm(double rpm);

    /** @return 轮端目标 RPM（由发动机当前转速通过齿比推算），空档返回 0 */
    double getTargetWheelRpm();

    /** @return 方向符号：+1 前进, -1 倒车, 0 空档/熄火 */
    double getDirectionSign();

    /** @return 上次更新时在档位中的轮子总数（用于扭矩均摊） */
    int getLastWheelCount();
    /** 设置轮子计数 */
    void setLastWheelCount(int count);

    // ===== 自动变速状态 =====
    /** @return 自动变速是否启用 */
    boolean isAutoShiftEnabled();

    /** @return 升档节流计数器 */
    int getUpshiftTimer();
    /** 设置升档节流计数器 */
    void setUpshiftTimer(int timer);

    /** @return 上次升档检查时的速度（m/s） */
    double getLastUpshiftSpeed();
    /** 设置上次升档检查速度 */
    void setLastUpshiftSpeed(double speed);

    /** @return 降档持续计时器 */
    int getDownshiftStallTimer();
    /** 设置降档持续计时器 */
    void setDownshiftStallTimer(int timer);

    /** @return 上次换挡的游戏刻 */
    int getLastShiftTick();
    /** 设置上次换挡的游戏刻 */
    void setLastShiftTick(int tick);
}
