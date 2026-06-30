# Current Status & Roadmap

> Last updated: 2026-06-30 (WG branch porting + Physics Assembler)

## ✅ Completed Features

All features across all subsystems are completed. See [Feature Overview](02-Feature-Overview.md) for the full list.

**Key milestones**:
- **06-17**: Arcade mode powertrain — throttle-direct RPM, Binary Grip, pure-ratio transmission. Removed: friction circle, load transfer, brush tire, quadratic drag, clutch state machine.
- **06-19**: Multiplayer fixes — player hiding, sound broadcasting, particle sync, chunk-watch crack resend.
- **06-27**: Engine simplification — removed torque curve, mass-adaptive torque, load factor. Torque is now throttle-linear only.
- **06-30**: WG branch porting — BaseCabinBlock (GeckoLib single-block cockpit), Shotgun turret system.
- **06-30**: Physics Assembler — Ctrl+Right Click to assemble (BFS → SubLevel) or disassemble (PD servo alignment → safety check → tick-based placement).

## ⚠️ Active Issues

| Issue | Priority | Notes |
| ------- | :------: | ------- |
| Weight synced only once on mount | Low | No resync on reconnect |
| No MassData API on client | Low | Mass must come from server |
| Multiplayer camera untested | Low | Orbital camera not tested with >1 player |
| SPRING_STIFFNESS_PER_NM=400 may be soft for heavy vehicles | Medium | Heavy vehicles may bottom out |
| Tire types not differentiated | Medium | 4 tire types share same physics properties |
| Player hiding incomplete | Medium | Some scenarios where mounted player is still visible |
| Engine rpm thread safety | Low | Dual-read/write without sync (no observed issues) |
| PhysicsAssembler no affiliation registration | Low | Assembled SubLevel not registered in AffiliationRegistry → self-damage not excluded |
| PhysicsAssembler no player feedback | Low | No chat message on assemble/disassemble success/failure |
| PhysicsAssembler multiplayer untested | Medium | BFS + PD servo alignment behavior under >1 player not verified |

## ✅ Recently Resolved

| Issue | Fix |
| ------- | --- |
| Bullet trail render crash (Veil NPE) | Switched to AFTER_LEVEL + RenderType.LINES |
| Entity hit position at feet | ProjectileUtil.getEntityHitResult() for precise intersection |
| Turret disassembly on reconnect | Deferred constraint rebuild in onLoad/tick |
| Other players visible on vehicle | setInvisible + RenderPlayerEvent.Pre + RenderNameTagEvent |
| Sound not heard by other players | Server-side level.playSound() broadcast |
| Adaptive camera height jitter | Use renderPos.y() instead of bbox.maxY() |
| Disassembly flicker re-assemble | Player cooldown (PLAYER_COOLDOWNS, 20 ticks) |
| Right Alt key Ctrl detection | Check both LEFT_ALT and RIGHT_ALT |
| Gear shift Q/E not working | Fixed rising edge detection in ClientEvents |

## 📋 Roadmap

### High Priority

| Feature | Description |
| --------- | ------------- |
| **Resource Pack Import System** | Community weapon FX / tire models / sounds |
| **Part Damage Integration** | Connect block damage to Sable constraint auto-split |

### Medium Priority

| Feature | Description |
| --------- | ------------- |
| Tire Squeal Particles | Smoke/skid marks when friction saturates |
| Smart Transmission | Auto-adjust gear ratios based on load |
| Vehicle Status HUD | Polished HUD replacing debug overlay |
| Tire Type Registry | Different physics for off-road / racing / heavy tires |

### Low Priority

| Feature | Description |
| --------- | ------------- |
| Cockpit Break Protection | Indestructible while occupied |
| In-Game Yaw Calibration | GUI slider instead of config file edit |
| Parameter Tuning | Compile-time constants → data-driven config |

## Paused

| Item | Reason | Resume Condition |
| ---- | ------ | ---------------- |
| SubLevel Physical Scaling | Sable Rust-side scaling incomplete | Upstream `Pose3d.scale` → Rapier native support |
| Veil Render Engine | Custom shader NPE (shader=null) | Needs shader layer fix |

## Design Direction

**Blocky car route** — Not waiting for Sable scaling. Build playable vehicles at Minecraft block scale.

**Asset strategy** — Don't make official FX/sound/texture packs. Build an import framework and let the community contribute.

See `《中控载具工坊：范式》管理文档3.0/5-技术参考/5.4-设计哲学与路线图/` (Chinese) for the full design philosophy.
