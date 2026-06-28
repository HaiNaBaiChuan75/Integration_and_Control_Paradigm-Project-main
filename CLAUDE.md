# CLAUDE.md
(update 2026/6/20)
This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

IAC-P is a **Minecraft 1.21.1 NeoForge mod** — a "glue layer" that adds vehicle controls, weapons, and HUD on top of the [Sable](https://github.com/ryanhcode/sable) physics engine (Rapier rigid-body simulation) and [Create](https://github.com/Creators-of-Create/Create) mechanical power network. It does not provide its own physics; it **coordinates** existing systems into a playable vehicle experience.

## Build Commands

```bash
./gradlew runClient       # Launch game client
./gradlew runClient_alt   # Launch game client
./gradlew runServer       # Launch dedicated server
./gradlew compileJava     # Compile only
./gradlew build           # Build mod JAR
```

- Java 21 required
- First launch extracts nested JAR dependencies automatically
- IDE runs: "Client", "Client 2" (alt username), "Server", "Data Generation"

## Library Guidance

### JOML (`org.joml`)

Java OpenGL Math Library — 3D linear algebra (vectors, matrices, quaternions, poses). Sable exposes its physics data
through JOML types, so we use them for all 3D math.

**Core idea:** Prefer JOML over Minecraft's `Vec3` in tick-hot paths (physics, aiming logic). The real win is JOML's
`dest`-parameter overloads — they let you reuse a single instance across many calls, avoiding per-tick allocation.

```java
// Less ideal — allocates a new Vector3d every call
public Vector3dc getAbsPosition(SubLevel subLevel) {
    return subLevel.logicalPose().transformPosition(new Vector3d(...));
}

// Better — caller passes a reusable dest, zero allocation
public void getAbsPosition(SubLevel subLevel, Vector3d dest) {
    subLevel.logicalPose().transformPosition(x, y, z, dest);
}
```

**Constants:** Declare with `Vector3dc` (read-only interface) and use `.toImmutable()` so nothing can mutate them
through a back-door cast.

```java
public static final Vector3dc DEFAULT_CENTER = new Vector3d(0.5, 0.5, 0.5).toImmutable();
```

### fastutil (`it.unimi.dsi.fastutil`)

A collections library that avoids boxing overhead for primitive types and offers more compact data structures.

**Core idea:** Consider fastutil when the collection sits in a tick-frequency code path (`sable$tick`,
`sable$physicsTick`, `onSubLevelTick`). For primitive types (`int`, `long`, `double`) the benefit is largest — zero
boxing, less GC pressure. For reference types (`ObjectArrayList<T>`) the benefit is smaller; use it if you like the
consistency, but `ArrayList` is fine too.

**Common patterns:**

```java
// Primitive list — no boxing
IntArrayList values = new IntArrayList();
values.

add(42);           // no Integer.valueOf()

int v = values.getInt(0); // no .intValue()

// Primitive map — no Map.Entry objects on iteration
Long2ObjectOpenHashMap<BlockPos> map = new Long2ObjectOpenHashMap<>();
map.

put(12345L,pos);

BlockPos p = map.get(12345L);
```

**Bottom line:** If you're writing a hot loop, think about whether fastutil reduces allocations. If it's a one-time
setup (registry, config), `java.util.*` is perfectly fine.
