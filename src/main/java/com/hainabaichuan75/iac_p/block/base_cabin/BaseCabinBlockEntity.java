package com.hainabaichuan75.iac_p.block.base_cabin;

import com.hainabaichuan75.iac_p.content.blocks.cockpit.CockpitBlockEntity;
import com.hainabaichuan75.iac_p.index.ModCockpitBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BaseCabinBlockEntity —— 基础座舱方块实体。
 * <p>
 * 继承自 {@link CockpitBlockEntity}，复用完整的动力链逻辑（发动机、变速箱、档位等），
 * 但使用不同的 BlockEntityType 以匹配 GeckoLib 渲染的方块。
 * <p>
 * 功能上与通用驾驶舱完全等效，仅视觉效果不同（GeckoLib 骨骼动画 vs 原版纹理）。
 */
public class BaseCabinBlockEntity extends CockpitBlockEntity {

    public BaseCabinBlockEntity(BlockPos pos, BlockState state) {
        super(ModCockpitBlockEntityTypes.BASE_CABIN.get(), pos, state);
    }

    @Override
    public void setRemoved() {
        com.hainabaichuan75.iac_p.affiliation.ComponentHost.unregisterComponent(this);
        super.setRemoved();
    }
}
