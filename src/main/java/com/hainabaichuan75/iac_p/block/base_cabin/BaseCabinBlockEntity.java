package com.hainabaichuan75.iac_p.block.base_cabin;

import com.hainabaichuan75.iac_p.core.vehicle.VehiclePartBlockEntity;
import com.hainabaichuan75.iac_p.registry.IACPBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class BaseCabinBlockEntity extends VehiclePartBlockEntity implements GeoAnimatable {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BaseCabinBlockEntity(BlockPos pos, BlockState state) {
        super(IACPBlockEntities.BASE_CABIN.get(), pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 无动画
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return level != null ? level.getGameTime() : 0;
    }
}
