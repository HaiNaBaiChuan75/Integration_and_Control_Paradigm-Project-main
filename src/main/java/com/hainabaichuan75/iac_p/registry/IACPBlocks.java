package com.hainabaichuan75.iac_p.registry;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.block.base_cabin.BaseCabinBlock;
import com.hainabaichuan75.iac_p.block.shotgun.ShotGunBlock;
import com.hainabaichuan75.iac_p.block.simplewheel.SimpleWheelBlock;
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

    // ========== 轮子 ==========

    /**
     * SimpleWheel 简易轮子
     */
    public static final DeferredBlock<SimpleWheelBlock> SIMPLE_WHEEL = BLOCKS.register("simple_wheel",
            () -> new SimpleWheelBlock(BlockBehaviour.Properties.of().strength(3.0f, 6.0f).noOcclusion(), 0.7, 0.25,
                    0.7, 0.3,   // radius, thick, verticalOffset, horizontalOffset
            10, 0.2, 0.8));   // stiffness, damping, friction

    // ========== 武器 ==========

    /**
     * ShotGun 霰弹枪炮塔
     */
    public static final DeferredBlock<ShotGunBlock> SHOT_GUN = BLOCKS.register("shot_gun",
            () -> new ShotGunBlock(BlockBehaviour.Properties.of().strength(3.0f, 6.0f).noOcclusion()));
}
