package com.hainabaichuan75.iac_p.content.blocks.assembly_barrier;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 装配扫描屏障方块 —— 装饰性方块，用于铺设车库地板等。
 * <p>
 * 该方块不会被 BFS 扫描纳入载具装配范围，作为扫描"黑名单"使用。
 * 玩家可以将其铺在地上作为车库地板，装配扫描会在此方块处停止。
 */
public class AssemblyBarrierBlock extends Block {

    public AssemblyBarrierBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(2.0f, 6.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops());
    }

    public AssemblyBarrierBlock(Properties properties) {
        super(properties);
    }
}
