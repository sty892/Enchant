# Guardian Mod Final State

## Final Head

- `332efcbc4ff13f202a56452c72dbaddbea58ed8c`
- GitHub `main` includes `332efcb feat(commands): add guardian admin command tree`.

## Completed Modules

- Module 1 - Project setup: verified Gradle multiproject, `me.guardian` package root, current dependency foundation.
- Module 2 - Client/server handshake: client handshake and vanilla-server feature disable path.
- Module 3 - Altar blocks: four altar upgrade BlockEntity blocks, altar core block, owner/fragment/active/tick persistence.
- Module 4 - Keyholes: single `guardian_mod:keyhole`, `STAGE` 0..8, JSON key insertion, shared event executor callbacks.
- Module 5 - Items: 8 keys and 3 fragments registered with stack size 1 and test creative tab.
- Module 6 - Diamond restriction: `GuardianWorldState`, Overworld unlock flag, UUID whitelist, ore break cancel, 20-tick inventory scan.
- Module 7 - Boss event system: reusable JSON executor with world border, structure spawn, offsets, fragments, flags, diamond unlock, title, animation hook.
- Module 8 - Overworld Guardian: `guardian_mod:boss_overworld`, server hitbox/damage, contributors, attributes, goals, phase hooks, projectile hook, JSON events.
- Module 9 - Nether Guardian: `guardian_mod:boss_nether`, fire immune server boss, contributors, attributes, goals, fire/phase/minion hooks, JSON events.
- Module 10 - Generic Boss: `guardian_mod:boss_generic`, persistent variant/summoned state, contributors, charge/group/phase hooks, JSON death events.
- Module 11 - Altar ritual: fragment insert/return, core activation, active altar tracking, 100-tick particle ritual, config stage caps, persistent upgrade levels, modifiers, recovery exhaustion hook.
- Module 12 - Structures: valid placeholder `.nbt` templates and real `StructureTemplate` placement through `StructureSpawner.place`.
- Module 13 - Server resourcepack + fallback: server-resourcepack boss assets, client-only fallback assets, resource-pack detection, client jar asset separation.
- Module 14 - Commands + finalization: `/guardian` OP command tree for boss spawn/kill, whitelist add/remove/list, state, reset, stage, reload.

## Verification Commands

- `git status --short --branch` -> clean at start of final pass.
- `git log --oneline -20` -> confirmed `332efcb` is latest local/GitHub HEAD.
- `./gradlew clean build --stacktrace` -> passes.
- Client jar inspection -> no `boss_overworld`, `boss_nether`, or `boss_generic` assets in client jar; only fallback assets are present.

## Jar Outputs

- `common/build/libs/guardian_mod-common-1.0.0.jar`
- `client/build/libs/guardian_mod-client-1.0.0.jar`
- `server/build/libs/guardian_mod-server-1.0.0.jar`

## Client Jar Asset Check

Client jar contains:

- `assets/guardian_mod/animations/boss_fallback.animation.json`
- `assets/guardian_mod/models/entity/boss_fallback.geo.json`
- `assets/guardian_mod/textures/entity/boss_fallback.png`

Client jar does not contain real boss assets:

- `boss_overworld`
- `boss_nether`
- `boss_generic`

## Remaining TODOs

- Recovery saturation-gain multiplier from SPEC Module 11 is not implemented. Recovery exhaustion reduction is implemented through `PlayerRecoveryMixin`.
