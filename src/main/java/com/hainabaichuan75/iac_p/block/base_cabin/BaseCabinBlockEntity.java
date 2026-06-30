package com.hainabaichuan75.iac_p.block.base_cabin;

import com.hainabaichuan75.iac_p.content.blocks.cockpit.CockpitBlockEntity;
import com.hainabaichuan75.iac_p.index.ModCockpitBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * BaseCabinBlockEntity —— 基础座舱方块实体。
 * <p>
 * 继承自 {@link CockpitBlockEntity}，复用完整的动力链逻辑（发动机、变速箱、档位等），
 * 同时实现 {@link GeoBlockEntity} 以支持 GeckoLib 骨骼动画渲染。
 * <p>
 * 功能上与通用驾驶舱完全等效，仅视觉效果不同（GeckoLib 骨骼动画 vs 原版纹理）。
 */
public class BaseCabinBlockEntity extends CockpitBlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BaseCabinBlockEntity(BlockPos pos, BlockState state) {
        super(ModCockpitBlockEntityTypes.BASE_CABIN.get(), pos, state);
    }

    // ====== GeoBlockEntity 实现 ======

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 基础座舱为静态模型，无动画控制器
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
