package com.hainabaichuan75.iac_p.ecs.v2.component;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.ComponentKey;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import com.hainabaichuan75.iac_p.part.field.YawPitch;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 云台定义 —— ECS <b>组件（Component）</b>，不可变的云台机械参数。
 * <p>
 * 与运行时状态 {@link GimbalState} 分离，两者通过各自的 {@link ComponentKey} 独立存取。
 * 定义参数在载具生命周期内不会变化，或仅在升级时通过 swap 整个 record 变更。
 * <p>
 * 角速度单位为度/tick（MC 20 tick/s），角度范围单位为度。
 * <p>
 * 遵循值类型约定：不可变、可 NBT 序列化、按值相等。
 *
 * @param maxSpeedYaw   最大偏航角速度（度/tick），正值
 * @param maxSpeedPitch 最大俯仰角速度（度/tick），正值
 * @param minYaw        偏航最小角度（度），机械限位
 * @param maxYaw        偏航最大角度（度），机械限位
 * @param minPitch      俯仰最小角度（度），机械限位
 * @param maxPitch      俯仰最大角度（度），机械限位
 */
public record GimbalDef(double maxSpeedYaw, double maxSpeedPitch, double minYaw, double maxYaw, double minPitch,
                        double maxPitch) {

    /**
     * 默认云台定义（通用武器挂架）
     */
    public static final GimbalDef DEFAULT = new GimbalDef(3, 3, -150, 150, -45, 30);

    // ====================================================================
    //  紧凑构造：无效参数回退
    // ====================================================================

    /**
     * 速度/角度范围异常时回退为无限制（数学最大值）。
     */
    public GimbalDef {
        if (maxSpeedYaw <= 0) maxSpeedYaw = Double.MAX_VALUE;
        if (maxSpeedPitch <= 0) maxSpeedPitch = Double.MAX_VALUE;
        if (minYaw >= maxYaw) {
            minYaw = -180;
            maxYaw = 180;
        }
        if (minPitch >= maxPitch) {
            minPitch = -90;
            maxPitch = 90;
        }
    }

    // ====================================================================
    //  钳制方法
    // ====================================================================

    /**
     * 将偏航角度钳制在 {@link #minYaw} ~ {@link #maxYaw} 范围内。
     */
    public double clampYaw(double yaw) {
        return Math.clamp(yaw, minYaw, maxYaw);
    }

    /**
     * 将俯仰角度钳制在 {@link #minPitch} ~ {@link #maxPitch} 范围内。
     */
    public double clampPitch(double pitch) {
        return Math.clamp(pitch, minPitch, maxPitch);
    }

    /**
     * 将 {@link YawPitch} 的两个轴同时钳制到各自限位内。
     */
    @NotNull
    public YawPitch clampAngles(@NotNull YawPitch angles) {
        return new YawPitch(clampYaw(angles.yaw()), clampPitch(angles.pitch()));
    }

    /**
     * 将偏航角速度钳制在 {@code [-maxSpeedYaw, maxSpeedYaw]} 范围内。
     */
    public double clampSpeedYaw(double speed) {
        return Math.clamp(speed, -maxSpeedYaw, maxSpeedYaw);
    }

    /**
     * 将俯仰角速度钳制在 {@code [-maxSpeedPitch, maxSpeedPitch]} 范围内。
     */
    public double clampSpeedPitch(double speed) {
        return Math.clamp(speed, -maxSpeedPitch, maxSpeedPitch);
    }

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    private static final String TAG_MAX_SPEED_YAW = "maxSpeedYaw";
    private static final String TAG_MAX_SPEED_PITCH = "maxSpeedPitch";
    private static final String TAG_MIN_YAW = "minYaw";
    private static final String TAG_MAX_YAW = "maxYaw";
    private static final String TAG_MIN_PITCH = "minPitch";
    private static final String TAG_MAX_PITCH = "maxPitch";

    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putDouble(TAG_MAX_SPEED_YAW, maxSpeedYaw);
        tag.putDouble(TAG_MAX_SPEED_PITCH, maxSpeedPitch);
        tag.putDouble(TAG_MIN_YAW, minYaw);
        tag.putDouble(TAG_MAX_YAW, maxYaw);
        tag.putDouble(TAG_MIN_PITCH, minPitch);
        tag.putDouble(TAG_MAX_PITCH, maxPitch);
        return tag;
    }

    @NotNull
    public static GimbalDef fromTag(@NotNull CompoundTag tag) {
        return new GimbalDef(tag.getDouble(TAG_MAX_SPEED_YAW), tag.getDouble(TAG_MAX_SPEED_PITCH),
                tag.getDouble(TAG_MIN_YAW), tag.getDouble(TAG_MAX_YAW), tag.getDouble(TAG_MIN_PITCH),
                tag.getDouble(TAG_MAX_PITCH));
    }

    // ====================================================================
    //  组件键
    // ====================================================================

    /**
     * 云台定义的组件键，包含 NBT 元数据
     */
    public static final ComponentKey<GimbalDef> KEY = ComponentKey.of(GimbalDef.class, "gimbal_def", GimbalDef::toTag
            , tag -> GimbalDef.fromTag((CompoundTag) tag));

    // ====================================================================
    //  访问器
    // ====================================================================

    /**
     * 创建类型化组件访问器（可空）。
     * <p>
     * 部件无此组件时返回 {@code null}，调用方必须处理缺失情况。
     * <pre>{@code
     * var dv = GimbalDef.view(part);
     * if (dv != null) {
     *     var def = dv.get();
     * }
     * }</pre>
     */
    @Nullable
    public static View<GimbalDef> view(@NotNull Part part) {
        return View.of(part, KEY);
    }
}
