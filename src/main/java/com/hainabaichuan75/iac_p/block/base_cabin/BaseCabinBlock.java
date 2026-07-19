package com.hainabaichuan75.iac_p.block.base_cabin;

import com.google.common.base.Optional;
import com.hainabaichuan75.iac_p.block.cockpit.CockpitBlockEntity;
import com.hainabaichuan75.iac_p.entity.IACPSeatEntity;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;


/**
 * BaseCabinBlock —— 基础座舱方块（GeckoLib 渲染的单格驾驶舱）。
 * <p>
 * 功能上与 {@link com.hainabaichuan75.iac_p.block.cockpit.CockpitBlock} 等效，
 * 但仅占一格高度，使用 GeckoLib 骨骼动画模型渲染。
 * 视觉上作为"通用驾驶舱 2.0"，提供同样的载具驾驶功能。
 */
public class BaseCabinBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // 完整立方体形状（可被自定义模型覆盖）
    private static final VoxelShape SHAPE = Shapes.block();

    public BaseCabinBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    // ====== 空手右键 → 坐下 ======

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (player.isShiftKeyDown() || player instanceof FakePlayer)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        List<IACPSeatEntity> seats = level.getEntitiesOfClass(IACPSeatEntity.class, new AABB(pos));
        if (!seats.isEmpty()) {
            IACPSeatEntity seatEntity = seats.get(0);
            List<Entity> passengers = seatEntity.getPassengers();
            if (!passengers.isEmpty() && passengers.get(0) instanceof Player)
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!level.isClientSide) {
                seatEntity.ejectPassengers();
                player.startRiding(seatEntity);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;
        sitDown(level, pos, getLeashed(level, player).or(player));
        return ItemInteractionResult.SUCCESS;
    }


    public static void sitDown(Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide)
            return;
        IACPSeatEntity seat = new IACPSeatEntity(level, pos);
        level.addFreshEntity(seat);
        entity.startRiding(seat, true);
        if (entity instanceof TamableAnimal ta)
            ta.setInSittingPose(true);
    }


    public static Optional<Entity> getLeashed(Level level, Player player) {
        List<Entity> entities = player.level().getEntities((Entity) null, player.getBoundingBox()
                .inflate(10), e -> true);
        for (Entity e : entities)
            if (e instanceof Mob mob && mob.getLeashHolder() == player && SeatBlock.canBePickedUp(e))
                return Optional.of(mob);
        return Optional.absent();
    }

    // ====== BlockEntity ======
    //提供geckolib视觉渲染，必须使用EntityBlock接口，

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BaseCabinBlockEntity(pos, state);
    }

    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state, BlockEntityType<S> type) {
        return (l, p, s, be) -> {
            if (be instanceof CockpitBlockEntity cockpit) {
                cockpit.tick();
            }
        };
    }


}
