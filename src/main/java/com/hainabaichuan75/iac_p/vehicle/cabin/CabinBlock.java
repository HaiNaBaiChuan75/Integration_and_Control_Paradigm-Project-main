package com.hainabaichuan75.iac_p.vehicle.cabin;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public abstract class CabinBlock<BE extends CabinBlockEntity> extends Block implements EntityBlock {
    protected static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public CabinBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }
    public boolean assemble(ServerLevel level, BlockPos pos, BlockState state) {
        var result = SubLevelAssemblyHelper.gatherConnectedBlocks(pos, level, 1000, null);

        if (result.assemblyState() != SubLevelAssemblyHelper.GatherResult.State.SUCCESS) {
            onAssembleFailed(level, pos, state, result);
            return false;
        }

        // 3. 组装为物理 SubLevel
        Set<BlockPos> blocks = result.blocks();
        BoundingBox3i bounds = result.boundingBox();
        if (bounds == null) {return false;}
        if (blocks == null) {return false;}
        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks((ServerLevel) level, pos, blocks, bounds);
        return true;
    }

    public void onAssembleFailed(ServerLevel level, BlockPos pos, BlockState state,
                                 SubLevelAssemblyHelper.GatherResult result) {

    }

    @Nullable
    public static SubLevel getSubLevelAt(ServerLevel level, BlockPos pos) {
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) return null;

        ChunkPos chunkPos = new ChunkPos(pos);
        LevelPlot plot = container.getPlot(chunkPos);
        return plot != null ? plot.getSubLevel() : null;
    }
    public static void disassembleSubLevel(@NotNull final Level level,
                                           @NotNull final SubLevel toDisassemble,
                                           @NotNull final BlockPos subLevelAnchor,
                                           @NotNull final BlockPos disassemblyGoal,
                                           @NotNull final Rotation rotation) {

        final BoundingBox3i plotBounds = new BoundingBox3i(toDisassemble.getPlot().getBoundingBox());
        final SubLevelAssemblyHelper.AssemblyTransform transform = new SubLevelAssemblyHelper.AssemblyTransform(subLevelAnchor, disassemblyGoal, rotation == Rotation.NONE ? 0 : (4 - rotation.ordinal()), rotation, (ServerLevel) level);

        final ObjectArrayList<BlockPos> blocks = new ObjectArrayList<>();
        final LevelPlot plot = toDisassemble.getPlot();
        for (final PlotChunkHolder chunk : plot.getLoadedChunks()) {
            final BoundingBox3ic localChunkBounds = chunk.getBoundingBox();

            if (localChunkBounds == null || localChunkBounds == BoundingBox3i.EMPTY) {
                continue;
            }

            for (int x = localChunkBounds.minX(); x <= localChunkBounds.maxX(); x++) {
                for (int y = localChunkBounds.minY(); y <= localChunkBounds.maxY(); y++) {
                    for (int z = localChunkBounds.minZ(); z <= localChunkBounds.maxZ(); z++) {
                        final BlockPos pos = new BlockPos(x + chunk.getPos().getMinBlockX(), y, z + chunk.getPos().getMinBlockZ());
                        final BlockState state = level.getBlockState(pos);
                        if (!state.isAir()) {
                            blocks.add(pos);
                        }
                    }
                }
            }
        }

        // if there's no blocks in the given sublevel, don't attempt to move the blocks
        if (!blocks.isEmpty()) {
            ((ServerLevelPlot) toDisassemble.getPlot()).kickAllEntities();
            SubLevelAssemblyHelper.moveBlocks((ServerLevel) level, transform, blocks);
        }

        SubLevelAssemblyHelper.moveTrackingPoints((ServerLevel) level, plotBounds, null, transform);

        // 从容器中移除已拆解的 SubLevel
        final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer((ServerLevel) level);
        if (container != null) {
            container.removeSubLevel(toDisassemble, SubLevelRemovalReason.REMOVED);
        }
    }
    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
        if (level.isClientSide) {return InteractionResult.PASS;}
        ServerLevel serverLevel = (ServerLevel) level;
        SubLevel subLevel = getSubLevelAt(serverLevel, pos);
        if (subLevel == null) {
            boolean result = assemble(serverLevel, pos, state);
            if (result){return InteractionResult.SUCCESS;}
            return InteractionResult.FAIL;
        }
        BlockPos goal = BlockPos.containing(subLevel.logicalPose().transformPosition(Vec3.atCenterOf(pos)));
        goal = goal.above();
        disassembleSubLevel(level, subLevel, pos, goal, Rotation.NONE);

        return InteractionResult.SUCCESS;
    }

    @Override
    public abstract @Nullable BE newBlockEntity(@NotNull BlockPos blockPos, @NotNull BlockState blockState);

    @Override
    protected @NotNull BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        return state.mirror(mirror);
    }
}
