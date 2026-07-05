package com.hainabaichuan75.iac_p.part.field;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * 偏航/俯仰值类型 —— 表示一个方向姿态。
 * <p>
 * 角度单位为度，遵循 Minecraft / 载具局部坐标系约定：
 * <ul>
 *   <li>z- = 前方，x+ = 右方，y+ = 上方</li>
 *   <li>偏航（Yaw）：绕 Y 轴旋转，{@code atan2(-x, -z)}（CCW+），范围 [-180, 180)</li>
 *   <li>俯仰（Pitch）：绕 X 轴旋转，{@code atan2(-y, sqrt(x²+z²))}，范围 [-90, 90]</li>
 * </ul>
 * <p>
 * <b>不变式</b>：构造后自动归一化 —— yaw 归一化到 (-180, 180]，pitch 钳制到 [-90, 90]。
 *
 * @param yaw   偏航角（度）
 * @param pitch 俯仰角（度）
 */
public record YawPitch(double yaw, double pitch) {

    // ===== 静态常量 =====

    /**
     * 零姿态：水平，朝前
     */
    public static final YawPitch ZERO = new YawPitch(0, 0);
    /**
     * 前：yaw=0
     */
    public static final YawPitch FORWARD = ZERO;
    /**
     * 后：yaw=±180
     */
    public static final YawPitch BACKWARD = new YawPitch(180, 0);
    /**
     * 右：yaw=-90（CCW- 方向）
     */
    public static final YawPitch RIGHT = new YawPitch(-90, 0);
    /**
     * 左：yaw=90（CCW+ 方向）
     */
    public static final YawPitch LEFT = new YawPitch(90, 0);
    /**
     * 正上方
     */
    public static final YawPitch UP = new YawPitch(0, 90);
    /**
     * 正下方
     */
    public static final YawPitch DOWN = new YawPitch(0, -90);

    private static final String TAG_YAW = "yaw";
    private static final String TAG_PITCH = "pitch";

    /**
     * 紧凑构造：自动归一化角度
     */
    public YawPitch {
        yaw = normalizeYaw(yaw);
        pitch = clampPitch(pitch);
    }

    /**
     * 写入 NBT {@link CompoundTag}
     */
    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putDouble(TAG_YAW, yaw);
        tag.putDouble(TAG_PITCH, pitch);
        return tag;
    }

    /**
     * 从 NBT {@link CompoundTag} 读取
     */
    @NotNull
    public static YawPitch fromTag(@NotNull CompoundTag tag) {
        return new YawPitch(tag.getDouble(TAG_YAW), tag.getDouble(TAG_PITCH));
    }

    // ===== 归一化 =====

    /**
     * 将偏航角归一化到 (-180, 180]。
     * <p>
     * 使用 {@code Math.IEEEremainder} 确保结果稳定、对称。
     */
    public static double normalizeYaw(double yaw) {
        // IEEEremainder 返回 [-180, 180)，将 -180 映射到 180
        return Math.IEEEremainder(yaw, 360.0);
    }

    /**
     * 将俯仰角钳制到 [-90, 90]。
     */
    public static double clampPitch(double pitch) {
        return Math.clamp(pitch, -90, 90);
    }

    // ===== 工厂方法 =====

    /**
     * 从方向向量（单位向量）计算偏航/俯仰。
     * <p>
     * 向量在载具局部空间中表达，遵循 z- = 前方 的约定。
     *
     * @param dir 方向向量，需要时会被归一化
     * @return 对应的 {@link YawPitch}
     */
    @NotNull
    public static YawPitch from(@NotNull Vector3dc dir) {
        double lenSq = dir.lengthSquared();
        if (lenSq == 0) {
            return ZERO;
        }
        double invLen = 1.0 / Math.sqrt(lenSq);
        double nx = dir.x() * invLen;
        double ny = dir.y() * invLen;
        double nz = dir.z() * invLen;

        double yaw = Math.toDegrees(Math.atan2(-nx, -nz));
        double pitch = Math.toDegrees(Math.asin(Math.clamp(ny, -1, 1)));
        return new YawPitch(yaw, pitch);
    }

    /**
     * 计算两个偏航角之间的最短弧长。
     * <p>
     * 返回 (-180, 180] 范围内的有符号差值。
     */
    public static double shortestYawDelta(double from, double to) {
        return normalizeYaw(to - from);
    }

    /**
     * 转换为单位方向向量（载具局部空间）。
     *
     * @return 单位向量，z- = 前方，x+ = 右方，y+ = 上方
     */
    @NotNull
    public Vector3d toUnitVector() {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        return new Vector3d(-Math.cos(pitchRad) * Math.sin(yawRad), Math.sin(pitchRad),
                -Math.cos(pitchRad) * Math.cos(yawRad));
    }

    /**
     * 返回角度叠加后的新 {@link YawPitch}
     */
    @NotNull
    public YawPitch add(@NotNull YawPitch other) {
        return new YawPitch(yaw + other.yaw, pitch + other.pitch);
    }

    /**
     * 返回角度相减后的新 {@link YawPitch}
     */
    @NotNull
    public YawPitch subtract(@NotNull YawPitch other) {
        return new YawPitch(yaw - other.yaw, pitch - other.pitch);
    }

    /**
     * 返回当前角度的相反方向
     */
    @NotNull
    public YawPitch reverse() {
        return new YawPitch(normalizeYaw(yaw + 180), -pitch);
    }

    /**
     * 在偏航平面上线性插值到目标 {@link YawPitch}。
     *
     * @param target 目标角度
     * @param t      插值因子 [0, 1]，0 = 当前，1 = 目标
     */
    @NotNull
    public YawPitch lerp(@NotNull YawPitch target, double t) {
        return new YawPitch(yaw + shortestYawDelta(yaw, target.yaw) * t, pitch + (target.pitch - pitch) * t);
    }

    @Override
    public @NotNull String toString() {
        return String.format("YawPitch(yaw=%.1f°, pitch=%.1f°)", yaw, pitch);
    }
}
