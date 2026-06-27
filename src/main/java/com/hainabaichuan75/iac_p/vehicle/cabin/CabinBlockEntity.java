package com.hainabaichuan75.iac_p.vehicle.cabin;

import com.hainabaichuan75.iac_p.util.SubLevelUtil;
import com.hainabaichuan75.iac_p.vehicle.CapabilityProviderBlock;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class CabinBlockEntity extends BlockEntity {
    protected List<Vec3i> capabilityProviders = new ArrayList<>();

    public CabinBlockEntity(BlockEntityType<?> type, @NotNull BlockPos pos, @NotNull BlockState state) {
        super(type, pos, state);
    }

    public void tickServer(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state) {
        if ((level.getGameTime() + worldPosition.getX()) % 20 == 0) {
            //            scanCapabilityProviders(level);
            SubLevel subLevel = getSubLevel(level);
            if (subLevel != null) {
                SubLevelUtil.getAllBlockEntities(subLevel);
            }
        }
    }

    public void tickClient(@NotNull ClientLevel level, @NotNull BlockPos pos, @NotNull BlockState state) {}

    public @Nullable SubLevel getSubLevel(@NotNull ServerLevel level) {
        return SubLevelUtil.getSubLevelAt(level, worldPosition);
    }

    public boolean isAssembled(@NotNull ServerLevel level) {
        return getSubLevel(level) != null;
    }

    public void scanCapabilityProviders(@NotNull ServerLevel level) {
        SubLevel subLevel = getSubLevel(level);
        if (subLevel == null) {
            return;
        }

        ObjectArrayList<BlockPos> allBlocks = SubLevelUtil.scanBlocks(level, subLevel);
        List<Vec3i> relativePositions = new ArrayList<>();

        for (BlockPos absPos : allBlocks) {
            BlockState blockState = level.getBlockState(absPos);
            if (blockState.getBlock() instanceof CapabilityProviderBlock) {
                relativePositions.add(new Vec3i(absPos.getX() - worldPosition.getX(),
                        absPos.getY() - worldPosition.getY(), absPos.getZ() - worldPosition.getZ()));
            }
        }

        this.capabilityProviders = relativePositions;
    }

    BlockPos getAbsBlockPos(Vec3i offset) {
        return worldPosition.offset(offset);
    }

    public @NotNull List<BlockPos> getCapabilityProviders(ServerLevel level) {
        List<BlockPos> capabilityProviders = new ArrayList<>();
        for (Vec3i offset : this.capabilityProviders) {
            BlockPos absPos = getAbsBlockPos(offset);
            Block block = level.getBlockState(absPos).getBlock();
            if (block instanceof CapabilityProviderBlock) {
                capabilityProviders.add(absPos);
            }
        }
        return capabilityProviders;
    }

    // === NBT 持久化 ===

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag listTag = new ListTag();
        for (Vec3i relPos : capabilityProviders) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", relPos.getX());
            posTag.putInt("Y", relPos.getY());
            posTag.putInt("Z", relPos.getZ());
            listTag.add(posTag);
        }
        tag.put("CapabilityProviders", listTag);
    }

    @Override
    public void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        capabilityProviders.clear();
        if (tag.contains("CapabilityProviders", Tag.TAG_LIST)) {
            ListTag listTag = tag.getList("CapabilityProviders", Tag.TAG_COMPOUND);
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag posTag = listTag.getCompound(i);
                capabilityProviders.add(new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z")));
            }
        }
    }
}
