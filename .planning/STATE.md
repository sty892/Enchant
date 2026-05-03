# Guardian Mod Foundation State

## Completed in this pass

- Project identity aligned to `guardian_mod` with package root `me.guardian`.
- Common, client, and server Fabric metadata use separate mod ids.
- Build uses Minecraft 1.21.11, Fabric Loader 0.16.9, Fabric API 0.141.1+1.21.11, GeckoLib 5.4.5, Java 21, official Mojang mappings, and Fabric Loom 1.15.4.
- Skeleton common class, client class, and server class registered in entrypoints.
- Config Loader ready to read simple JSON structures from runtime directory without relying on complex external config libraries.
- Gradle upgraded to 9.2.1, Loom upgraded to 1.15.4, restoring `modImplementation` for Fabric API and GeckoLib, resolving previous mappings issues.
- Module 2: Client/Server Handshake implemented using Fabric Networking API. Mod presence detection and 5-second timeout logic added to client.
- Module 5: 8 Keys (`key_1` to `key_8`) and 3 Fragments (`fragment_overworld`, `fragment_nether`, `fragment_generic`) implemented and added to a custom creative tab.
- Module 3: 5 Altar blocks (`altar_core`, `altar_speed`, `altar_protection`, `altar_damage`, `altar_recovery`) registered with `AltarBlockEntity` foundation for fragment storage.
- Module 4: 8 Keyhole blocks (`keyhole_1` to `keyhole_8`) registered for boss arena unlocking.

## Architectural Mandates
- Do NOT merge client and server source sets; keep `common`, `client`, `server` completely isolated.
- Avoid introducing transitive API libraries (like MidnightLib, AutoConfig) unless specifically allowed. Use simple POJO JSON parsing with GSON.
- Never strip GeckoLib dependencies in `fabric.mod.json`; they are required runtime dependencies.
- Add a real server-side boss hitbox entity that is visible through vanilla debug hitbox rendering.
- Keep model, texture, and animation delivery server-authoritative; client jar should only contain a fallback if required.
