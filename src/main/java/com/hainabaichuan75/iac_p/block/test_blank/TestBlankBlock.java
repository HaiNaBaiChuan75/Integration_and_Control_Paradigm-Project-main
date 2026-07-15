package com.hainabaichuan75.iac_p.block.test_blank;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.ComponentKey;
import com.hainabaichuan75.iac_p.ecs.v2.component.rotation.CubeRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

/**
 * 测试用空白方块 —— 可携带预设组件列表，放置时由 BE 复制。
 * <p>
 * 每个 Block 实例持有不同的默认组件列表，在构造时传入。
 * BE 创建时从 Block 读取列表并将每个条目复制到组件 map 中。
 * 也可通过 NBT 手动添加额外组件。
 * <p>
 * 用于测试场景：验证不同默认组件组合的初始化路径、ComponentKey fallback
 * 机制、多组件组合等。
 */
public class TestBlankBlock extends Block implements EntityBlock {

    /**
     * 默认组件条目 —— 关联一个 {@link ComponentKey} 与其默认实例值。
     *
     * @param key   组件键
     * @param value 默认实例值
     */
    public record DefaultComponentEntry(@NotNull ComponentKey<?> key, @NotNull Object value) {}

    private final List<DefaultComponentEntry> defaultComponents;

    public TestBlankBlock(@NotNull BlockBehaviour.Properties properties,
                          @NotNull List<DefaultComponentEntry> defaultComponents) {
        super(properties);
        this.defaultComponents = List.copyOf(defaultComponents);
        // 注册 HORIZONTAL_FACING 属性
        registerDefaultState(stateDefinition.any().setValue(HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext ctx) {
        return defaultBlockState().setValue(HORIZONTAL_FACING, ctx.getHorizontalDirection().getOpposite());
    }

    /**
     * @return 此方块变体携带的默认组件列表（不可变快照）
     */
    @NotNull
    public List<DefaultComponentEntry> getDefaultComponents() {
        return defaultComponents;
    }

    // ====== BlockEntity ======

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new TestBlankBlockEntity(pos, state);
    }
}
