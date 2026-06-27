/*
 * 智能按键映射处理器 —— 管理悬挂方块的按键绑定（手动 + 智能映射）。
 *
 * 职责：
 *   1. 存储 10 个按键绑定字符串（5 手动 + 5 智能映射）
 *   2. 提供"生效按键"的优先级解析（智能映射非空时优先，否则回退手动）
 *   3. 提供 NBT 序列化/反序列化
 *   4. 提供批量设置和重置
 *
 * 用法：
 *   SuspensionTestBlockEntity 持有 SmartKeyHandler 实例，
 *   在 getActiveKey* / setSmartKeyBindings / resetSmartKeys 中委托调用。
 */
package com.hainabaichuan75.iac_p.content.blocks.suspension_test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * 智能按键映射处理器。
 * <p>
 * 封装了手动按键绑定和智能映射按键的存储、优先级解析和 NBT 持久化。
 */
public final class SmartKeyHandler {

    // ===== 手动按键绑定 =====
    private String keyForward;
    private String keyBackward;
    private String keyLeft;
    private String keyRight;
    private String keyBrake;

    // ===== 智能映射按键（WASD 智能映射系统分配，不与手动按键冲突） =====
    // 当 smartKey* 非空时优先使用，否则回退到手动 key*
    private String smartKeyForward = "";
    private String smartKeyBackward = "";
    private String smartKeyLeft = "";
    private String smartKeyRight = "";
    private String smartKeyBrake = "";

    // ===== NBT 标签 =====
    private static final String TAG_KEY_FORWARD = "KeyForward";
    private static final String TAG_KEY_BACKWARD = "KeyBackward";
    private static final String TAG_KEY_LEFT = "KeyLeft";
    private static final String TAG_KEY_RIGHT = "KeyRight";
    private static final String TAG_KEY_BRAKE = "KeyBrake";
    private static final String TAG_SMART_KEY_FORWARD = "SmartKeyForward";
    private static final String TAG_SMART_KEY_BACKWARD = "SmartKeyBackward";
    private static final String TAG_SMART_KEY_LEFT = "SmartKeyLeft";
    private static final String TAG_SMART_KEY_RIGHT = "SmartKeyRight";
    private static final String TAG_SMART_KEY_BRAKE = "SmartKeyBrake";

    public SmartKeyHandler(String defaultForward, String defaultBackward,
                           String defaultLeft, String defaultRight, String defaultBrake) {
        this.keyForward = defaultForward;
        this.keyBackward = defaultBackward;
        this.keyLeft = defaultLeft;
        this.keyRight = defaultRight;
        this.keyBrake = defaultBrake;
    }

    // ==================================================================
    //  生效按键解析（智能映射优先）
    // ==================================================================

    public String getActiveKeyForward() {
        return smartKeyForward.isEmpty() ? keyForward : smartKeyForward;
    }

    public String getActiveKeyBackward() {
        return smartKeyBackward.isEmpty() ? keyBackward : smartKeyBackward;
    }

    public String getActiveKeyLeft() {
        return smartKeyLeft.isEmpty() ? keyLeft : smartKeyLeft;
    }

    public String getActiveKeyRight() {
        return smartKeyRight.isEmpty() ? keyRight : smartKeyRight;
    }

    public String getActiveKeyBrake() {
        return smartKeyBrake.isEmpty() ? keyBrake : smartKeyBrake;
    }

    // ==================================================================
    //  手动按键存取
    // ==================================================================

    public String getKeyForward() { return keyForward; }
    public String getKeyBackward() { return keyBackward; }
    public String getKeyLeft() { return keyLeft; }
    public String getKeyRight() { return keyRight; }
    public String getKeyBrake() { return keyBrake; }

    /**
     * 批量设置 5 个按键绑定。
     */
    public void setKeyBindings(String forward, String backward, String left, String right, String brake) {
        this.keyForward = forward;
        this.keyBackward = backward;
        this.keyLeft = left;
        this.keyRight = right;
        this.keyBrake = brake;
    }

    // ==================================================================
    //  智能映射按键存取
    // ==================================================================

    public String getSmartKeyForward() { return smartKeyForward; }
    public String getSmartKeyBackward() { return smartKeyBackward; }
    public String getSmartKeyLeft() { return smartKeyLeft; }
    public String getSmartKeyRight() { return smartKeyRight; }
    public String getSmartKeyBrake() { return smartKeyBrake; }

    /**
     * 批量设置智能映射按键。
     */
    public void setSmartKeyBindings(String forward, String backward, String left, String right, String brake) {
        this.smartKeyForward = forward;
        this.smartKeyBackward = backward;
        this.smartKeyLeft = left;
        this.smartKeyRight = right;
        this.smartKeyBrake = brake;
    }

    /**
     * 清除所有智能映射按键，回退到手动配置。
     */
    public void resetSmartKeys() {
        this.smartKeyForward = "";
        this.smartKeyBackward = "";
        this.smartKeyLeft = "";
        this.smartKeyRight = "";
        this.smartKeyBrake = "";
    }

    // ==================================================================
    //  NBT 序列化
    // ==================================================================

    /**
     * 写入 NBT。不调用 CompoundTag.put 系列方法以外的逻辑。
     */
    public void writeToNbt(CompoundTag tag) {
        tag.putString(TAG_KEY_FORWARD, this.keyForward);
        tag.putString(TAG_KEY_BACKWARD, this.keyBackward);
        tag.putString(TAG_KEY_LEFT, this.keyLeft);
        tag.putString(TAG_KEY_RIGHT, this.keyRight);
        tag.putString(TAG_KEY_BRAKE, this.keyBrake);
        tag.putString(TAG_SMART_KEY_FORWARD, this.smartKeyForward);
        tag.putString(TAG_SMART_KEY_BACKWARD, this.smartKeyBackward);
        tag.putString(TAG_SMART_KEY_LEFT, this.smartKeyLeft);
        tag.putString(TAG_SMART_KEY_RIGHT, this.smartKeyRight);
        tag.putString(TAG_SMART_KEY_BRAKE, this.smartKeyBrake);
    }

    /**
     * 从 NBT 读取。兼容旧档——无此标签时保持默认值。
     */
    public void readFromNbt(CompoundTag tag) {
        if (tag.contains(TAG_KEY_FORWARD)) {
            this.keyForward = tag.getString(TAG_KEY_FORWARD);
        }
        if (tag.contains(TAG_KEY_BACKWARD)) {
            this.keyBackward = tag.getString(TAG_KEY_BACKWARD);
        }
        if (tag.contains(TAG_KEY_LEFT)) {
            this.keyLeft = tag.getString(TAG_KEY_LEFT);
        }
        if (tag.contains(TAG_KEY_RIGHT)) {
            this.keyRight = tag.getString(TAG_KEY_RIGHT);
        }
        if (tag.contains(TAG_KEY_BRAKE)) {
            this.keyBrake = tag.getString(TAG_KEY_BRAKE);
        }
        if (tag.contains(TAG_SMART_KEY_FORWARD)) {
            this.smartKeyForward = tag.getString(TAG_SMART_KEY_FORWARD);
        }
        if (tag.contains(TAG_SMART_KEY_BACKWARD)) {
            this.smartKeyBackward = tag.getString(TAG_SMART_KEY_BACKWARD);
        }
        if (tag.contains(TAG_SMART_KEY_LEFT)) {
            this.smartKeyLeft = tag.getString(TAG_SMART_KEY_LEFT);
        }
        if (tag.contains(TAG_SMART_KEY_RIGHT)) {
            this.smartKeyRight = tag.getString(TAG_SMART_KEY_RIGHT);
        }
        if (tag.contains(TAG_SMART_KEY_BRAKE)) {
            this.smartKeyBrake = tag.getString(TAG_SMART_KEY_BRAKE);
        }
    }
}
