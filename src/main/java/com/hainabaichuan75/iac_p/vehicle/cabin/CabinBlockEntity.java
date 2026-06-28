package com.hainabaichuan75.iac_p.vehicle.cabin;

import com.hainabaichuan75.iac_p.util.SubLevelUtil;
import com.hainabaichuan75.iac_p.vehicle.System;
import com.hainabaichuan75.iac_p.vehicle.Systems;
import com.hainabaichuan75.iac_p.vehicle.VehiclePartBlockEntity;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class CabinBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    // TODO: 在这个位置设置键盘输入, 并设置为public

    public CabinBlockEntity(BlockEntityType<?> type, @NotNull BlockPos pos, @NotNull BlockState state) {
        super(type, pos, state);
    }

    public List<VehiclePartBlockEntity> getAllParts(@NotNull SubLevel subLevel) {
        ArrayList<VehiclePartBlockEntity> list = new ArrayList<>();
        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof VehiclePartBlockEntity) {
                list.add((VehiclePartBlockEntity) actor);
            }
        }
        return list;
    }

    public void tickServer(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state) {}

    public void tickClient(@NotNull ClientLevel level, @NotNull BlockPos pos, @NotNull BlockState state) {}

    public @Nullable SubLevel getSubLevel(@NotNull ServerLevel level) {
        return SubLevelUtil.getSubLevelAt(level, worldPosition);
    }

    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        List<VehiclePartBlockEntity> parts = getAllParts(subLevel);
        for (System system : Systems.SYSTEMS) {
            system.onSubLeveTick(subLevel, this, parts);
        }
    }

    public boolean isAssembled(@NotNull ServerLevel level) {
        return getSubLevel(level) != null;
    }

    // === NBT 持久化 ===

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
    }

    @Override
    public void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
    }
}
