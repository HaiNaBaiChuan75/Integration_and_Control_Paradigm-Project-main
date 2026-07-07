package com.hainabaichuan75.iac_p.index;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.block.machine_gun.MachineGunBaseBlockEntity;
import com.hainabaichuan75.iac_p.block.shotgun.ShotGunBlockEntity;
import com.hainabaichuan75.iac_p.block.shotgun.ShotgunBaseBlockEntity;
import com.hainabaichuan75.iac_p.block.suspension_test.SuspensionTestBlockEntity;
import com.hainabaichuan75.iac_p.block.test_blank.TestBlankBlockEntity;
import com.hainabaichuan75.iac_p.content.blocks.debug_gear.DebugGearBlockEntity;
import com.hainabaichuan75.iac_p.content.blocks.debug_swivel.DebugSwivelBearingBlockEntity;
import com.hainabaichuan75.iac_p.content.blocks.test_controller.TestControllerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * BlockEntity 类型注册中心（主）。
 * <p>
 * 包含：SUSPENSION_TEST, MACHINE_GUN_BASE, SHOTGUN_BASE,
 * DEBUG_GEAR, DEBUG_SWIVEL_BEARING, TEST_CONTROLLER。
 * <p>
 * 驾驶舱/Cockpit 系列 BE 保留单独注册类（ModCockpitBlockEntityTypes /
 * ModLightCockpitBlockEntityTypes）以避免 Block ↔ BE 循环依赖。
 */
public class ModBlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IACP.MODID);

    public static final Supplier<BlockEntityType<SuspensionTestBlockEntity>> SUSPENSION_TEST =
            BLOCK_ENTITY_TYPES.register("suspension_test",
                    () -> BlockEntityType.Builder.of(
                            SuspensionTestBlockEntity::new,
                            ModBlocks.SUSPENSION_TEST.get()
                    ).build(null));

    public static final Supplier<BlockEntityType<MachineGunBaseBlockEntity>> MACHINE_GUN_BASE =
            BLOCK_ENTITY_TYPES.register("machine_gun_base",
                    () -> BlockEntityType.Builder.of(
                            MachineGunBaseBlockEntity::new,
                            ModBlocks.MACHINE_GUN_BASE.get()
                    ).build(null));

    public static final Supplier<BlockEntityType<ShotgunBaseBlockEntity>> SHOTGUN_BASE =
            BLOCK_ENTITY_TYPES.register("shotgun_base",
                    () -> BlockEntityType.Builder.of(
                            ShotgunBaseBlockEntity::new,
                            ModBlocks.SHOTGUN_BASE.get()
                    ).build(null));

    public static final Supplier<BlockEntityType<ShotGunBlockEntity>> SHOTGUN_TURRET =
            BLOCK_ENTITY_TYPES.register("shotgun_turret",
                    () -> BlockEntityType.Builder.of(
                            ShotGunBlockEntity::new,
                            ModBlocks.SHOTGUN_TURRET.get()
                    ).build(null));

    // ===== 以下从旧独立注册类合并而来 =====

    public static final Supplier<BlockEntityType<DebugGearBlockEntity>> DEBUG_GEAR =
            BLOCK_ENTITY_TYPES.register("debug_gear",
                    () -> BlockEntityType.Builder.of(
                            DebugGearBlockEntity::new,
                            ModBlocks.DEBUG_GEAR.get()
                    ).build(null));

    public static final Supplier<BlockEntityType<DebugSwivelBearingBlockEntity>> DEBUG_SWIVEL_BEARING =
            BLOCK_ENTITY_TYPES.register("debug_swivel_bearing",
                    () -> BlockEntityType.Builder.of(
                            DebugSwivelBearingBlockEntity::new,
                            ModBlocks.DEBUG_SWIVEL_BEARING.get()
                    ).build(null));

    public static final Supplier<BlockEntityType<TestControllerBlockEntity>> TEST_CONTROLLER =
            BLOCK_ENTITY_TYPES.register("test_controller",
                    () -> BlockEntityType.Builder.of(
                            TestControllerBlockEntity::new,
                            ModBlocks.TEST_CONTROLLER.get()
                    ).build(null));

    // ===== 测试空白方块（所有变体共用同一 BE 类型） =====

    public static final Supplier<BlockEntityType<TestBlankBlockEntity>> TEST_BLANK =
            BLOCK_ENTITY_TYPES.register("test_blank",
                    () -> BlockEntityType.Builder.of(
                            TestBlankBlockEntity::new, ModBlocks.TEST_BLANK.get(), ModBlocks.TEST_BLANK_ENGINE.get(),
                            ModBlocks.TEST_BLANK_WHEEL.get(), ModBlocks.TEST_BLANK_GIMBAL.get()
                    ).build(null));
}
