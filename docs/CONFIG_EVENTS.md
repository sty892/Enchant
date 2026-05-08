# Guardian Config Events

Guardian Mod now prefers named command scripts for runtime events.

Scripts live in:

```text
config/guardian_mod/scripts
```

Run a script manually:

```mcfunction
/guardian event run season_start
```

Boss configs use script fields:

```json
{
  "boss_id": "guardian_mod:boss_overworld",
  "on_spawn_script": "boss_overworld_spawn",
  "on_death_script": "boss_overworld_death"
}
```

Inline command objects are still supported for simple tests:

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

Older special JSON actions such as `world_border_expand`, `give_fragment`, `set_flag`, `allow_diamonds`, `broadcast_title`, and direct `play_animation` are not part of the primary boss event path. Use normal commands in scripts instead.

See `docs/SCRIPTS.md` for script format, structure placement, and local test server steps.
