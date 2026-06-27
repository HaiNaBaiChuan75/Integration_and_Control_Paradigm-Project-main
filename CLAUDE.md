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
