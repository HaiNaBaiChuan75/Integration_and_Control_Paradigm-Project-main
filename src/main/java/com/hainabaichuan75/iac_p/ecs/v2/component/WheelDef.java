package com.hainabaichuan75.iac_p.ecs.v2.component;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.ComponentKey;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * 轮子定义 —— ECS <b>组件（Component）</b>，不可变的轮子几何与悬挂参数。
 * <p>
 * 与运行时状态 {@link WheelState} 分离，两者通过各自的 {@link ComponentKey} 独立存取。
 * 定义参数在载具生命周期内通常不会变化，或仅在更换轮胎/升级悬挂时通过 swap 整个 record 变更。
 * <p>
 * 遵循值类型约定：不可变、可 NBT 序列化、按值相等。
 *
 * @param radius              轮胎半径（米），用于力臂和轮速换算
 * @param suspensionStiffness 悬挂刚度系数（N/m），物理 System 读用于弹力计算
 * @param axialNormal         车轮旋转轴的方向向量（SubLevel 局部空间，单位向量），
 *                            默认 {@code (0, 0, -1)}（指向 Z-，即载具前方）
 * @param maxCompression      悬挂最大压缩量（米），用于钳制压缩量范围
 */
public record WheelDef(double radius, double suspensionStiffness, @NotNull Vector3dc axialNormal,
                       double maxCompression) {

    /**
     * 默认轴向：指向 Z-（载具前方）
     */
    private static final Vector3dc DEFAULT_AXIAL = new Vector3d(0, 0, -1);

    /**
     * 默认轮子定义（轮胎半径 0.5m，刚度 20000 N/m）
     */
    public static final WheelDef DEFAULT = new WheelDef(0.5, 20000.0, DEFAULT_AXIAL, 0.3);

    /**
     * 紧凑构造：轴向法线为 null 时回退到默认；入口防御性拷贝。
     * 参见 CLAUDE.md 「JOML 对象约定」。
     */
    public WheelDef {
        if (axialNormal == null) {
            axialNormal = DEFAULT_AXIAL;
        }
        axialNormal = new Vector3d(axialNormal);
    }

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    private static final String TAG_RADIUS = "radius";
    private static final String TAG_STIFFNESS = "suspensionStiffness";
    private static final String TAG_AXIAL_X = "axialX";
    private static final String TAG_AXIAL_Y = "axialY";
    private static final String TAG_AXIAL_Z = "axialZ";
    private static final String TAG_MAX_COMPRESSION = "maxCompression";

    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putDouble(TAG_RADIUS, radius);
        tag.putDouble(TAG_STIFFNESS, suspensionStiffness);
        tag.putDouble(TAG_AXIAL_X, axialNormal.x());
        tag.putDouble(TAG_AXIAL_Y, axialNormal.y());
        tag.putDouble(TAG_AXIAL_Z, axialNormal.z());
        tag.putDouble(TAG_MAX_COMPRESSION, maxCompression);
        return tag;
    }

    @NotNull
    public static WheelDef fromTag(@NotNull CompoundTag tag) {
        return new WheelDef(tag.getDouble(TAG_RADIUS), tag.getDouble(TAG_STIFFNESS),
                new Vector3d(tag.getDouble(TAG_AXIAL_X), tag.getDouble(TAG_AXIAL_Y), tag.getDouble(TAG_AXIAL_Z)),
                tag.getDouble(TAG_MAX_COMPRESSION));
    }

    // ====================================================================
    //  组件键
    // ====================================================================

    /**
     * 轮子定义的组件键，包含 NBT 元数据
     */
    public static final ComponentKey<WheelDef> KEY = ComponentKey.of(WheelDef.class, "wheel_def", WheelDef::toTag,
            tag -> WheelDef.fromTag((CompoundTag) tag));

    // ====================================================================
    //  访问器
    // ====================================================================

    /**
     * 创建类型化组件访问器。
     * <pre>{@code
     * var wd = WheelDef.view(part);
     * var def = wd.get();
     * }</pre>
     */
    @NotNull
    public static View<WheelDef> view(@NotNull Part part) {
        return new View<>(part, KEY);
    }
}
