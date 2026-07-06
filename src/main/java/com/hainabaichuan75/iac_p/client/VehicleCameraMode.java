package com.hainabaichuan75.iac_p.client;

/**
 * 载具参照摄像机模式 —— 以当前乘坐的物理结构（SubLevel）为参考系的摄像机模式。
 * <p>
 * 在骑乘 {@link com.hainabaichuan75.iac_p.entity.IACPSeatEntity} 时，
 * 通过额外按键（F6）循环切换，不替代原版 F5 三模式。
 * <p>
 * 模式说明：
 * <ul>
 *   <li>{@link #STRUCTURE_FIXED}：摄像机位置完全跟随 SubLevel 的全量位姿变换
 *       （含俯仰/侧倾），视野随车体同步晃动，"路感全通"。</li>
 *   <li>{@link #DIRECTION_STABILIZED}：摄像机位置跟随 SubLevel，但只应用偏航，
 *       保持地平线水平，画面稳定。</li>
 * </ul>
 */
public enum VehicleCameraMode {

    /**
     * 结构固定模式 —— 摄像机固定于载具结构坐标系上。
     * <p>
     * 局部偏移（例如后方上方）通过 SubLevel 的全量 {@code Pose3d.transformPosition()}
     * 变换到世界空间。车辆俯仰/侧倾时偏移随之旋转，摄像机与车体刚性连接。
     * <p>
     * 效果：驾驶中遇到颠簸或斜坡时画面一起晃动，沉浸感强。
     */
    STRUCTURE_FIXED,

    /**
     * 方向稳定模式 —— 摄像机位置跟随载具，但保持世界坐标系上方向。
     * <p>
     * 局部偏移仅应用 SubLevel 的偏航旋转和位移，不继承俯仰/侧倾。
     * 摄像机始终水平，地平线保持稳定。
     * <p>
     * 效果：画面稳定、不晕，适合长时间驾驶或高速行驶。
     */
    DIRECTION_STABILIZED;

    /**
     * @return 下一个循环模式（→ STRUCTURE_FIXED → DIRECTION_STABILIZED → null）
     */
    @javax.annotation.Nullable
    public VehicleCameraMode next() {
        return switch (this) {
            case STRUCTURE_FIXED -> DIRECTION_STABILIZED;
            case DIRECTION_STABILIZED -> null;
        };
    }

    /**
     * @return 模式的显示名称（供 HUD 调试/通知用）
     */
    public String displayName() {
        return switch (this) {
            case STRUCTURE_FIXED -> "结构固定";
            case DIRECTION_STABILIZED -> "方向稳定";
        };
    }
}
