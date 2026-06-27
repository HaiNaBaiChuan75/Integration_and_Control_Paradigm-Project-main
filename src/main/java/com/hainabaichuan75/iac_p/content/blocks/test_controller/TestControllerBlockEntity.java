package com.hainabaichuan75.iac_p.content.blocks.test_controller;

import com.hainabaichuan75.iac_p.index.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * TestControllerBlockEntity —— GeckoLib 测试 BlockEntity。
 * <p>
 * 播放一个简单的旋转动画（spin），验证 GeckoLib 在 SubLevel 内的渲染兼容性。
 */
public class TestControllerBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final RawAnimation SPIN_ANIM = RawAnimation.begin().thenLoop("spin");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TestControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.TEST_CONTROLLER.get(), pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, animState -> {
            return animState.setAndContinue(SPIN_ANIM);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
