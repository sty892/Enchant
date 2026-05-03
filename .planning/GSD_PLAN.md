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
- **Status:** TODO

## Module 4 — Keyholes
- **Status:** TODO

## Module 6 — Diamond restriction system
- **Status:** TODO

## Module 7 — JSON-driven boss event system
- **Status:** TODO

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
