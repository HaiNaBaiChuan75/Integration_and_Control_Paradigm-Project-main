package com.hainabaichuan75.iac_p.ecs.part;

/**
 * 武器挂载点 —— 武器瞄准状态的纯数据接口。
 * <p>
 * System 通过 {@code instanceof WeaponMount} 找到武器部件，写入瞄准角度。
 * <p>
 * <b>纯数据约束</b>：只暴露目标角度 getter/setter，不暴露瞄准计算方法。
 * 瞄准解算（坐标转换、角度计算）在 {@code WeaponAimSystem} 中完成。
 */
public interface WeaponMount {

    /** 设置目标偏航角（度，载具局部空间） */
    void setTargetYaw(double yaw);

    /** 设置目标俯仰角（度，正=上仰） */
    void setTargetPitch(double pitch);

    /** @return 当前目标偏航角（度） */
    double getTargetYaw();

    /** @return 当前目标俯仰角（度） */
    double getTargetPitch();

    /** @return 当前实际偏航角（度，含平滑插值后的值） */
    double getCurrentYaw();

    /** @return 当前实际俯仰角（度，含平滑插值后的值） */
    double getCurrentPitch();
}
