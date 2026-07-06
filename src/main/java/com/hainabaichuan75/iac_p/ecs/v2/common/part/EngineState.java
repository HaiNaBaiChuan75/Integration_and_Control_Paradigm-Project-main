package com.hainabaichuan75.iac_p.ecs.v2.common.part;

import com.hainabaichuan75.iac_p.ecs.v2.api.part.ComponentKey;
import com.hainabaichuan75.iac_p.ecs.v2.api.part.Part;
import com.hainabaichuan75.iac_p.ecs.v2.api.part.View;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

/**
 * 引擎运行时状态 —— 当前输出扭矩。
 * <p>
 * 不可变记录，每次修改通过 wither 方法创建新实例。
 * 在 20Hz tick 频率下，每次 {@link #withTorque(double)} 产生一次分配，
 * 现代 JVM GC 可完全消化。
 * <p>
 * 与配置参数 {@link EngineDef} 分离 — Def/State 是同级组件，各持独立 {@link ComponentKey}，
 * 通过 {@code part.getComponent(EngineDef.KEY)} / {@code part.getComponent(EngineState.KEY)} 分别存取。
 * <p>
 * 扭矩约束（clamp 到 maxTorque）由写入方（System 层）保证，本记录不持有 Def 引用。
 *
 * @param torque 当前输出扭矩（Nm）
 */
public record EngineState(double torque) {

    /**
     * 零扭矩状态
     */
    public static final EngineState IDLE = new EngineState(0);

    // ====================================================================
    //  Wither 方法
    // ====================================================================

    @NotNull
    public EngineState withTorque(double torque) {
        return new EngineState(torque);
    }

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    private static final String TAG_TORQUE = "torque";

    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putDouble(TAG_TORQUE, torque);
        return tag;
    }

    @NotNull
    public static EngineState fromTag(@NotNull CompoundTag tag) {
        return new EngineState(tag.getDouble(TAG_TORQUE));
    }

    // ====================================================================
    //  组件键
    // ====================================================================

    /**
     * 引擎运行时状态的组件键，包含 NBT 元数据
     */
    public static final ComponentKey<EngineState> KEY = ComponentKey.of(EngineState.class, "engine",
            EngineState::toTag, tag -> EngineState.fromTag((CompoundTag) tag));

    // ====================================================================
    //  访问器
    // ====================================================================

    /**
     * 创建类型化组件访问器。
     * <pre>{@code
     * var ev = EngineState.view(part);
     * ev.set(ev.get().withTorque(50));
     * }</pre>
     */
    @NotNull
    public static View<EngineState> view(@NotNull Part part) {
        return new View<>(part, KEY);
    }
}
