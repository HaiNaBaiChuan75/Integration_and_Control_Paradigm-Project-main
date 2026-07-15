package com.hainabaichuan75.iac_p.block.test_blank;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.ComponentKey;
import com.hainabaichuan75.iac_p.ecs.v2.entity.PartBlockEntity;
import com.hainabaichuan75.iac_p.index.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 测试用空白 Part —— 创建时从所属 Block 拷贝默认组件列表。
 * <p>
 * 放置后 BE 的组件 map 包含 Block 实例预设的默认组件，
 * 可通过 NBT 额外添加或覆盖。
 */
public class TestBlankBlockEntity extends PartBlockEntity {

    public TestBlankBlockEntity(@NotNull BlockPos pos, @NotNull BlockState blockState) {
        super(ModBlockEntityTypes.TEST_BLANK.get(), pos, blockState);
        // 从 Block 读取默认组件列表并拷贝到组件 map
        if (blockState.getBlock() instanceof TestBlankBlock tbb) {
            copyDefaults(tbb);
        }
        // 放置时自动注入 CubeRotation（从 HORIZONTAL_FACING 转换）
        if (blockState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
            var facing = blockState.getValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
            setComponent(com.hainabaichuan75.iac_p.ecs.v2.component.rotation.CubeRotation.KEY,
                    com.hainabaichuan75.iac_p.ecs.v2.component.rotation.CubeRotation.fromDirection(facing));
        }
    }

    /**
     * 拷贝默认组件到 BE 的组件 map。
     * <p>
     * {@link TestBlankBlock.DefaultComponentEntry} 类型擦除了 key/value 的绑定关系，
     * 但类型安全性在构造条目时已保证（key 的类型形参与 value 的运行时类型匹配）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void copyDefaults(@NotNull TestBlankBlock tbb) {
        for (var entry : tbb.getDefaultComponents()) {
            setComponent((ComponentKey) entry.key(), entry.value());
        }
    }
}
