package com.hainabaichuan75.iac_p.index;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.entity.IACPSeatEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES
            = DeferredRegister.create(Registries.ENTITY_TYPE, IACP.MODID);

    /**
     * 座位实体 —— 极小碰撞箱、不可见，玩家坐上后跟随 SubLevel 物理位姿。
     * 归类为 {@link MobCategory#MISC}，不生成、不被加载卸载逻辑影响。
     */
    /**
     * 座位实体 EntityType 参数与 Create SeatEntity 对齐：
     * <ul>
     *   <li>trackingRange=5（Create 值，不可见实体无需远距离追踪）</li>
     *   <li>updateInterval=Integer.MAX_VALUE（不发送位置同步，客户端通过 SubLevel 本地跟随）</li>
     *   <li>velocityChanged=false（无需速度追踪）</li>
     *   <li>updatePos=true（允许 spawn 包同步初始位置）</li>
     * </ul>
     */
    public static final Supplier<EntityType<IACPSeatEntity>> IACP_SEAT =
            ENTITIES.register("seat", () -> EntityType.Builder.<IACPSeatEntity>of(
                            (type, level) -> new IACPSeatEntity(type, level), MobCategory.MISC)
                    .sized(0.25f, 0.35f)
                    .clientTrackingRange(5)
                    .updateInterval(Integer.MAX_VALUE)
                    .noSummon()
                    .build("iac_p.seat"));
}
