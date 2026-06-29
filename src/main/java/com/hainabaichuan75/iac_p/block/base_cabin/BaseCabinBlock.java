package com.hainabaichuan75.iac_p.block.base_cabin;

import com.hainabaichuan75.iac_p.core.util.AssemblyUtil;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BaseCabinBlock extends Block implements EntityBlock {
    public static final MapCodec<BaseCabinBlock> CODEC = simpleCodec(BaseCabinBlock::new);

    public BaseCabinBlock(Properties properties) {
        super(properties);
    }

    /* ====== 放置 ====== */

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        if (placer != null && level.getBlockEntity(pos) instanceof BaseCabinBlockEntity cabin) {
            // 玩家 yaw（0 = 南, 顺时针为正）映射到 0-7 索引：方块正面朝向玩家
            int index = Math.round(placer.getYRot() / 45f) & 7;
            cabin.setFacingIndex(index);
        }
    }

    /* ====== 渲染 ====== */

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos,
                                        @NotNull CollisionContext ctx) {
        return Shapes.block();
    }

    /* ====== 交互 ====== */

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hitResult) {
        return AssemblyUtil.tryAssembleOrDisassemble(level, pos, player);
    }

    /* ====== BlockEntity ====== */

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BaseCabinBlockEntity(pos, state);
    }

    /* ====== Codec ====== */

    @Override
    protected @NotNull MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
