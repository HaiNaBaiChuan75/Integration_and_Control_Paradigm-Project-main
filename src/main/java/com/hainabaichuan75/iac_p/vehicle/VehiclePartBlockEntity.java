package com.hainabaichuan75.iac_p.vehicle;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;

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
}
