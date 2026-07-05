package com.hainabaichuan75.iac_p.part;

import com.hainabaichuan75.iac_p.ecs.part.Part;
import com.hainabaichuan75.iac_p.part.field.YawPitch;

public interface AimingMount extends Part {

    void setAngles(YawPitch angles);

    YawPitch getAngles();

    default void setAngles(double yaw, double pitch) {
        setAngles(new YawPitch(yaw, pitch));
    }
}
