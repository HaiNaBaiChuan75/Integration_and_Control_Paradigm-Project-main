package com.hainabaichuan75.iac_p.ecs.v2.part.state;

import com.hainabaichuan75.iac_p.ecs.v2.part.ComponentKey;
import com.hainabaichuan75.iac_p.ecs.v2.part.Part;
import com.hainabaichuan75.iac_p.ecs.v2.part.View;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

/**
 * 驱动状态 —— 引擎分配给本轮的扭矩。
 * <p>
 * 由 {@code TorqueDistributionSystem} 在逻辑 tick 中写入（含差速器偏置分配），
 * 物理 System 在物理 tick 中读取用于推进力计算。
 * <p>
 * 此组件仅出现在同时持有 {@link WheelState} 的 Part 上。
 * <p>
 * 不可变记录，每次修改通过 wither 方法创建新实例。
 *
 * @param torqueInput 引擎分配给本轮的扭矩（Nm），正 = 前进，负 = 后退
 */
public record DriveState(double torqueInput) {

    /**
     * 零扭矩状态（无动力输入）
     */
    public static final DriveState IDLE = new DriveState(0);

    // ====================================================================
    //  Wither 方法
    // ====================================================================

    /**
     * 仅更新扭矩输入，保持其他字段不变
     */
    @NotNull
    public DriveState withTorqueInput(double torqueInput) {
        return new DriveState(torqueInput);
    }

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    private static final String TAG_TORQUE = "torqueInput";

    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putDouble(TAG_TORQUE, torqueInput);
        return tag;
    }

    @NotNull
    public static DriveState fromTag(@NotNull CompoundTag tag) {
        return new DriveState(tag.getDouble(TAG_TORQUE));
    }

    // ====================================================================
    //  组件键
    // ====================================================================

    /**
     * 驱动组件的类型键，包含 NBT 元数据
     */
    public static final ComponentKey<DriveState> KEY = ComponentKey.of(DriveState.class, "drive", DriveState::toTag,
            tag -> DriveState.fromTag((CompoundTag) tag));

    // ====================================================================
    //  访问器
    // ====================================================================

    /**
     * 创建类型化组件访问器。
     * <pre>{@code
     * var dv = DriveState.view(part);
     * dv.set(dv.get().withTorqueInput(50));
     * }</pre>
     */
    @NotNull
    public static View<DriveState> view(@NotNull Part part) {
        return new View<>(part, KEY);
    }
}
