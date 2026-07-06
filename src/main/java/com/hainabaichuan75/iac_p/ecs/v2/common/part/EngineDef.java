package com.hainabaichuan75.iac_p.ecs.v2.common.part;

import com.hainabaichuan75.iac_p.ecs.v2.api.part.ComponentKey;
import com.hainabaichuan75.iac_p.ecs.v2.api.part.Part;
import com.hainabaichuan75.iac_p.ecs.v2.api.part.View;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

/**
 * 引擎定义 —— 不可变的引擎基础参数。
 * <p>
 * 与运行时状态 {@link EngineState} 分离，两者通过各自的 {@link ComponentKey} 独立存取。
 * 定义参数在载具生命周期内不会变化，或仅在配置/升级时通过 swap 整个 record 变更。
 * <p>
 * 遵循值类型约定：不可变、可 NBT 序列化、按值相等。
 *
 * @param maxTorque 最大扭矩（Nm）
 * @param maxRpm    最大转速（RPM），即红线转速
 */
public record EngineDef(double maxTorque, double maxRpm) {

    /**
     * 默认引擎定义（驾驶舱默认值）
     */
    public static final EngineDef DEFAULT = new EngineDef(50.0, 6000.0);

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    private static final String TAG_MAX_TORQUE = "maxTorque";
    private static final String TAG_MAX_RPM = "maxRpm";

    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putDouble(TAG_MAX_TORQUE, maxTorque);
        tag.putDouble(TAG_MAX_RPM, maxRpm);
        return tag;
    }

    @NotNull
    public static EngineDef fromTag(@NotNull CompoundTag tag) {
        return new EngineDef(tag.getDouble(TAG_MAX_TORQUE), tag.getDouble(TAG_MAX_RPM));
    }

    // ====================================================================
    //  组件键
    // ====================================================================

    /**
     * 引擎定义的组件键，包含 NBT 元数据
     */
    public static final ComponentKey<EngineDef> KEY = ComponentKey.of(EngineDef.class, "engine_def", EngineDef::toTag
            , tag -> EngineDef.fromTag((CompoundTag) tag));

    // ====================================================================
    //  访问器
    // ====================================================================

    /**
     * 创建类型化组件访问器。
     * <pre>{@code
     * var dv = EngineDef.view(part);
     * var def = dv.get();
     * }</pre>
     */
    @NotNull
    public static View<EngineDef> view(@NotNull Part part) {
        return new View<>(part, KEY);
    }
}
