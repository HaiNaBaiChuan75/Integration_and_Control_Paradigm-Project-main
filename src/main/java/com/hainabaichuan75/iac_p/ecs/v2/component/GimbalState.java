package com.hainabaichuan75.iac_p.ecs.v2.component;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.ComponentKey;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import com.hainabaichuan75.iac_p.part.field.YawPitch;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 云台运行时状态 —— ECS <b>组件（Component）</b>，当前偏航/俯仰角度及角速度。
 * <p>
 * 由 {@link com.hainabaichuan75.iac_p.ecs.v2.system.AimSystem AimSystem}
 * 在逻辑 tick 中写入目标角速度，
 * 再由 {@link com.hainabaichuan75.iac_p.ecs.v2.system.GimbalSystem GimbalSystem}
 * 积分到角度并处理机械限位，
 * 渲染 System 读取角度用于模型旋转、读取角速度用于帧间插值。
 * <p>
 * 不可变记录，每次修改通过 wither 方法创建新实例。
 * 与配置参数 {@link GimbalDef} 分离，Def/State 各持独立 {@link ComponentKey}。
 *
 * @param angles   当前云台角度（偏航/俯仰，单位：度），Minecraft / 载具局部坐标系约定
 * @param velYaw   偏航角速度（度/tick），正值 = CCW+
 * @param velPitch 俯仰角速度（度/tick），正值 = 向上
 */
public record GimbalState(@NotNull YawPitch angles, double velYaw, double velPitch) {

    /**
     * 零姿态：水平朝前，静止
     */
    public static final GimbalState ZERO = new GimbalState(YawPitch.ZERO, 0, 0);

    /**
     * 紧凑构造：angles 为 null 时回退到 ZERO；无效角速度归零。
     */
    public GimbalState {
        if (!Double.isFinite(velYaw)) velYaw = 0;
        if (!Double.isFinite(velPitch)) velPitch = 0;
    }

    // ====================================================================
    //  Wither 方法
    // ====================================================================

    /**
     * 完整替换角度，角速度归零（位置指令）。
     */
    @NotNull
    public GimbalState withAngles(@NotNull YawPitch angles) {
        return new GimbalState(angles, 0, 0);
    }

    /**
     * 仅更新偏航，角速度归零。
     */
    @NotNull
    public GimbalState withYaw(double yaw) {
        return new GimbalState(new YawPitch(yaw, angles.pitch()), 0, 0);
    }

    /**
     * 仅更新俯仰，角速度归零。
     */
    @NotNull
    public GimbalState withPitch(double pitch) {
        return new GimbalState(new YawPitch(angles.yaw(), pitch), 0, 0);
    }

    /**
     * 设置角速度而不改变角度。
     */
    @NotNull
    public GimbalState withVelocity(double velYaw, double velPitch) {
        return new GimbalState(angles, velYaw, velPitch);
    }

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    private static final String TAG_ANGLES = "angles";
    private static final String TAG_VEL_YAW = "velYaw";
    private static final String TAG_VEL_PITCH = "velPitch";

    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.put(TAG_ANGLES, angles.toTag());
        tag.putDouble(TAG_VEL_YAW, velYaw);
        tag.putDouble(TAG_VEL_PITCH, velPitch);
        return tag;
    }

    @NotNull
    public static GimbalState fromTag(@NotNull CompoundTag tag) {
        return new GimbalState(YawPitch.fromTag(tag.getCompound(TAG_ANGLES)), tag.getDouble(TAG_VEL_YAW),
                tag.getDouble(TAG_VEL_PITCH));
    }

    // ====================================================================
    //  组件键
    // ====================================================================

    /**
     * 云台状态组件的类型键，包含 NBT 元数据
     */
    public static final ComponentKey<GimbalState> KEY = ComponentKey.of(GimbalState.class, "gimbal",
            GimbalState::toTag, tag -> GimbalState.fromTag((CompoundTag) tag));

    // ====================================================================
    //  访问器
    // ====================================================================

    /**
     * 创建类型化组件访问器（可空）。
     * <p>
     * 部件无此组件时返回 {@code null}，调用方必须处理缺失情况。
     * <pre>{@code
     * var gv = GimbalState.view(part);
     * if (gv != null) {
     *     gv.set(gv.get().withYaw(45));
     * }
     * }</pre>
     */
    @Nullable
    public static View<GimbalState> view(@NotNull Part part) {
        return View.of(part, KEY);
    }
}
