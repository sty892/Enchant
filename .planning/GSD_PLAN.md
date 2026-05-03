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
- **Repair commits:** `01f7119 fix(server): align diamond restriction with world state`, `0300ac4 fix(server): send diamond restriction title notice`
- **Status:** DONE

## Module 7 - JSON-driven boss event system
- **Goal:** Implement reusable JSON event executor for boss and keyhole events.
- **Acceptance criteria:** Executor supports `world_border_expand`, `spawn_structure`, `spawn_structure_offset`, `give_fragment`, `set_flag`, `allow_diamonds`, `broadcast_title`, and `play_animation` placeholder/hook. Boss manager exposes on-spawn/on-death trigger methods. Keyholes reuse the same executor bridge.
- **Test commands:** `./gradlew :common:compileJava --stacktrace`, `./gradlew build --stacktrace`
- **Repair commit:** `72cb7f5 fix(events): add json driven guardian event executor`
- **Status:** FOUNDATION. Structure placement is a non-crashing placeholder until Module 12; animation is a placeholder/hook as required.

## Module 8 - Overworld Guardian
- **Goal:** Implement the Overworld Guardian server entity.
- **Acceptance criteria:** Entity id `guardian_mod:boss_overworld`; real server hitbox; accepts damage; tracks `Map<UUID, Float>` damage contributors; attributes 500/15/0.25/64/0.8; basic goals; phase hooks at 75/50/25; projectile hook; JSON-driven spawn/death events; no real assets in client jar.
- **Test commands:** `./gradlew :common:compileJava --stacktrace`, `./gradlew build --stacktrace`
- **Commit:** `9c2c8b6 feat(boss): add overworld guardian`
- **Status:** DONE

## Module 9 - Nether Guardian
- **Goal:** Implement the Nether Guardian server entity.
- **Acceptance criteria:** Entity id `guardian_mod:boss_nether`; fire immune; accepts damage; tracks damage contributors; attributes 750/20/0.3/80/1.0; melee fire, phase hooks, fireball/minion hooks; JSON-driven spawn/death events.
- **Test commands:** `./gradlew :common:compileJava --stacktrace`, `./gradlew build --stacktrace`
- **Commit:** `82c335d feat(boss): add nether guardian`
- **Status:** DONE

## Module 10 - Generic Boss
- **Goal:** Implement multi-variant Generic Guardian boss.
- **Acceptance criteria:** Entity id `guardian_mod:boss_generic`; persistent `variant` and `isSummoned`; damage contributors; charge hook; group buff hook; 33% phase summon; summoned minions do not trigger fragment grant; non-summoned death uses JSON event system.
- **Test commands:** `./gradlew :common:compileJava --stacktrace`, `./gradlew build --stacktrace`
- **Commit:** `31fb360 feat(boss): add generic guardian boss`
- **Status:** DONE

## Module 11 - Altar ritual logic
- **Goal:** Implement altar fragment insertion and ritual upgrades.
- **Acceptance criteria:** Right-click altar stores/returns fragments with owner UUID; altar core starts ritual while player stands on it holding a fragment; matched owner altars within 5 blocks become active; 100-tick particle ritual; cancel on distance; `altar_config.json` and `GuardianWorldState.netherBossDefeated` choose stage caps; speed/protection/damage use permanent attribute modifiers; recovery level persists and reduces exhaustion by mixin.
- **Test commands:** `./gradlew :common:compileJava --stacktrace`, `./gradlew build --stacktrace`
- **Commit:** `b068f96 feat(altar): implement guardian ritual upgrades`
- **Status:** DONE for specified server behavior. Saturation-gain multiplier remains a narrow follow-up; exhaustion reduction is implemented.

## Module 12 - Structures
- **Goal:** Add swappable StructureTemplate placeholders and real spawner.
- **Acceptance criteria:** Valid `altar.nbt`, `boss_overworld_arena.nbt`, and `boss_nether_arena.nbt` under `data/guardian_mod/structures`; `StructureSpawner.place(ServerLevel, BlockPos, String)` loads `guardian_mod:name` via StructureTemplate API and centers placement.
- **Test commands:** `./gradlew :common:compileJava --stacktrace`, `./gradlew build --stacktrace`
- **Commit:** `bb8fb8b feat(structures): place guardian templates from data pack`
- **Status:** DONE

## Module 13 - Server resourcepack + fallback
- **Goal:** Keep real boss assets in server resourcepack and client fallback assets in client jar.
- **Acceptance criteria:** `server-resourcepack/` contains boss geo/texture/animation placeholders; client jar contains only `boss_fallback` model/texture/animation; client detects resourcepack by checking `textures/entity/boss_overworld.png`.
- **Test commands:** `./gradlew :common:compileJava --stacktrace`, `./gradlew build --stacktrace`, client jar contents check.
- **Commit:** `ec494d4 feat(client): add boss resource pack fallback`
- **Status:** DONE

## Module 14 - Commands + finalization
- **Goal:** Add `/guardian` OP command tree and final verification.
- **Acceptance criteria:** Commands for boss spawn/kill, whitelist add/remove/list, state, reset, stage, reload; final clean build; jars assemble; final report records residual TODOs.
- **Status:** IN PROGRESS in current working tree.
