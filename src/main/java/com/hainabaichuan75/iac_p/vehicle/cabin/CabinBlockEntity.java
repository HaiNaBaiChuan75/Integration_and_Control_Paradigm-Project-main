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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class CabinBlockEntity extends BlockEntity {
    private static final int SCAN_INTERVAL = 20; // 扫描间隔（tick）
    private int tickCounter = 0;

    /**
     * 记录 SubLevel 内所有 CapabilityProviderBlock 的相对坐标
     */
    private List<Vec3i> capabilityProviderRelPositions = new ArrayList<>();

    public CabinBlockEntity(BlockEntityType<?> type, @NotNull BlockPos pos, @NotNull BlockState state) {
        super(type, pos, state);
    }

    public void tickServer(ServerLevel level, BlockPos pos, BlockState state) {

    }

    public void tickClient(ClientLevel level, BlockPos pos, BlockState state) {}

    public void tick(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state) {
        if (level.isClientSide) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        tickCounter++;
        if (tickCounter < SCAN_INTERVAL) {
            return;
        }
        tickCounter = 0;

        // 仅当自己在 SubLevel 内才扫描
        SubLevel subLevel = SubLevelUtil.getSubLevelAt(serverLevel, worldPosition);
        if (subLevel == null) return;

        // 扫描 SubLevel 中所有非空气方块
        ObjectArrayList<BlockPos> allBlocks = SubLevelUtil.scanBlocks(level, subLevel);
        List<Vec3i> relativePositions = new ArrayList<>();

        for (BlockPos absPos : allBlocks) {
            BlockState blockState = level.getBlockState(absPos);
            if (blockState.getBlock() instanceof CapabilityProviderBlock) {
                // 转换为相对于自身方块的坐标
                relativePositions.add(new Vec3i(absPos.getX() - worldPosition.getX(),
                        absPos.getY() - worldPosition.getY(), absPos.getZ() - worldPosition.getZ()));
            }
        }

        this.capabilityProviderRelPositions = relativePositions;
    }

    /**
     * @return 当前缓存的 CapabilityProviderBlock 相对坐标列表（不可变视图）
     */
    public @NotNull List<Vec3i> getCapabilityProviderPositions() {
        return capabilityProviderRelPositions;
    }

    // === NBT 持久化 ===

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag listTag = new ListTag();
        for (Vec3i relPos : capabilityProviderRelPositions) {
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
        capabilityProviderRelPositions.clear();
        if (tag.contains("CapabilityProviders", Tag.TAG_LIST)) {
            ListTag listTag = tag.getList("CapabilityProviders", Tag.TAG_COMPOUND);
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag posTag = listTag.getCompound(i);
                capabilityProviderRelPositions.add(new BlockPos(posTag.getInt("X"), posTag.getInt("Y"),
                        posTag.getInt("Z")));
            }
        }
    }
}
