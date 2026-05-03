# GSD Plan - Guardian Mod

## Module 1 — Project setup verification
- **Goal:** Verify foundation corresponds to SPEC, remove old packages, ensure clean project structure.
- **Files to change:** Delete any `me.sty892.enchant` packages.
- **Acceptance criteria:** `me.guardian` is the only package, mod IDs are correct, build passes.
- **Test commands:** `./gradlew :common:compileJava --stacktrace`, `./gradlew build --stacktrace`
- **Commit message:** `chore(gsd): verify foundation against specification`
- **Status:** DONE

## Module 2 — Client/server handshake
- **Goal:** Implement client-server handshake to disable client features on vanilla servers.
- **Files to change:** ModState, Network packets, GuardianModClient, GuardianModServer, GuardianMod.
- **Acceptance criteria:** ModState fields added, payload packets sent/received, timeout logic works, singleplayer bypassed.
- **Test commands:** `./gradlew :common:compileJava --stacktrace`, `./gradlew build --stacktrace`
- **Commit message:** `feat(network): add client server handshake`
- **Status:** DONE

## Module 5 — Items
- **Goal:** Implement 8 keys and 3 boss fragments with textures and creative tab.
- **Files to change:** ModItems, GuardianMod.
- **Acceptance criteria:** Items registered, creative tab functional, stack size 1.
- **Test commands:** `./gradlew :common:compileJava`, `./gradlew build`
- **Commit message:** `feat(item): add keys and fragments`
- **Status:** DONE

## Module 3 — Altar blocks
- **Goal:** Implement 5 altar blocks and their BlockEntity to store fragments.
- **Files to change:** ModBlocks, AltarBlock, AltarBlockEntity, GuardianMod.
- **Acceptance criteria:** Blocks registered, BlockEntity skeleton ready, common module compiles.
- **Test commands:** `./gradlew :common:compileJava`, `./gradlew build`
- **Commit message:** `feat(block): add altar blocks and blockentity foundation`
- **Status:** DONE

## Module 4 — Keyholes
- **Goal:** Implement 8 keyhole blocks to unlock boss arenas.
- **Files to change:** ModBlocks, KeyholeBlock, KeyholeBlockEntity.
- **Acceptance criteria:** Blocks registered, common module compiles.
- **Test commands:** `./gradlew :common:compileJava`, `./gradlew build`
- **Commit message:** `feat(block): add keyhole blocks foundation`
- **Status:** DONE

## Module 6 — Diamond restriction system
- **Goal:** Prevent diamond equipment usage without generic boss fragment.
- **Files to change:** DiamondRestrictionMixin (server), guardian_mod_server.mixins.json.
- **Acceptance criteria:** Mixin injected into LivingEntity.tick, diamond items ejected if fragment missing.
- **Test commands:** `./gradlew build`
- **Commit message:** `feat(mechanics): add diamond equipment restriction`
- **Status:** DONE

## Module 7 — JSON-driven boss event system
- **Goal:** Implement the logic for boss encounters driven by external configuration.
- **Files to change:** BossEventManager, GuardianModServer.
- **Acceptance criteria:** Configs loaded, manager initialized, system ready for boss entities.
- **Test commands:** `./gradlew build`
- **Commit message:** `feat(boss): add boss event system foundation`
- **Status:** DONE

## Module 8 — Overworld Guardian
- **Status:** TODO

## Module 9 — Nether Guardian
- **Status:** TODO

## Module 10 — Generic Boss
- **Status:** TODO

## Module 11 — Altar ritual logic
- **Status:** TODO

## Module 12 — Structures
- **Status:** TODO

## Module 13 — Server resourcepack + fallback
- **Status:** TODO

## Module 14 — Commands + finalization
- **Status:** TODO
