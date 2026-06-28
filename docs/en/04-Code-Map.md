# Code Map

> Quick reference for finding the right file.

## Entry Points

| File | Purpose |
|------|---------|
| `src/main/java/.../IACP.java` | Main mod class — registration, event bus setup |
| `src/main/java/.../IACPClient.java` | Client-side setup |
| `src/main/java/.../Config.java` | Mod configuration |

## Networking

| File | Direction | Purpose |
|------|:---------:|---------|
| `.../ModNetworking.java` | — | Payload registration |
| `.../SeatMountC2SPacket.java` | C→S | Mount/dismount request |
| `.../MountedStateS2CPacket.java` | S→C | Mount state + SubLevel UUID + vehicle mass |
| `.../VehicleControlC2SPacket.java` | C→S | WASD/brake control input |
| `.../GearShiftC2SPacket.java` | C→S | Shift up/down request |
| `.../MachineGunTargetC2SPacket.java` | C→S | 🆕 Weapon aim target coordinates (replaces TurretTargetC2SPacket) |
| `.../VehicleStateS2CPacket.java` | S→C | 🆕 Vehicle real-time state sync (RPM/gear/torque/speed) |
| `.../WeaponSoundS2CPacket.java` | S→C | 🆕 Weapon fire sound sync |
| `.../WeaponFireC2SPacket.java` | C→S | Weapon fire hit position + damage |
| `.../AnchorConfigC2SPacket.java` | C→S | Anchor point coordinate update |
| `.../AnchorDataS2CPacket.java` | S→C | Anchor + axis world position data |
| `.../GrindstoneConfigC2SPacket.java` | C→S | Grindstone/rod orientation config |
| `.../TireConfigC2SPacket.java` | C→S | Tire pressure config |

## Client (Rendering & Input)

| File | Purpose |
|------|---------|
| `.../client/ClientMountHandler.java` | Client-side mount state management + scanOrientation() + orientation cache + smartMappingActive cache |
| `.../client/ClientEvents.java` | Key input, raycast, fire control, player hiding |
| `.../client/WeaponOverlay.java` | Crosshair rendering, raycast system (aim + damage) |
| `.../client/VehicleDebugOverlay.java` | In-game F3-style debug HUD |
| `.../client/renderer/BulletTrailRenderer.java` | White line fire trail rendering |
| `.../client/renderer/AxisLineRenderer.java` | Turret axis debug rendering |
| `.../client/screen/VehicleKeyConfigScreen.java` | Keybinding config GUI + smart key display column |
| `.../client/screen/GrindstoneConfigScreen.java` | Grindstone orientation & anchor config GUI |
| `.../client/screen/VehicleOrientationScreen.java` | WASD Smart Mapping GUI: FACING stats + Car Mode/Reverse/Toggle |
| `.../client/VehicleOrientationData.java` | Record counting suspension HORIZONTAL_FACING distribution |
| `.../mixin/CameraMixin.java` | Orbital camera override |

## Blocks & Block Entities

| File | Purpose |
|------|---------|
| `.../content/blocks/seat/SeatBlock.java` | Original vehicle core (legacy) |
| `.../content/blocks/cockpit/CockpitBlock.java` | Cockpit dual-block structure |
| `.../content/blocks/cockpit/CockpitBlockEntity.java` | Cockpit BE — orchestrates EngineModel/TransmissionModel, state + NBT |
| `.../content/blocks/cockpit/PowertrainConstants.java` | 🆕 Powertrain compile-time constants (engine/transmission/throttle) |
| `.../content/blocks/cockpit/EngineModel.java` | 🆕 Engine pure functions (mass-adaptive torque, torque curve, throttle, coupling) |
| `.../content/blocks/cockpit/TransmissionModel.java` | 🆕 Transmission pure functions (gear shift, RPM sync, output distribution) |
| `.../content/blocks/suspension_test/SuspensionTestBlock.java` | Suspension + wheel test block |
| `.../content/blocks/suspension_test/SuspensionConstants.java` | 🆕 All suspension/tire/steering compile-time constants |
| `.../content/blocks/suspension_test/TirePhysicsCalculator.java` | 🆕 Tire physics pure functions (rolling resistance, burst, deflection) |
| `.../content/blocks/suspension_test/BrushTireModel.java` | 🆕 Brush tire lateral slip pure function |
| `.../content/blocks/suspension_test/SuspensionTestBlockEntity.java` | Suspension BE — orchestrates physics tick + smartKey* fallback |
| `.../content/blocks/turret/TurretTestBlock.java` | 🆕 Crossout-style single-block turret (GeckoLib) |
| `.../content/blocks/turret/TurretTestBlockEntity.java` | 🆕 TurretTest BE: yaw/pitch + driveImmediate |
| `.../content/blocks/turret/TurretClearanceSolver.java` | 🆕 Grid-level turret rotation clearance solver |
| `.../content/blocks/turret/ClearanceAlgorithmTest.java` | 🆕 Standalone clearance test (9 scenarios) |
| `.../content/blocks/machine_gun/MachineGunBaseBlock.java` | 🆕 Machine gun block + BE + AimController |
| `.../content/blocks/shotgun/ShotgunBaseBlock.java` | 🆕 Shotgun block + BE + AimController |
| `.../content/blocks/debug_gear/DebugGearBlock.java` | Debug gear for RPM diagnosis |
| `.../content/blocks/debug_gear/DebugGearBlockEntity.java` | Debug gear RPM printout |

## Server Events

| File | Purpose |
|------|---------|
| `.../events/PlayerMountTracker.java` | Mount state tracking, `tick()` for aim controller |
| `.../events/ServerMountHandler.java` | Server-side mount logic |
| `.../events/MountedProtectionHandler.java` | Damage immunity & aggro suppression |
| `.../events/SubLevelScanner.java` | 🆕 SubLevel chunk traversal utility (unifies 9 boilerplate loops) |

## Index / Registration

| File | Purpose |
|------|---------|
| `.../index/ModBlocks.java` | Block registration |
| `.../index/ModItems.java` | Item registration |
| `.../index/ModEntities.java` | Entity registration |
| `.../index/ModBlockEntityTypes.java` | Block entity registration |
| `.../index/ModCreativeModeTabs.java` | Creative tab registration |
