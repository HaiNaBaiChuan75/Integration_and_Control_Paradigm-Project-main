package com.hainabaichuan75.iac_p.ecs.v2.component;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.ComponentKey;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import com.hainabaichuan75.iac_p.part.field.YawPitch;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

/**
 * 瞄准状态 —— ECS <b>组件（Component）</b>，武器挂载的俯仰/偏航角度。
 * <p>
 * 由 {@code WeaponAimSystem} 在逻辑 tick 中写入，
 * 渲染 System 读取用于模型旋转。
 * <p>
 * 不可变记录，每次修改通过 wither 方法创建新实例。
 *
 * @param angles 当前瞄准角度（偏航/俯仰，单位：度），使用 Minecraft / 载具局部坐标系约定
 */
public record AimingState(@NotNull YawPitch angles) {

    /**
     * 零姿态：水平朝前（无瞄准偏移）
     */
    public static final AimingState ZERO = new AimingState(YawPitch.ZERO);

    /**
     * 紧凑构造：angles 为 null 时回退到 ZERO。
     */
    public AimingState {
        if (angles == null) {
            angles = YawPitch.ZERO;
        }
    }

    // ====================================================================
    //  Wither 方法
    // ====================================================================

    /**
     * 完整替换角度
     */
    @NotNull
    public AimingState withAngles(@NotNull YawPitch angles) {
        return new AimingState(angles);
    }

    /**
     * 仅更新偏航，保持俯仰不变
     */
    @NotNull
    public AimingState withYaw(double yaw) {
        return new AimingState(new YawPitch(yaw, angles.pitch()));
    }

    /**
     * 仅更新俯仰，保持偏航不变
     */
    @NotNull
    public AimingState withPitch(double pitch) {
        return new AimingState(new YawPitch(angles.yaw(), pitch));
    }

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    private static final String TAG_ANGLES = "angles";

    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.put(TAG_ANGLES, angles.toTag());
        return tag;
    }

    @NotNull
    public static AimingState fromTag(@NotNull CompoundTag tag) {
        return new AimingState(YawPitch.fromTag(tag.getCompound(TAG_ANGLES)));
    }

    // ====================================================================
    //  组件键
    // ====================================================================

    /**
     * 瞄准组件的类型键，包含 NBT 元数据
     */
    public static final ComponentKey<AimingState> KEY = ComponentKey.of(AimingState.class, "aiming",
            AimingState::toTag, tag -> AimingState.fromTag((CompoundTag) tag));

    // ====================================================================
    //  访问器
    // ====================================================================

    /**
     * 创建类型化组件访问器。
     * <pre>{@code
     * var av = AimingState.view(part);
     * av.set(av.get().withYaw(45));
     * }</pre>
     */
    @NotNull
    public static View<AimingState> view(@NotNull Part part) {
        return new View<>(part, KEY);
    }
}
