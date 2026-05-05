# Guardian Configs And Events

Runtime configs are created under:

```text
config/guardian_mod
```

Files:

- `boss_overworld.json`
- `boss_nether.json`
- `boss_generic.json`
- `altar_config.json`
- `keys_config.json`
- `guardian_config.json`

Defaults are written only when a file is missing. If an old config already exists, edit it manually or delete it so the mod can recreate the latest default.

## keys_config.json

Each key entry maps one key item to one keyhole slot:

```json
{
  "keys": [
    {
      "item_id": "guardian_mod:key_1",
      "keyhole_id": "guardian_mod:keyhole_1",
      "slot": 1,
      "on_insert": {
        "broadcast_title": "Key 1 inserted"
      }
    }
  ],
  "on_all_inserted": {
    "world_border_expand": {
      "to": 500,
      "duration_seconds": 30
    }
  }
}
```

`keyhole_1` accepts `key_1`, `keyhole_2` accepts `key_2`, and so on through `keyhole_8`.

`on_insert` runs after the matching key is consumed and the keyhole becomes filled. `on_all_inserted` runs when all eight nearby keyhole slots are filled.

Manual event tests:

```mcfunction
/guardian event test key_insert 1
/guardian event test all_keys
```

## altar_config.json

```json
{
  "radius": 5,
  "ritual_ticks": 100,
  "stage_1": {
    "max_speed": 3,
    "max_protection": 3,
    "max_damage": 3,
    "max_recovery": 3
  },
  "stage_2": {
    "max_speed": 7,
    "max_protection": 7,
    "max_damage": 7,
    "max_recovery": 7
  },
  "stage_threshold_flag": "netherBossDefeated"
}
```

`radius` controls how far aspect blocks search for `altar_core`. `ritual_ticks` controls ritual length; `100` ticks is five seconds.

Stage 1 caps apply before the Nether Guardian is defeated. Stage 2 caps apply after `netherBossDefeated` is true. For testing:

```mcfunction
/guardian stage 1
/guardian stage 2
```

## Boss Events

Boss config events are JSON objects. Supported actions:

- `world_border_expand`
- `spawn_structure`
- `spawn_structure_offset`
- `give_fragment`
- `set_flag`
- `allow_diamonds`
- `broadcast_title`
- `play_animation`

Example Overworld death event:

```json
{
  "on_death": {
    "world_border_expand": {
      "to": 500,
      "duration_seconds": 60
    },
    "spawn_structure": "guardian_mod:altar",
    "spawn_structure_offset": {
      "x": 0,
      "y": 0,
      "z": 0
    },
    "give_fragment": "guardian_mod:fragment_overworld",
    "set_flag": "overworldBossDefeated",
    "allow_diamonds": true,
    "broadcast_title": "Overworld Guardian defeated",
    "play_animation": "death"
  }
}
```

Example all-keys event that spawns a structure:

```json
{
  "on_all_inserted": {
    "spawn_structure": "guardian_mod:altar",
    "spawn_structure_offset": {
      "x": 0,
      "y": 0,
      "z": 0
    },
    "broadcast_title": "All keys inserted"
  }
}
```

Example generic boss death fragment:

```json
{
  "on_death": {
    "give_fragment": "guardian_mod:fragment_generic"
  }
}
```

`play_animation` is currently a placeholder hook. It logs the requested animation and is ready for a future client packet/renderer integration.

