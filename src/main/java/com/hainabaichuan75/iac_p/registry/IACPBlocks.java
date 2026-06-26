package com.hainabaichuan75.iac_p.registry;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.block.base_cabin.BaseCabinBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;


public abstract class IACPBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IACP.MODID);

    // ========== 载具舱室 ==========

    /** BaseCabin 载具舱室 */
    public static final DeferredBlock<BaseCabinBlock> BASE_CABIN = BLOCKS.register("base_cabin",
            () -> new BaseCabinBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f, 6.0f)
                    .noOcclusion()));
}
