package com.hainabaichuan75.iac_p.ecs.part;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 载具部件的抽象便利基类。
 * <p>
 * 所有载具功能方块（悬挂、轮子、武器、驾驶舱）<b>应当</b>继承此类。
 * <b>但不强制</b>：如果因单继承限制无法继承此类，直接实现 {@link Part} 接口即可。
 * <p>
 * 此类自身提供：
 * <ul>
 *   <li>{@link #orientation()} 默认实现（单位四元数）</li>
 *   <li>{@link #getSubLevel()} — 委托 {@code Sable.HELPER.getContaining(this)}</li>
 * </ul>
 * 其余接口方法（{@link Part#getBlockPos()}、{@link Part#partLogicalPose()}、
 * {@link Part#getCenterInWorld()}）由本类从 {@link BlockEntity} 继承或使用接口默认实现。
 * <p>
 * <b>坐标系约定</b>
 * <p>
 * Sable 定义了双层坐标空间：
 * <ul>
 *   <li><b>SubLevel 内部坐标 (local)</b> — 方块在 SubLevel 内的 BlockPos，原点为其旋转中心</li>
 *   <li><b>父 Level 坐标 (logical)</b> — 经 {@link dev.ryanhcode.sable.sublevel.SubLevel#logicalPose()} 缩放+旋转+平移后的世界坐标，
 *       Sable 称为 "logical" 空间，是物理引擎的参考系</li>
 * </ul>
 * Part 与 SubLevel 一样位于 local 空间，需要同样的 logical 变换才能进入物理/渲染的坐标域。
 * z- 方向为前方，x+ 为右方。
 *
 * @see Part
 * @see dev.ryanhcode.sable.sublevel.SubLevel#logicalPose()
 */
@Deprecated(since = "1.0", forRemoval = true)
public abstract class PartBlockEntity extends BlockEntity implements Part {

    public PartBlockEntity(@NotNull BlockEntityType<?> type, @NotNull BlockPos pos, @NotNull BlockState blockState) {
        super(type, pos, blockState);
    }

}
