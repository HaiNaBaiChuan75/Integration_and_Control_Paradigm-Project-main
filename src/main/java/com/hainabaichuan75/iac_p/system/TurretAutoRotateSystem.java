package com.hainabaichuan75.iac_p.system;

import com.hainabaichuan75.iac_p.ecs.part.PartBlockEntity;
import com.hainabaichuan75.iac_p.ecs.part.TurretPart;
import com.hainabaichuan75.iac_p.ecs.system.VehicleTickSystem;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 炮塔自动旋转 System —— 当炮塔的 AUTO_ROTATE 属性激活时，每 tick 递增 yaw。
 * <p>
 * 来源：{@link com.hainabaichuan75.iac_p.block.turret.TurretTestBlockEntity#tick()} 中的 2 行逻辑。
 * <p>
 * <b>纯数据流</b>：读取 {@link TurretPart#isAutoRotate()}，写入 {@link TurretPart#setYaw(double)}。
 */
public class TurretAutoRotateSystem implements VehicleTickSystem {

    /** 自动旋转速度（度/游戏刻） */
    private static final double AUTO_ROTATE_SPEED = 2.0;

    @Override
    public void onTick(@NotNull ServerSubLevel subLevel, @NotNull List<PartBlockEntity> parts) {
        for (PartBlockEntity part : parts) {
            if (part instanceof TurretPart turret && turret.isAutoRotate()) {
                turret.setYaw(turret.getYaw() + AUTO_ROTATE_SPEED);
            }
        }
    }
}
