package com.hainabaichuan75.iac_p.ecs.v2.component;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.ComponentKey;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

/**
 * 转向状态 —— ECS <b>组件（Component）</b>，本轮的归一化转向输入。
 * <p>
 * 由 {@code SteeringSystem} 在逻辑 tick 中写入，
 * 物理 System 读取后将其映射为实际偏转角，然后旋转轮子轴向获得偏转后的牵引方向。
 * <p>
 * 此组件仅出现在同时持有 {@link WheelState} 的 Part 上。
 * <p>
 * 不可变记录，每次修改通过 wither 方法创建新实例。
 *
 * @param steeringInput 当前转向输入，{@code [-1, 1]} 范围归一化标量。
 *                      {@code 0 = 回正}，正值 = 右转，负值 = 左转。
 */
public record SteeringState(double steeringInput) {

    /**
     * 回正状态（无转向输入）
     */
    public static final SteeringState STRAIGHT = new SteeringState(0);

    // ====================================================================
    //  Wither 方法
    // ====================================================================

    /**
     * 仅更新转向输入，保持其他字段不变
     */
    @NotNull
    public SteeringState withSteeringInput(double steeringInput) {
        return new SteeringState(steeringInput);
    }

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    private static final String TAG_STEERING = "steeringInput";

    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putDouble(TAG_STEERING, steeringInput);
        return tag;
    }

    @NotNull
    public static SteeringState fromTag(@NotNull CompoundTag tag) {
        return new SteeringState(tag.getDouble(TAG_STEERING));
    }

    // ====================================================================
    //  组件键
    // ====================================================================

    /**
     * 转向组件的类型键，包含 NBT 元数据
     */
    public static final ComponentKey<SteeringState> KEY = ComponentKey.of(SteeringState.class, "steering",
            SteeringState::toTag, tag -> SteeringState.fromTag((CompoundTag) tag));

    // ====================================================================
    //  访问器
    // ====================================================================

    /**
     * 创建类型化组件访问器。
     * <pre>{@code
     * var sv = SteeringState.view(part);
     * sv.set(sv.get().withSteeringInput(0.5));
     * }</pre>
     */
    @NotNull
    public static View<SteeringState> view(@NotNull Part part) {
        return new View<>(part, KEY);
    }
}
