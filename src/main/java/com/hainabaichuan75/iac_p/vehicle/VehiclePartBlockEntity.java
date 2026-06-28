package com.hainabaichuan75.iac_p.vehicle;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * 载具方块实体的抽象基类。
 * <p>
 * 所有载具部件（座舱、轮子、炮塔）都继承此类。
 * Systems 不再由特定方块驱动，而是由
 * {@link com.hainabaichuan75.iac_p.event.VehicleTickHandler} 统一调度。
 * <p>
 * {@link #sable$tick(ServerSubLevel)} 提供空实现 —— 子类按需覆盖。
 */
public abstract class VehiclePartBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    public static final Vector3dc DEFAULT_CENTER = new Vector3d(0.5, 0.5, 0.5);

    public VehiclePartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public Vector3dc getLogicCenter() {
        return DEFAULT_CENTER;
    }

    public Vector3dc getAbsPosition(SubLevel subLevel) {
        return subLevel.logicalPose().transformPosition(new Vector3d(worldPosition.getX() + getLogicCenter().x(),
                worldPosition.getY() + getLogicCenter().y(), worldPosition.getZ() + getLogicCenter().z()));
    }

    /**
     * 游戏 tick（20Hz），在物理 step 之前调用。
     * 子类按需覆盖（如 {@code ShotGunBlockEntity} 的瞄准旋转）。
     * <p>
     * 注意：不要在子类中驱动 {@link Systems} —— 已由
     * {@link com.hainabaichuan75.iac_p.event.VehicleTickHandler} 统一处理。
     */
    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        // 默认空实现 — 子类按需覆盖
    }
}
