package com.hainabaichuan75.iac_p.affiliation;

/**
 * 遗留枚举 —— 将在后续清理中移除。
 * <p>
 * 原有 {@code ComponentRegistry} 已被删除，部件角色信息分散到各 {@code PartBlockEntity} 子类中。
 * 此枚举保留仅为避免大规模重构时的编译错误。新代码不应引用此类。
 */
@Deprecated
public enum ComponentRole {

    COCKPIT,
    SUSPENSION,
    MACHINE_GUN_BASE,
    MACHINE_GUN_YAW,
    MACHINE_GUN_PITCH,
    SHOTGUN_BASE,
    SHOTGUN_YAW,
    SHOTGUN_PITCH,
    WEAPON_CANNON,
    TURRET_TEST,
    MODIFIER_ENGINE,
    DEBUG_GEAR;

    @Deprecated
    public static ComponentRole fromString(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return COCKPIT;
        }
    }
}
