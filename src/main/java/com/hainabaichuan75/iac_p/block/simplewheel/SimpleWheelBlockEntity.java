package com.hainabaichuan75.iac_p.block.simplewheel;

import com.hainabaichuan75.iac_p.vehicle.WheelCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SimpleWheelBlockEntity extends BlockEntity implements WheelCapability {

    public SimpleWheelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public double getVerticalLoad() {
        return 0;
    }

    @Override
    public void driving(double power) {

    }

    @Override
    public void steering(double targetDegree) {

    }
}
