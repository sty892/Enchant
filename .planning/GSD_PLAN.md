# GSD Plan - Guardian Mod

## Module 1 - Project setup verification
- **Goal:** Verify foundation corresponds to SPEC, remove old packages, ensure clean project structure.
- **Acceptance criteria:** `me.guardian` is the only package, mod IDs are correct, build passes.
- **Test commands:** `./gradlew :common:compileJava --stacktrace`, `./gradlew build --stacktrace`
- **Status:** DONE

## Module 2 - Client/server handshake
- **Goal:** Implement client-server handshake to disable client features on vanilla servers.
- **Acceptance criteria:** ModState fields added, payload packets sent/received, timeout logic works, singleplayer bypassed.
- **Test commands:** `./gradlew :common:compileJava --stacktrace`, `./gradlew build --stacktrace`
- **Status:** DONE

## Module 5 - Items
- **Goal:** Implement 8 keys and 3 boss fragments with stack size 1 and testing creative tab.
- **Acceptance criteria:** Items registered, creative tab functional, stack size 1.
- **Test commands:** `./gradlew :common:compileJava --stacktrace`, `./gradlew build --stacktrace`
- **Status:** DONE

## Module 3 - Altar blocks
- **Goal:** Implement 5 altar blocks and `AltarBlockEntity` fragment storage.
- **Acceptance criteria:** 4 upgrade altar blocks are block entity blocks; `altar_core` is a normal block for now; `AltarBlockEntity` stores `ownerUuid`, `fragment`, `isActive`, `ritualTicks` with 1.21.11 NBT persistence.
- **Test commands:** `./gradlew :common:compileJava --stacktrace`, `./gradlew build --stacktrace`
- **Repair commit:** `101aeaf fix(blocks): align altar and keyhole foundations with spec`
- **Status:** DONE for Module 3 block foundation. Full ritual behavior remains Module 11 TODO.

## Module 4 - Keyholes
- **Goal:** Implement one staged keyhole block.
- **Acceptance criteria:** One block id `guardian_mod:keyhole`; `IntegerProperty STAGE` range 0..8; right-click reads `keys_config.json`, consumes sequential matching keys, updates stage, invokes shared event executor for `on_insert` and `on_all_inserted`.
- **Test commands:** `./gradlew :common:compileJava --stacktrace`, `./gradlew build --stacktrace`
- **Repair commit:** `101aeaf fix(blocks): align altar and keyhole foundations with spec`
- **Status:** DONE

## Module 6 - Diamond restriction system
- **Goal:** Restrict diamond acquisition until Overworld Guardian defeat.
- **Acceptance criteria:** `GuardianWorldState` stores `overworldBossDefeated` and `netherBossDefeated`; restriction unlocks only from world state; `guardian_config.json` controls enable flag and UUID whitelist; diamond ore/deepslate diamond ore break is canceled; inventories are scanned every 20 server ticks and diamond items are removed.
- **Test commands:** `./gradlew :common:compileJava --stacktrace`, `./gradlew build --stacktrace`
- **Repair commit:** `01f7119 fix(server): align diamond restriction with world state`
- **Status:** DONE

## Module 7 - JSON-driven boss event system
- **Goal:** Implement reusable JSON event executor for boss and keyhole events.
- **Acceptance criteria:** Executor supports `world_border_expand`, `spawn_structure`, `spawn_structure_offset`, `give_fragment`, `set_flag`, `allow_diamonds`, `broadcast_title`, and `play_animation` placeholder/hook. Boss manager exposes on-spawn/on-death trigger methods. Keyholes reuse the same executor bridge.
- **Test commands:** `./gradlew :common:compileJava --stacktrace`, `./gradlew build --stacktrace`
- **Repair commit:** `72cb7f5 fix(events): add json driven guardian event executor`
- **Status:** FOUNDATION. Structure placement is a non-crashing placeholder until Module 12; animation is a placeholder/hook as required.

## Module 8 - Overworld Guardian
- **Status:** TODO

## Module 9 - Nether Guardian
- **Status:** TODO

## Module 10 - Generic Boss
- **Status:** TODO

## Module 11 - Altar ritual logic
- **Status:** TODO

## Module 12 - Structures
- **Status:** TODO

## Module 13 - Server resourcepack + fallback
- **Status:** TODO

## Module 14 - Commands + finalization
- **Status:** TODO
