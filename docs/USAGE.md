# Guardian Mod Usage

## Running

Build jars:

```bash
./gradlew clean build --stacktrace
```

Use the generated jars from:

- `common/build/libs/guardian_mod-common-1.0.0.jar`
- `client/build/libs/guardian_mod-client-1.0.0.jar`
- `server/build/libs/guardian_mod-server-1.0.0.jar`

For a dedicated server, install Fabric Loader for Minecraft `1.21.11`, then put Fabric API, GeckoLib, `guardian_mod-common`, and `guardian_mod-server` in the server `mods` folder.

For a client, install Fabric Loader for Minecraft `1.21.11`, then put Fabric API, GeckoLib, `guardian_mod-common`, and `guardian_mod-client` in the client `mods` folder.

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

