# Integration and Control : Paradigm (IAC-P)

> A **glue layer** that turns [Create Simulated](https://github.com/ryanhcode/sable) physical structures into drivable, fightable vehicles.
>
> Minecraft 1.21.1 + NeoForge.

[![License: LGPL-3.0](https://img.shields.io/badge/License-LGPL--3.0-blue.svg)](https://www.gnu.org/licenses/lgpl-3.0)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.230-orange)

---

> 🚀 **New? Start here → [Quick Start Guide](docs/en/00-Quick-Start.md)** — get driving in 3 minutes.

---

## What is IAC-P?

IAC-P is a **glue mod** — it adds vehicle controls, weapons, and HUD on top of the physics and mechanical blocks already provided by [Create Simulated](https://github.com/ryanhcode/sable).

The heavy lifting comes from our dependencies. What we do is **connect them into a playable vehicle experience**.

### Tech stack

| Layer | Handled by |
| ------- | ----------- |
| Physics engine (Rapier rigid-body simulation) | [Sable](https://github.com/ryanhcode/sable) |
| Physical blocks (wheels, suspensions, drills) | Create Offroad / Create Aeronautics |
| Mechanical power (RPM network, gearboxes) | [Create](https://github.com/Creators-of-Create/Create) |
| SubLevel hosting (in-world physics pockets) | [Sable](https://github.com/ryanhcode/sable) |
| **Vehicle controls, weapons, HUD — the glue** | **IAC-P (this mod)** |

### On top of existing physics blocks, we added…

- **Mount/dismount system**\* (F key, player hiding while seated, occupancy management)
- **Universal Cockpit** (two-block structure integrity check) + **CockpitLight** (lightweight 2×2 linear cockpit)
- **Orbital camera** with adaptive height/distance (tracks SubLevel pose via Sable API)
- **Keybinding config GUI** (C key, per-vehicle key remapping + tire pressure input)
- **Vehicle Orientation Screen** + **WASD Smart Mapping** (FACING-voting width axis, Car Mode, Reverse)
- **Structure Info Screen** (N key block statistics + weapon system overview)

    > \* Player mounting within SubLevels is handled by Simulated-Project. IAC-P provides the mount/dismount triggers, player hiding, occupancy management, and state synchronization.
    > \* Honey Glue and Physical Assembler are provided by **Simulated-Project**. IAC-P interacts with them for vehicle assembly and cockpit placement within physical structures.

### On top of Create RPM network and Offroad tire data components, we built our own…

> ⚠ The wheel items and tire data component (`TireLike` interface) are provided by Offroad. The physics simulation below — suspension springs, Binary Grip, tire pressure — is our own implementation built on top of Offroad's tire data.

- **Arcade-mode powertrain**: throttle-direct RPM + throttle-linear torque (decoupled from RPM), 5-speed + reverse transmission with 6-tick shift interruption
- **Binary Grip** traction model: per-wheel min(driveForce, μ×N), no shared friction budget (replaced friction circle 06-17)
- **Handbrake v3**: wheel lock + pure sliding friction (~0.35g)
- **Tire physics**: pressure sensitivity, carcass stiffness, rolling resistance, burst detection

### On top of Sable constraints and SubLevels, we added…

- **Turret system**: TurretBase block spawns a physical grindstone + lightning rod as two SubLevels, linked by a RotaryConstraint for yaw and a GenericConstraint for pitch. Position-mode PD servo aiming. Anchor point config via GUI. RGB axis line rendering.
- **Shotgun system**: independent weapon type with 8-pellet spread, 1s cooldown
- **Weapon system**: auto-aim with vehicle-local coordinate stabilization. Barrel-origin damage ray (independent of camera aim). Multi-turret support. Hold-to-fire, bypasses immunity frames. Bullet trail rendering. Part-damage system (5-hit block destruction).
- **Affiliation system**: O(1) runtime index for SubLevel ownership, group membership, player-vehicle bindings. Policy-based ray interaction resolution.

### On top of NeoForge, we added…

- **14 custom network packets** (C2S / S2C) for vehicle control, weapon fire, gear shift, smart mapping, anchor config
- **Debug overlay** (F3-style HUD: weight, RPM, torque, gear, speed, friction budget)
- **Debug Gear block** (small gear that prints RPM per tick for Create network diagnosis)
- **Full i18n** (Chinese + English, 3-level NeoForge config GUI, all screens and messages translatable)

### Design Philosophy

**IAC-P is bridgeware.** The physics engine already exists. The physical blocks already exist. What was missing was the **coordination** — a layer that says "when the player presses W, here's how the engine torque gets distributed to each wheel, accounting for gear ratio and the tire's current grip limit."

**Physics isn't just visual — it's playable.** Our springs *actually compress* — no animations, just real spring forces. When we say "50 km/h", that's `linearVelocity.length() × 3.6` computed for you.

**Components matter more than the whole.** Knock off a wheel and your vehicle handles differently, not because of a data value, but because the physical connection is gone.

---

## Quick Start

```bash
gradlew runClient       # Launch game client
gradlew runServer       # Launch dedicated server
gradlew compileJava     # Compile only
gradlew build           # Build mod JAR
```

### In-Game Controls

| Key | Action |
| :---: | -------- |
| F | Mount / Dismount |
| C | Vehicle orientation / keybinding config / Turret anchor config |
| Q / E | Shift up / Shift down |
| Left Mouse | Fire weapons (hold for auto-fire) |
| N | Structure info screen (near cockpit) / Toggle Debug Gear (near debug block) |
| V | Toggle stationary camera mode |
| Home / End | Increase / Decrease throttle |

---

## Dependencies

| Dependency | Version | Notes |
| ----------- | --------- | ------- |
| Minecraft | 1.21.1 | |
| NeoForge | 21.1.230 | |
| Create | 6.0.10-280 | Mechanical power network |
| Sable | 2.0.3 | Rapier physics engine |
| Simulated | 1.3.0 | Core physics framework (SubLevel, assembly) |
| Aeronautics | 1.3.0 | Propellers, balloons |
| Offroad | 1.3.0 | Wheels, drills, tire data components |

### First-Time Build

```bash
gradlew runClient
```

The first launch auto-extracts nested JAR dependencies. See [Getting Started Guide](docs/en/01-Getting-Started.md) for details.

All dependency versions are managed in `gradle.properties`.

---

## Documentation

| Document | Audience |
| ---------- | ---------- |
| [Feature Overview](docs/en/02-Feature-Overview.md) | Everyone |
| [Getting Started Guide](docs/en/01-Getting-Started.md) | New contributors |
| [SubLevel Physics Architecture](docs/en/03-SubLevel-Physics.md) | Developers |
| [Code Map](docs/en/04-Code-Map.md) | Contributors |
| [Troubleshooting](docs/en/05-Troubleshooting.md) | Everyone |
| [Current Status & Roadmap](docs/en/06-Current-Status.md) | Everyone |

> **Chinese documentation (comprehensive)**: `《中控载具工坊：范式》管理文档4.0/` — the single source of truth. Includes complete feature list with status tracking, subsystem deep-dives, architecture & code index, current status with active/resolved/frozen issues, troubleshooting index (50+ entries), performance analysis, design philosophy, full development history, and developer guides. `《中控载具工坊：范式》管理文档3.0/` is an older version kept for reference.

---

## License

**LGPL-3.0-only** — See [LICENSE](LICENSE) for details.

- ✅ Use in modpacks
- ✅ Modify and distribute (source must stay open)
- ✅ Use as a library
- ❌ No additional restrictions

---

## Contributing

Contributions welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) first.

Good ways to help: bug reports, feature suggestions, code PRs, documentation & translations.

---

## Acknowledgements

- **Create** — mechanical power network (RPM, gearboxes, stress system)
- **Sable (RyanHCode)** — Rapier physics engine, SubLevel system, constraint API
- **Simulated-Project** — SubLevel hosting, assembly framework
- **Offroad (Simulated-Project)** — wheel items, tire data components (`TireLike`)
- **Aeronautics (Simulated-Project)** — propeller, balloon, and levitite systems
- **NeoForged** — Minecraft modding framework
- **Copilot (DeepSeek-V4)** — Code implementation assistance
- Inspired by *Crossout* and *Besiege*

*Building vehicles, one constraint at a time.*
