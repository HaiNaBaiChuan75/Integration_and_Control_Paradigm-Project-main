package com.hainabaichuan75.iac_p.core.util;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class AssemblyUtil {
    private AssemblyUtil() {
    }

    public static InteractionResult tryAssembleOrDisassemble(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) return InteractionResult.PASS;

        ServerLevel serverLevel = (ServerLevel) level;
        SubLevel subLevel = SubLevelUtil.getSubLevelAt(serverLevel, pos);
        if (subLevel == null) {
            SubLevel result = assemble(serverLevel, pos);
            if (result != null) return InteractionResult.SUCCESS;
            return InteractionResult.FAIL;
        }

        disassembleSubLevel(level, subLevel, pos);
        return InteractionResult.SUCCESS;
    }

    public static @Nullable SubLevel assemble(ServerLevel level, BlockPos pos) {
        var result = SubLevelAssemblyHelper.gatherConnectedBlocks(pos, level, 1000, null);

        if (result.assemblyState() != SubLevelAssemblyHelper.GatherResult.State.SUCCESS) {
            return null;
        }

        Set<BlockPos> blocks = result.blocks();
        BoundingBox3i bounds = result.boundingBox();
        if (bounds == null) {return null;}
        if (blocks == null) {return null;}
        return SubLevelAssemblyHelper.assembleBlocks(level, pos, blocks, bounds);
    }

    public static void disassembleSubLevel(@NotNull final Level level, @NotNull final SubLevel toDisassemble,
                                           @NotNull final BlockPos subLevelAnchor) {
        BlockPos disassemblyGoal =
                BlockPos.containing(toDisassemble.logicalPose().transformPosition(Vec3.atCenterOf(subLevelAnchor)));
        disassemblyGoal = disassemblyGoal.above();//TODO: 移除向上偏移
        Rotation rotation = Rotation.NONE;
        final BoundingBox3i plotBounds = new BoundingBox3i(toDisassemble.getPlot().getBoundingBox());
        final SubLevelAssemblyHelper.AssemblyTransform transform =
                new SubLevelAssemblyHelper.AssemblyTransform(subLevelAnchor, disassemblyGoal, 0, rotation,
                        (ServerLevel) level);

        final ObjectArrayList<BlockPos> blocks = SubLevelUtil.scanBlocks(level, toDisassemble);

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
