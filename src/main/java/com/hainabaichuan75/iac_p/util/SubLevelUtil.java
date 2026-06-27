package com.hainabaichuan75.iac_p.util;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class SubLevelUtil {

    public static @Nullable SubLevel assemble(ServerLevel level, BlockPos pos) {
        var result = SubLevelAssemblyHelper.gatherConnectedBlocks(pos, level, 1000, null);

        if (result.assemblyState() != SubLevelAssemblyHelper.GatherResult.State.SUCCESS) {
            return null;
        }

        Set<BlockPos> blocks = result.blocks();
        BoundingBox3i bounds = result.boundingBox();
        if (bounds == null) {return null;}
        if (blocks == null) {return null;}
        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, pos, blocks, bounds);
        return subLevel;
    }

    @Nullable
    public static SubLevel getSubLevelAt(ServerLevel level, BlockPos pos) {
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        ChunkPos chunkPos = new ChunkPos(pos);
        LevelPlot plot = container.getPlot(chunkPos);
        return plot != null ? plot.getSubLevel() : null;
    }

    public static void disassembleSubLevel(@NotNull final Level level, @NotNull final SubLevel toDisassemble,
                                           @NotNull final BlockPos subLevelAnchor) {
        BlockPos disassemblyGoal =
                BlockPos.containing(toDisassemble.logicalPose().transformPosition(Vec3.atCenterOf(subLevelAnchor)));
        disassemblyGoal = disassemblyGoal.above();
        Rotation rotation = Rotation.NONE;
        final BoundingBox3i plotBounds = new BoundingBox3i(toDisassemble.getPlot().getBoundingBox());
        final SubLevelAssemblyHelper.AssemblyTransform transform =
                new SubLevelAssemblyHelper.AssemblyTransform(subLevelAnchor, disassemblyGoal,
                        rotation == Rotation.NONE ? 0 : (4 - rotation.ordinal()), rotation, (ServerLevel) level);

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
                        final BlockPos pos = new BlockPos(x + chunk.getPos().getMinBlockX(), y,
                                z + chunk.getPos().getMinBlockZ());
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
}
