package com.hainabaichuan75.iac_p.part;

/**
 * 炮塔部件 —— 炮塔旋转状态的纯数据接口。
 * <p>
 * System 通过 {@code instanceof TurretPart} 找到炮塔，控制自动旋转或目标角度。
 * <p>
 * <b>纯数据约束</b>：只暴露角度 getter/setter，旋转计算在 {@code TurretAutoRotateSystem} 中。
 */
public interface TurretPart {

    /** @return 当前偏航角（度） */
    double getYaw();
    /** 设置偏航角（度） */
    void setYaw(double degrees);

    /** @return 当前俯仰角（度），正=上仰 */
    double getPitch();
    /** 设置俯仰角（度） */
    void setPitch(double degrees);

    /** 同时设置偏航和俯仰 */
    void setAngles(double yawDeg, double pitchDeg);

    /** @return 自动旋转是否开启 */
    boolean isAutoRotate();
    /** 开启/关闭自动旋转 */
    void setAutoRotate(boolean autoRotate);

    // ===== 渲染插值 =====
    /** @return 插值后的渲染偏航角（度） */
    float getRenderYaw(float partialTick);

    /** @return 插值后的渲染俯仰角（度） */
    float getRenderPitch(float partialTick);
}
