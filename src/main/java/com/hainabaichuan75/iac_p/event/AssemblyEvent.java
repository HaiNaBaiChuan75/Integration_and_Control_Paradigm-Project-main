package com.hainabaichuan75.iac_p.event;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.joml.Quaterniond;


@EventBusSubscriber
public class AssemblyEvent {
    @SubscribeEvent
    public static void on(PlayerInteractEvent.RightClickBlock event){
        if(event.getSide().isClient()){return;}


        BlockPos pos = event.getPos();
        Level level = event.getLevel();
        Block block = level.getBlockState(pos).getBlock();
        if (!block.equals(Blocks.CAULDRON)) {
            return;
        }


        // 1. 从原点 BFS 搜索所有相连方块
        SubLevelAssemblyHelper.GatherResult result = SubLevelAssemblyHelper.gatherConnectedBlocks(
                pos,          // 起始方块位置
                (ServerLevel) level,              // ServerLevel
                1000,         // 方块数量上限（防止无限扩散）
                null               // FrontierPredicate = null 表示所有非空气方块都算相连
        );

        // 2. 检查搜索结果
        if (result.assemblyState() != SubLevelAssemblyHelper.GatherResult.State.SUCCESS) {
            // SUCCESS / TOO_MANY_BLOCKS / NO_BLOCKS 三种状态
            return;
        }

        // 3. 组装为物理 SubLevel
        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(
                (ServerLevel) level,
                pos,
                result.blocks(),
                result.boundingBox()
        );

    }

}
