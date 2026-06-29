package com.hainabaichuan75.iac_p.block.shotgun;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShotGunBlock extends Block implements EntityBlock {
    public static final MapCodec<ShotGunBlock> CODEC = simpleCodec(ShotGunBlock::new);

    public ShotGunBlock(Properties properties) {
        super(properties);
    }

    /* ====== 放置 ====== */

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        if (placer != null && level.getBlockEntity(pos) instanceof ShotGunBlockEntity gun) {
            // 炮塔底座正方向朝向玩家, 仅轴对齐 4 方向, 索引由 yaw 计算
            int index = Math.round(placer.getYRot() / 90f) & 3;
            gun.setFacingIndex(index);
        }
    }

    /* ====== 渲染 ====== */

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /* ====== BlockEntity ====== */

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new ShotGunBlockEntity(pos, state);
    }

    /* ====== Codec ====== */

    @Override
    protected @NotNull MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
