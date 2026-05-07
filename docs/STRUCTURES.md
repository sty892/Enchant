# Guardian Structures

Structure files live in:

```text
common/src/main/resources/data/guardian_mod/structures/
```

The file `altar.nbt` maps to the structure id `guardian_mod:altar`.

Use this in boss configs:

```json
"spawn_structure": "guardian_mod:altar"
```

During build, these files are packaged into the common jar.

To save a vanilla structure in-game:

1. Place a Structure Block.
2. Set mode to `Save`.
3. Set name to `guardian_mod:altar`.
4. Select the area.
5. Press `Save`.
6. Take `altar.nbt` from `world/generated/guardian_mod/structures/altar.nbt`, or from `world/generated/minecraft/structures` if Minecraft saved it there.
7. Put it at `common/src/main/resources/data/guardian_mod/structures/altar.nbt`.

After replacing an NBT file, rebuild the common jar.
