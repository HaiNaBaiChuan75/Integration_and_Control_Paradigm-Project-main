package com.hainabaichuan75.iac_p.content.blocks.shotgun;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ShotGunBlock —— 霰弹枪炮塔方块（GeckoLib 渲染的单方块架构）。
 * <p>
 * 使用 RenderShape.ENTITYBLOCK_ANIMATED 让 GeckoLib 接管渲染，
 * 骨骼：base → yaw → yaw_ani → pitch → pitch_ani → 炮管。
 * 放置时根据玩家朝向设置 facingIndex（0-3），用于初始方向对齐。
 * <p>
 * 对应 Crossout 第5章「运动学武器装饰器」——武器作为纯视觉+碰撞元素，
 * 不创建额外 SubLevel，不产生物理反力。
 */
public class ShotGunBlock extends Block implements EntityBlock {

    public static final MapCodec<ShotGunBlock> CODEC = simpleCodec(ShotGunBlock::new);

    public ShotGunBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        if (placer != null && level.getBlockEntity(pos) instanceof ShotGunBlockEntity gun) {
            // 根据玩家放置时的朝向设置 facingIndex（0=南, 1=西, 2=北, 3=东）
            int index = Math.round(placer.getYRot() / 90f) & 3;
            gun.setFacingIndex(index);
        }
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new ShotGunBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> ((ShotGunBlockEntity) be).tick();
    }

    @Override
    protected @NotNull MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
