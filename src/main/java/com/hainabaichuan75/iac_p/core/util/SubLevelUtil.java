package com.hainabaichuan75.iac_p.core.util;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SubLevelUtil {
    private SubLevelUtil() {}

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


    public static @NotNull ObjectArrayList<BlockPos> scanBlocks(@NotNull Level level, @NotNull SubLevel toDisassemble) {
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
        return blocks;
    }
}
