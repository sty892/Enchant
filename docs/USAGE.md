# Guardian Mod Usage

## Running

Build jars:

```bash
./gradlew clean build --stacktrace
```

Use the generated jars from:

- `common/build/libs/guardian_mod-common-1.0.2.jar`
- `client/build/libs/guardian_mod-client-1.0.2.jar`
- `server/build/libs/guardian_mod-server-1.0.2.jar`

For a dedicated server, install Fabric Loader for Minecraft `1.21.11`, then put Fabric API, GeckoLib, `guardian_mod-common`, and `guardian_mod-server` in the server `mods` folder.

For a client, install Fabric Loader for Minecraft `1.21.11`, then put Fabric API, GeckoLib, `guardian_mod-common`, and `guardian_mod-client` in the client `mods` folder.

## First Version Test Checklist

Use this checklist before polishing mechanics further:

1. Start a Fabric client with `guardian_mod-common`, `guardian_mod-client`, Fabric API, and GeckoLib.
2. Start a Fabric server with `guardian_mod-common`, `guardian_mod-server`, Fabric API, and GeckoLib.
3. Join the server and confirm the client handshake logs enable Guardian Mod features.
4. Spawn each boss with `/guardian boss spawn boss_overworld`, `/guardian boss spawn boss_nether`, and `/guardian boss spawn boss_generic`.
5. Confirm each boss is visible. If real boss assets are missing or disabled, a fallback model and texture should render instead of an invisible entity.
6. Kill a boss and confirm the configured death script runs.
7. Test keyholes, altar flow, and diamond restriction with the commands below.

## Bosses

Spawn bosses with either short or namespaced ids:

```mcfunction
/guardian boss spawn boss_overworld
/guardian boss spawn guardian_mod:boss_overworld
/guardian boss spawn boss_nether
/guardian boss spawn boss_generic
```

Remove all spawned guardian bosses:

```mcfunction
/guardian boss kill all
```

Boss death behavior is configured in runtime files under:

```text
config/guardian_mod/boss_overworld.json
config/guardian_mod/boss_nether.json
config/guardian_mod/boss_generic.json
```

Each boss should point at named command scripts:

```json
{
  "boss_id": "guardian_mod:boss_overworld",
  "on_spawn_script": "boss_overworld_spawn",
  "on_death_script": "boss_overworld_death"
}
```

Scripts live in:

```text
config/guardian_mod/scripts
```

Run one manually:

```mcfunction
/guardian event run season_start
```

Script files contain a `commands` array:

```json
{
  "commands": [
    "say First command",
    "guardian structure place guardian_mod:altar",
    {
      "delay_ticks": 100,
      "command": "worldborder set 500 10"
    }
  ]
}
```

Commands may be written with or without `/`. They run from the boss/event position with permission level 4.
For commands containing quotes, prefer escaping them as `\"`; the script loader also has a lenient fallback for one command per line.

## Config Structures

The mod creates this folder automatically:

```text
config/guardian_mod/structures
```

To add a custom structure that can be referenced from boss configs, put a `.nbt` structure file here:

```text
config/guardian_mod/structures/guardian_mod/my_structure.nbt
```

Then reference it in a boss config:

```json
{
  "on_death": {
    "spawn_structure": "guardian_mod:my_structure"
  }
}
```

Bundled or datapack structures use Minecraft's current folder:

```text
data/guardian_mod/structure/altar.nbt
```

Bundled `.nbt` files are structure templates. Vanilla placement:

```mcfunction
/place template guardian_mod:altar
```

`/place structure` is for worldgen configured structures, not these `.nbt` template files.

Config structures are checked before datapack structures by the Guardian command. If `config/guardian_mod/structures/guardian_mod/my_structure.nbt` exists, it will be used. If not, the mod falls back to datapack structures from `data/guardian_mod/structure`.

```mcfunction
/guardian structure place guardian_mod:my_structure
/guardian structure place guardian_mod:my_structure ~ ~ ~
```

See `docs/SCRIPTS.md` for the full script and structure workflow.

## Keyholes

Place the eight independent keyholes:

- `guardian_mod:keyhole_1`
- `guardian_mod:keyhole_2`
- `guardian_mod:keyhole_3`
- `guardian_mod:keyhole_4`
- `guardian_mod:keyhole_5`
- `guardian_mod:keyhole_6`
- `guardian_mod:keyhole_7`
- `guardian_mod:keyhole_8`

Give matching keys:

```mcfunction
/give @s guardian_mod:key_1
/give @s guardian_mod:key_2
```

Each keyhole accepts only its matching key. For example, `key_5` does not insert into `keyhole_7`; `key_7` inserts into `keyhole_7`. A successful insert consumes one key, switches that keyhole to `filled=true`, and runs that key entry's `on_insert` from `keys_config.json`.

When all eight keyholes within the nearby group are filled, `on_all_inserted` from `keys_config.json` runs.

Useful test commands:

```mcfunction
/guardian keyholes state 16
/guardian keyholes reset 16
/guardian event test key_insert 1
/guardian event test all_keys
```

## Altar

Blocks:

- `altar_core` is the ritual center.
- `altar_speed` selects speed.
- `altar_protection` selects protection.
- `altar_damage` selects damage.
- `altar_recovery` selects recovery.

Basic flow:

1. Place `altar_core`.
2. Place one or more aspect blocks near it, within `altar_config.json` `radius` default `5`.
3. Hold a guardian fragment and right click an aspect block.
4. The actionbar should say `Выбрана стихия: Скорость/Защита/Урон/Восстановление`.
5. Stand on `altar_core`.
6. Wait for the ritual particles for `ritual_ticks`, default `100` ticks.
7. On success, the fragment is consumed and the upgraded stat is printed green in chat.

If no core is near the aspect block, the actionbar says `Рядом нет ядра алтаря`. If you leave the core or move out of range during the ritual, it cancels with `Ритуал прерван`.

Altar test commands:

```mcfunction
/guardian altar stats
/guardian altar reset
```

## Diamond Restriction

Before the Overworld Guardian is defeated, diamond items and diamond ore breaks are blocked unless the player's UUID is whitelisted in `guardian_config.json`.

Test flow:

```mcfunction
/guardian reset
/give @s minecraft:diamond
```

The diamond should be removed and the actionbar should show:

```text
Нельзя получить алмазы пока не убит Хранитель Верхнего Мира
```

Unlock diamonds for testing:

```mcfunction
/guardian stage 1
```

Stage `1` marks the Overworld Guardian defeated. Stage `2` also marks the Nether Guardian defeated, enabling stage 2 altar caps.
