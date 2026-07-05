package com.hainabaichuan75.iac_p.ecs.v2.part.rotation;

import com.hainabaichuan75.iac_p.ecs.v2.part.ComponentKey;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

/**
 * 任意旋转状态 —— 不限于轴对齐的任意四元数。
 * <p>
 * 用于 45&deg; 椅子等非立方体对齐的部件。与 {@link com.hainabaichuan75.iac_p.ecs.v2.part.rotation.CubeRotation}
 * （24 种离散朝向）互补。
 * <p>
 * <b>不变式</b>：存储的单位四元数始终归一化。
 *
 * @param quaternion 单位四元数
 */
public record Rotation(@NotNull Quaterniondc quaternion) {

    /**
     * 无旋转状态（单位四元数）。
     */
    public static final Rotation IDENTITY = new Rotation(new Quaterniond());

    public Rotation {
        quaternion = new Quaterniond(quaternion).normalize();
    }

    // ====================================================================
    //  NBT 序列化
    // ====================================================================

    private static final String TAG_QX = "qx";
    private static final String TAG_QY = "qy";
    private static final String TAG_QZ = "qz";
    private static final String TAG_QW = "qw";

    @NotNull
    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putDouble(TAG_QX, quaternion.x());
        tag.putDouble(TAG_QY, quaternion.y());
        tag.putDouble(TAG_QZ, quaternion.z());
        tag.putDouble(TAG_QW, quaternion.w());
        return tag;
    }

    @Contract("_ -> new")
    @NotNull
    public static Rotation fromTag(@NotNull CompoundTag tag) {
        return new Rotation(new Quaterniond(tag.getDouble(TAG_QX), tag.getDouble(TAG_QY), tag.getDouble(TAG_QZ),
                tag.getDouble(TAG_QW)));
    }

    // ====================================================================
    //  ECS 组件键
    // ====================================================================

    public static final ComponentKey<Rotation> KEY = ComponentKey.of(Rotation.class, "rotation", Rotation::toTag,
            tag -> Rotation.fromTag((CompoundTag) tag));
}
