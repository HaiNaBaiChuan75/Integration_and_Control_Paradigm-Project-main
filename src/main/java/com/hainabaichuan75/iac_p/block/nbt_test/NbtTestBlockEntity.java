package com.hainabaichuan75.iac_p.block.nbt_test;

import com.hainabaichuan75.iac_p.index.ModBlockEntityTypes;
import com.hainabaichuan75.iac_p.index.ModBlockEntityTypes;
import com.hainabaichuan75.iac_p.vehicle.api.ComponentBE;
import com.hainabaichuan75.iac_p.vehicle.common.Engine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 测试 BE — 组合 {@link Engine}。
 */
public class NbtTestBlockEntity extends ComponentBE {

    public final Engine engine = new Engine(this);

    public NbtTestBlockEntity(@NotNull BlockPos pos, @NotNull BlockState blockState) {
        super(ModBlockEntityTypes.NBT_TEST.get(), pos, blockState);
    }
}
