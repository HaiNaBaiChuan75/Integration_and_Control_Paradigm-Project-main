package com.hainabaichuan75.iac_p.block.test_blank;

import com.hainabaichuan75.iac_p.ecs.v2.entity.PartBlockEntity;
import com.hainabaichuan75.iac_p.index.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 测试用空白 Part —— 不携带任何默认组件，可通过 NBT 添加。
 * <p>
 * 放置后 BE 的组件 map 为空，没有 {@code EngineDef}、{@code WheelState}
 * 等任何预置组件。使用 {@code /data merge block ~ ~ ~ {vehicle_parts:{...}}}
 * 手动写入 NBT 后，PartBlockEntity 的 NBT 序列化会自动解码并挂载对应组件。
 */
public class TestBlankBlockEntity extends PartBlockEntity {

    public TestBlankBlockEntity(@NotNull BlockPos pos, @NotNull BlockState blockState) {
        super(ModBlockEntityTypes.TEST_BLANK.get(), pos, blockState);
        // 无默认组件 —— 空 map，完全由 NBT 决定
    }
}
