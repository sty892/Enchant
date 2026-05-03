# Continue Notes

Gemini CLI is stopped. Codex performed a repair pass after audit.

## Latest Commits

- `4d91e6a fix(build): stabilize current gemini worktree`
- `101aeaf fix(blocks): align altar and keyhole foundations with spec`
- `01f7119 fix(server): align diamond restriction with world state`
- `72cb7f5 fix(events): add json driven guardian event executor`
- `0300ac4 fix(server): send diamond restriction title notice`
- `21170c9 fix(config): align runtime defaults with spec json`
- `9c2c8b6 feat(boss): add overworld guardian`
- `82c335d feat(boss): add nether guardian`
- `31fb360 feat(boss): add generic guardian boss`
- `b068f96 feat(altar): implement guardian ritual upgrades`
- `bb8fb8b feat(structures): place guardian templates from data pack`
- `ec494d4 feat(client): add boss resource pack fallback`

## Current Status

- `./gradlew :common:compileJava --stacktrace` passes.
- `./gradlew build --stacktrace` passes.
- Module 3 block foundation is aligned with SPEC; full altar ritual remains Module 11.
- Module 4 is aligned with SPEC: one staged `guardian_mod:keyhole`, `keys_config.json`, item consumption, and shared event executor callbacks.
- Module 6 is aligned with SPEC foundation: world-state unlock, UUID whitelist, ore break cancel, title/actionbar warning, and 20-tick inventory scan. The old `fragment_generic` unlock was removed.
- Module 7 executor is present and JSON-driven. `spawn_structure` now calls a real StructureTemplate spawner. `play_animation` remains a placeholder/hook by SPEC.
- Modules 8-10 boss server entities are implemented and pushed.
- Module 11 altar ritual server logic is implemented and pushed. Recovery exhaustion reduction is implemented; saturation multiplier remains a narrow follow-up.
- Module 12 valid placeholder NBT structures and StructureTemplate placement are implemented and pushed.
- Module 13 server-resourcepack placeholders and client fallback assets are implemented and pushed. Client jar contains only fallback boss assets.
- Module 14 commands are implemented in the current working tree and need final verification/commit/push.

## Next Module

Final Module 14 verification, commit, and push.
