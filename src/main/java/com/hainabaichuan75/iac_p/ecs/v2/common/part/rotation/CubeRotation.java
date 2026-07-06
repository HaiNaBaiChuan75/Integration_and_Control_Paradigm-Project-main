package com.hainabaichuan75.iac_p.ecs.v2.common.part.rotation;

import com.hainabaichuan75.iac_p.ecs.v2.api.part.ComponentKey;
import net.minecraft.core.Direction;
import net.minecraft.nbt.StringTag;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * 三维空间朝向 —— 24 种立方体旋转（旋转群 O）的枚举。
 * <p>
 * 一个朝向由「前方指向」和「上方指向」唯一确定，前方与上方必须相互垂直。
 * 枚举值在编译期即固定，所有 24 种均为合法。
 * <p>
 * <b>命名约定</b>：
 * <pre>
 *   前方_上方         前方（上方=UP 时可省略 _UP）
 *   NORTH_UP / NORTH  普通北方
 *   NORTH_DOWN        倒置北方
 *   NORTH_EAST        北方 + 右方为上
 *   UP_SOUTH          天花板朝南
 * </pre>
 * 即 {@code NORTH = NORTH_UP}、{@code SOUTH = SOUTH_UP} 等。其余 20 种均完整标注。
 * </p>
 * <p>
 * <b>24 种的分解</b>：
 * <pre>
 * 前方方向（6 选 1） &times; 上方方向（4 选 1）= 24
 *        &uarr;                    &uarr;
 *   NORTH/SOUTH/       与前方不同轴的四个方向之一
 *   EAST/WEST/UP/DOWN
 * </pre>
 * <p>
 * <b>四元数约定</b>：
 * <pre>
 * 默认朝向（NORTH_UP）= 单位四元数（无旋转）
 *   局部 Z- &rarr; 世界 NORTH（0, 0, -1）
 *   局部 Y+ &rarr; 世界 UP（0, 1, 0）
 *   局部 X+ &rarr; 世界 EAST（1, 0, 0）
 * </pre>
 * <p>
 * <b>四元数</b>在枚举构造时预计算，后续零分配读取。
 * <b>组件键</b>通过 {@link #KEY} 获取，可在 ECS 中直接作为组件读写。
 *
 * @see #KEY ECS 组件键
 */
public enum CubeRotation {

    // ====================================================================
    //  24 种常量（前方_上方命名）
    // ====================================================================

    /**
     * 单位朝向：前方 NORTH x 上方 UP
     */
    NORTH(Direction.NORTH, Direction.UP), NORTH_DOWN(Direction.NORTH, Direction.DOWN), NORTH_EAST(Direction.NORTH,
            Direction.EAST), NORTH_WEST(Direction.NORTH, Direction.WEST),

    SOUTH(Direction.SOUTH, Direction.UP), SOUTH_DOWN(Direction.SOUTH, Direction.DOWN), SOUTH_WEST(Direction.SOUTH,
            Direction.WEST), SOUTH_EAST(Direction.SOUTH, Direction.EAST),

    EAST(Direction.EAST, Direction.UP), EAST_DOWN(Direction.EAST, Direction.DOWN), EAST_SOUTH(Direction.EAST,
            Direction.SOUTH), EAST_NORTH(Direction.EAST, Direction.NORTH),

    WEST(Direction.WEST, Direction.UP), WEST_DOWN(Direction.WEST, Direction.DOWN), WEST_NORTH(Direction.WEST,
            Direction.NORTH), WEST_SOUTH(Direction.WEST, Direction.SOUTH),

    UP_SOUTH(Direction.UP, Direction.SOUTH), UP_NORTH(Direction.UP, Direction.NORTH), UP_EAST(Direction.UP,
            Direction.EAST), UP_WEST(Direction.UP, Direction.WEST),

    DOWN_NORTH(Direction.DOWN, Direction.NORTH), DOWN_SOUTH(Direction.DOWN, Direction.SOUTH),
    DOWN_EAST(Direction.DOWN, Direction.EAST), DOWN_WEST(Direction.DOWN, Direction.WEST);

    // ====================================================================
    //  O(1) 查找表
    // ====================================================================

    /**
     * 6 &times; 6 快速查找表，按 forward.get3DDataValue() &times; up.get3DDataValue() 索引
     */
    private static final CubeRotation[][] LOOKUP = new CubeRotation[6][6];

    static {
        for (CubeRotation r : values()) {
            LOOKUP[r.forward.get3DDataValue()][r.up.get3DDataValue()] = r;
        }
    }

    // ====================================================================
    //  构造与四元数
    // ====================================================================

    public final @NotNull Direction forward;
    public final @NotNull Direction up;
    public final @NotNull Quaterniondc quaternion;

    CubeRotation(@NotNull Direction forward, @NotNull Direction up) {
        this.forward = forward;
        this.up = up;
        this.quaternion = computeQuaternion(forward, up);
    }

    // ====================================================================
    //  工厂
    // ====================================================================

    /**
     * 从前方向 {@code forward} 和上方向 {@code up} 查找对应的枚举值。
     *
     * @param forward 前方指向
     * @param up      上方指向
     * @return 匹配的 CubeRotation
     * @throws IllegalArgumentException 若 {@code forward} 与 {@code up} 在同一轴上
     */
    @Contract(pure = true)
    public static @NotNull CubeRotation of(@NotNull Direction forward, @NotNull Direction up) {
        if (forward.getAxis() == up.getAxis()) {
            throw new IllegalArgumentException("forward [%s] and up [%s] must be on different axes".formatted(forward
                    , up));
        }
        CubeRotation r = LOOKUP[forward.ordinal()][up.ordinal()];
        assert r != null : "No CubeRotation for forward=" + forward + " up=" + up;
        return r;
    }

    @Contract(pure = true)
    public static @NotNull CubeRotation fromDirection(@NotNull Direction forward) {
        Direction defaultUp = switch (forward) {
            case UP -> Direction.SOUTH;
            case DOWN -> Direction.NORTH;
            default -> Direction.UP;
        };
        return of(forward, defaultUp);
    }
    // ====================================================================
    //  四元数计算
    // ====================================================================

    /**
     * 从 (forward, up) 方向对计算单位四元数。
     */
    private static @NotNull Quaterniondc computeQuaternion(@NotNull Direction forward, @NotNull Direction up) {
        // lookAlong 一步变换：将局部 -Z 指向 forward，局部 +Y 指向 up
        return new Quaterniond().lookAlong(dirToVec(forward.getOpposite()), dirToVec(up));
    }

    /**
     * Direction &rarr; JOML Vector3d。
     */
    private static @NotNull Vector3d dirToVec(@NotNull Direction dir) {
        return new Vector3d(dir.getNormal().getX(), dir.getNormal().getY(), dir.getNormal().getZ());
    }

    // ====================================================================
    //  ECS 组件键
    // ====================================================================

    /**
     * 三维朝向的 ECS 组件键。
     * <p>
     * 可在 ECS 系统中将完整 3D 朝向作为组件读写：
     * <pre>{@code
     * part.setComponent(CubeRotation.KEY, CubeRotation.NORTH_UP);
     * CubeRotation r = part.getComponent(CubeRotation.KEY);
     * Quaterniondc q = r.quaternion();
     * }</pre>
     */
    public static final ComponentKey<CubeRotation> KEY = ComponentKey.of(CubeRotation.class, "cube_rotation",
            r -> StringTag.valueOf(r.name()), tag -> CubeRotation.valueOf(tag.getAsString()));
}
