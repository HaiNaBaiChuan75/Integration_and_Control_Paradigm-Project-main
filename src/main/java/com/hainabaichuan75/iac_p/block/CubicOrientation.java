package com.hainabaichuan75.iac_p.block;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * 立方取向 —— 24 种立方体朝向（旋转群 O）的枚举，可作为方块的 BlockState Property 使用。
 * <p>
 * 参照 {@link net.minecraft.core.FrontAndTop} 的 API 风格，但扩展了所有 12 种额外朝向
 * （水平前方 × 水平上方、水平前方 × DOWN），完整覆盖立方体的全部旋转对称性。
 * <p>
 * <b>命名约定</b>：{@code FRONT_TOP}，前方为水平方向且上方为 UP 时可省略 {@code _UP} 后缀。
 * <pre>
 *   NORTH      → front=NORTH, top=UP
 *   NORTH_DOWN → front=NORTH, top=DOWN
 *   UP_SOUTH   → front=UP,    top=SOUTH
 * </pre>
 * <p>
 * <b>计数</b>：front（6 选 1）× top（4 选 1）= 24 种。
 */
public enum CubicOrientation implements StringRepresentable {
    DOWN_NORTH("down_north", Direction.DOWN, Direction.NORTH), DOWN_SOUTH("down_south", Direction.DOWN,
            Direction.SOUTH), DOWN_EAST("down_east", Direction.DOWN, Direction.EAST), DOWN_WEST("down_west",
            Direction.DOWN, Direction.WEST),

    UP_NORTH("up_north", Direction.UP, Direction.NORTH), UP_SOUTH("up_south", Direction.UP, Direction.SOUTH),
    UP_EAST("up_east", Direction.UP, Direction.EAST), UP_WEST("up_west", Direction.UP, Direction.WEST),

    NORTH("north", Direction.NORTH, Direction.UP), EAST("east", Direction.EAST, Direction.UP), SOUTH("south",
            Direction.SOUTH, Direction.UP), WEST("west", Direction.WEST, Direction.UP),

    NORTH_DOWN("north_down", Direction.NORTH, Direction.DOWN), EAST_DOWN("east_down", Direction.EAST, Direction.DOWN)
    , SOUTH_DOWN("south_down", Direction.SOUTH, Direction.DOWN), WEST_DOWN("west_down", Direction.WEST, Direction.DOWN),

    NORTH_EAST("north_east", Direction.NORTH, Direction.EAST), NORTH_WEST("north_west", Direction.NORTH,
            Direction.WEST), SOUTH_EAST("south_east", Direction.SOUTH, Direction.EAST), SOUTH_WEST("south_west",
            Direction.SOUTH, Direction.WEST), EAST_SOUTH("east_south", Direction.EAST, Direction.SOUTH), EAST_NORTH(
                    "east_north", Direction.EAST, Direction.NORTH), WEST_SOUTH("west_south", Direction.WEST,
            Direction.SOUTH), WEST_NORTH("west_north", Direction.WEST, Direction.NORTH);

    // ============================================================
    //  BlockState Property
    // ============================================================

    public static final EnumProperty<CubicOrientation> ORIENTATION = EnumProperty.create("orientation",
            CubicOrientation.class);

    // ============================================================
    //  O(1) 查找表
    // ============================================================

    private static final Int2ObjectMap<CubicOrientation> LOOKUP_FRONT_TOP =
            Util.make(new Int2ObjectOpenHashMap<>(values().length), map -> {
        for (CubicOrientation co : values()) {
            map.put(lookupKey(co.front, co.top), co);
        }
    });

    private static int lookupKey(@NotNull Direction front, @NotNull Direction top) {
        return front.ordinal() << 3 | top.ordinal();
    }

    // ============================================================
    //  字段
    // ============================================================

    private final String name;
    private final Direction front;
    private final Direction top;
    private final Quaterniondc quaternion;

    CubicOrientation(@NotNull String name, @NotNull Direction front, @NotNull Direction top) {
        this.name = name;
        this.front = front;
        this.top = top;
        this.quaternion = computeQuaternion(front, top);
    }

    // ============================================================
    //  访问器
    // ============================================================

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }

    public Direction front() {
        return front;
    }

    public Direction top() {
        return top;
    }

    /**
     * 该朝向对应的单位四元数（局部 * 世界 = 默认朝向 NORTH_UP）。
     * 构造时预计算，后续零分配读取。
     */
    public Quaterniondc quaternion() {
        return quaternion;
    }

    // ============================================================
    //  工厂
    // ============================================================

    /**
     * 从前方 {@code front} 和上方 {@code top} 查找对应的立方取向。
     *
     * @return 匹配的朝向，若 {@code front} 与 {@code top} 同轴则返回 {@code null}
     */
    @Contract(pure = true)
    @Nullable
    public static CubicOrientation fromFrontAndTop(@NotNull Direction front, @NotNull Direction top) {
        return LOOKUP_FRONT_TOP.get(lookupKey(front, top));
    }

    /**
     * 将原版 6 个方向映射为对应的预设立方取向。
     * <ul>
     *   <li>水平方向（N/S/E/W）→ 前方同方向，上方 {@link Direction#UP}</li>
     *   <li>{@link Direction#UP}        → 前方 UP，上方 {@link Direction#SOUTH}</li>
     *   <li>{@link Direction#DOWN}      → 前方 DOWN，上方 {@link Direction#NORTH}</li>
     * </ul>
     */
    @Contract(pure = true)
    @NotNull
    public static CubicOrientation fromFront(@NotNull Direction front) {
        return switch (front) {
            case UP -> UP_SOUTH;
            case DOWN -> DOWN_NORTH;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
        };
    }

    // ============================================================
    //  四元数计算
    // ============================================================

    private static @NotNull Quaterniondc computeQuaternion(@NotNull Direction front, @NotNull Direction top) {
        return new Quaterniond().lookAlong(dirToVec(front.getOpposite()), dirToVec(top));
    }

    private static @NotNull Vector3d dirToVec(@NotNull Direction dir) {
        return new Vector3d(dir
                .getNormal()
                .getX(), dir
                .getNormal()
                .getY(), dir
                .getNormal()
                .getZ());
    }
}
