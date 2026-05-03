# Guardian Mod Foundation State

## Completed in this pass

- Project identity aligned to `guardian_mod` with package root `me.guardian`.
- Common, client, and server Fabric metadata use separate mod ids.
- Build uses Minecraft 1.21.11, Fabric Loader 0.16.9, Fabric API 0.141.1+1.21.11, GeckoLib 5.4.5, Java 21, official Mojang mappings, and Fabric Loom 1.10-SNAPSHOT.
- Skeleton common class, client class, and server class registered in entrypoints.
- Config Loader ready to read simple JSON structures from runtime directory without relying on complex external config libraries.

## Build Blocker / Gradle Configuration Conflict
- Fabric API 0.141.1+1.21.11 is built with newer Loom metadata (Loom 1.15.4) than Loom 1.10-SNAPSHOT (resolves to 1.10.5) accepts as a `modImplementation` dependency.
- Setting Fabric API as `compileOnly` bypasses Loom metadata validation but fails to remap Fabric API to Mojang mappings (`class_8710` compilation errors when calling `PayloadTypeRegistry.playC2S().register`).
- Upgrading to `fabric-loom 1.15-SNAPSHOT` fails because it requires Gradle 9.2.0 (we are on 8.14.3).
- Switching to Yarn mappings fails because Yarn 1.21.11 uses an `unpick` version unsupported by Loom 1.10.5.
- Therefore, the project is currently in a blocked state for adding custom network payloads.

## Architectural Mandates
- Do NOT merge client and server source sets; keep `common`, `client`, `server` completely isolated.
- Avoid introducing transitive API libraries (like MidnightLib, AutoConfig) unless specifically allowed. Use simple POJO JSON parsing with GSON.
- Never strip GeckoLib dependencies in `fabric.mod.json`; they are required runtime dependencies, even if `compileOnly` is used to bypass Loom metadata conflicts locally.
- Add a real server-side boss hitbox entity that is visible through vanilla debug hitbox rendering.
- Keep model, texture, and animation delivery server-authoritative; client jar should only contain a fallback if required.
