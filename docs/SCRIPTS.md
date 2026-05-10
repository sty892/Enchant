# Guardian Scripts

Guardian runtime scripts live in:

```text
config/guardian_mod/scripts
```

Each script is a JSON file named `<script_id>.json`:

```json
{
  "commands": [
    "say Season start",
    "summon lightning_bolt ~ ~ ~",
    {
      "delay_ticks": 100,
      "command": "worldborder set 500 10"
    }
  ]
}
```

Commands may be written with or without a leading `/`. They run with permission level 4 from the command source position. If the server console runs a script, commands run in the overworld from the console command position.

Command objects support `delay_ticks` or `delay_seconds`. Delays are useful for scripts that spawn a boss and then run a follow-up command a few seconds later.

If a command itself contains unescaped quotes, for example:

```text
"/tellraw @a "привет""
```

the script loader has a lenient fallback parser for the `commands` array. Keep one command per line in that case. Valid JSON with escaped quotes is still preferred:

```json
"/tellraw @a \"привет\""
```

Run a script:

```mcfunction
/guardian event run season_start
```

Script ids tab-complete from files in `config/guardian_mod/scripts/*.json`.

If the same script id exists in both `config/guardian_mod/scripts/<id>.json` and
`config/guardian_mod/<id>.json`, the file in `scripts/` is used and the root
file is ignored with a log warning. Root-level command scripts are only kept as
a compatibility fallback when there is no matching file in `scripts/` and the
root file contains a `commands` array. Boss configs such as
`boss_overworld.json`, `boss_nether.json`, and `boss_generic.json` stay in the
root `config/guardian_mod` folder.

## Boss Scripts

Boss configs should prefer named scripts:

```json
{
  "boss_id": "guardian_mod:boss_overworld",
  "on_spawn_script": "boss_overworld_spawn",
  "on_death_script": "boss_overworld_death"
}
```

Inline commands are also supported for small tests:

```json
{
  "on_death": {
    "commands": [
      "say Boss died",
      "guardian structure place guardian_mod:altar"
    ]
  }
}
```

The Java boss event path no longer expands the world border, grants fragments, sets flags, broadcasts titles, or plays animations as hardcoded special actions. Put those behaviors in scripts as normal Minecraft or Guardian commands.

Default `season_start` spawns `guardian_mod:boss_overworld` at `0 70 0`, then runs `worldborder set 500 50s` after 80 ticks. The entity triggers the `spawn` GeckoLib animation key when it spawns. The provided resource pack must expose the Overworld boss model and animation through GeckoLib 5 paths:

```text
assets/guardian_mod/geckolib/models/entity/boss_overworld.geo.json
assets/guardian_mod/geckolib/animations/entity/boss_overworld.animation.json
assets/guardian_mod/textures/entity/boss_overworld.png
```

If a Blockbench export creates `assets/guardian_mod/models/entity/...` and `assets/guardian_mod/animations/...`, move those files into the `geckolib/` paths above for GeckoLib 5.

## Structures

Datapack or bundled mod structures go under the Minecraft structure folder:

```text
data/<namespace>/structure/<path>.nbt
```

For this project, bundled structures live under:

```text
server/src/main/resources/data/guardian_mod/structure
```

Bundled `.nbt` files are Minecraft structure templates. Vanilla placement uses `/place template`:

```mcfunction
/place template guardian_mod:altar
```

`/place structure` is for worldgen configured structures, not these `.nbt` template files.

Config-only structures live under:

```text
config/guardian_mod/structures/<namespace>/<path>.nbt
```

Example:

```text
config/guardian_mod/structures/guardian_mod/my_structure.nbt
```

Use the Guardian command for config structures or when you want config-first fallback behavior:

```mcfunction
/guardian structure place guardian_mod:my_structure
/guardian structure place guardian_mod:my_structure ~ ~ ~
```

`/guardian structure place` checks `config/guardian_mod/structures` first, then falls back to datapack or bundled mod structures.

## Local Test Server

For the local Fabric `1.21.11` test server at `D:\Servers\TestServer 1.21.11 Fabric`:

1. Build with `./gradlew clean build --stacktrace`.
2. Put the `guardian_mod-common`, `guardian_mod-server`, and dependencies in the server `mods` folder.
3. Start the server and confirm `/guardian event run season_start` is registered.
4. Run `/place template guardian_mod:altar`.
5. Run `/guardian structure place guardian_mod:altar`.
6. Spawn and kill a boss; confirm the configured `on_death_script` runs.
