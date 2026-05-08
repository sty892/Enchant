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
    "summon lightning_bolt ~ ~ ~"
  ]
}
```

Commands may be written with or without a leading `/`. They run with permission level 4 from the command source position. If the server console runs a script, commands run from the overworld spawn position.

Run a script:

```mcfunction
/guardian event run season_start
```

Script ids tab-complete from files in `config/guardian_mod/scripts/*.json`.

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
