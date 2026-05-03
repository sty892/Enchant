# Guardian Mod — Project Specification
## Fabric 1.21.11 | Client + Server | GeckoLib

---

## PROJECT GOAL

Create a split Fabric mod for Minecraft 1.21.11:
- **client jar** — UI, rendering, handshake only. No boss assets inside.
- **server jar** — all game logic, bosses, events, configs.

If a player joins a server/world WITHOUT the server mod installed, the client mod silently disables itself. This prevents spoilers.

---

## TECH STACK

- Minecraft 1.21.11 (Java Edition, "Mounts of Mayhem" drop, released December 9 2025)
- Fabric Loader + Fabric API (latest for 1.21.11)
- GeckoLib (latest for Fabric 1.21.11) — for boss animations
- Gradle multiproject: modules `common/`, `client/`, `server/`
- Mod ID: `guardian_mod`

---

## ARCHITECTURE RULES (non-negotiable)

1. Boss models, textures, animations → ONLY in `server-resourcepack/`. NEVER in client jar.
2. Client jar contains a fallback "error" GeckoLib model (a cube with text "ERROR: Load resource pack").
3. All game-tunable values (damage thresholds, coordinates, timings, structure names, max upgrade levels) → in JSON config files in `configs/` directory next to server.jar. ZERO hardcoded tunable values.
4. Structures (altar, arenas) → `.nbt` files in `data/guardian_mod/structures/`. Swappable by saving a new Structure Block with the same name.
5. Boss death actions (world border expansion, structure spawn, fragment give, flag set) → driven entirely by `configs/boss_*.json`. No hardcoded boss death logic.

---

## MODULE 1: PROJECT SETUP

### Gradle multiproject
```
project-root/
├── build.gradle          (root, shared deps)
├── settings.gradle       (includes: common, client, server)
├── common/               (shared registrations, ModState, packets)
├── client/               (client entrypoint, rendering, handshake client side)
├── server/               (server entrypoint, all game logic)
├── configs/              (JSON configs — loaded at runtime, NOT baked into jar)
│   ├── boss_overworld.json
│   ├── boss_nether.json
│   ├── boss_generic.json
│   ├── altar_config.json
│   ├── keys_config.json
│   └── guardian_config.json
└── server-resourcepack/  (boss assets — distributed separately from jar)
    └── assets/guardian_mod/
        ├── models/entity/
        ├── textures/entity/
        └── animations/
```

Each module produces its own jar. Client jar excludes `assets/guardian_mod/models/entity/boss*` and `assets/guardian_mod/textures/entity/boss*` and `assets/guardian_mod/animations/`.

---

## MODULE 2: CLIENT/SERVER HANDSHAKE

### Packet flow
- On join: client sends `guardian_mod:handshake` custom payload
- Server mod responds: `guardian_mod:handshake_ok`
- If no response within 5 seconds: `ModState.serverModPresent = false`
- In singleplayer: always `true` (integrated server has server mod)

### ModState (in common module)
```java
public class ModState {
    public static boolean serverModPresent = false;
    public static boolean resourcePackLoaded = false;
}
```

All client-side features check `ModState.serverModPresent` before executing.

---

## MODULE 3: BLOCKS — ALTARS

### 5 blocks total
| Block ID | Purpose |
|---|---|
| `guardian_mod:altar_speed` | Applies movement speed buff |
| `guardian_mod:altar_protection` | Applies armor buff |
| `guardian_mod:altar_damage` | Applies attack damage buff |
| `guardian_mod:altar_recovery` | Applies hunger/saturation buff |
| `guardian_mod:altar_core` | Central activation block |

### Rules
- NO crafting recipe, NOT in creative menu (dev spawn egg only for testing)
- Spawned exclusively via structure after boss death
- Each altar block (speed/protection/damage/recovery) has a `BlockEntity` storing:
  - `UUID owner` — who inserted the fragment
  - `ItemStack fragment` — the inserted fragment item
  - `boolean isActive` — currently performing ritual

---

## MODULE 4: BLOCKS — KEYHOLES (8 stages)

### One block type with INT property STAGE (0–8)
- STAGE 0 = empty keyhole
- STAGE 1–8 = key N has been inserted (visual model changes)

### Behavior on right-click
- If STAGE == 0 and player holds matching key (per `keys_config.json`) → consume key, set STAGE
- If all 8 stages filled → fire `on_all_inserted` event from `keys_config.json`

### keys_config.json structure
```json
{
  "keys": [
    { "item_id": "guardian_mod:key_1", "keyhole_stage": 1, "on_insert": {} },
    { "item_id": "guardian_mod:key_2", "keyhole_stage": 2, "on_insert": {} },
    // ... up to key_8
  ],
  "on_all_inserted": {}
}
```
`on_insert` and `on_all_inserted` are event objects (same format as boss on_death events).

---

## MODULE 5: ITEMS

### Keys (8 items)
- IDs: `guardian_mod:key_1` through `guardian_mod:key_8`
- Textures in CLIENT jar at `assets/guardian_mod/textures/item/key_N.png`
- Stack size: 1
- Added to creative tab "Guardian Mod" for testing
- Found in world chests (placed manually by server admin — no loot table needed)

### Boss Fragments (3 items)
- `guardian_mod:fragment_overworld` — from Overworld Guardian
- `guardian_mod:fragment_nether` — from Nether Guardian
- `guardian_mod:fragment_generic` — from any generic boss
- Textures in CLIENT jar
- Stack size: 1

---

## MODULE 6: DIAMOND RESTRICTION SYSTEM

### Guardian World State (PersistentState)
Saved to world data:
```java
public class GuardianWorldState extends PersistentState {
    public boolean overworldBossDefeated = false;
    public boolean netherBossDefeated = false;
}
```

### guardian_config.json
```json
{
  "diamond_restriction_enabled": true,
  "op_diamond_whitelist": []
}
```
`op_diamond_whitelist` = list of player UUIDs who can always get diamonds.

### Restriction logic (server-side)
1. **BlockBreakEvent**: if `overworldBossDefeated == false` and block is `diamond_ore` or `deepslate_diamond_ore`:
   - Cancel break
   - Send title + actionbar: `"Нельзя получить алмазы пока не убит Хранитель Верхнего Мира"`
   - Skip if player UUID in whitelist
2. **Every 20 server ticks**: scan all online players' inventories
   - If `overworldBossDefeated == false` and player has any `diamond` or `diamond_*` items → remove them, send actionbar message
   - Skip whitelist players

### Commands
- `/guardian whitelist add <player>` — adds UUID to whitelist (OP only)
- `/guardian whitelist remove <player>` — removes UUID
- `/guardian whitelist list` — lists whitelist

---

## MODULE 7: BOSS EVENT SYSTEM (JSON-driven)

This is the core of configurability. All boss death consequences are in JSON.

### boss_overworld.json
```json
{
  "boss_id": "guardian_mod:boss_overworld",
  "on_spawn": {
    "play_animation": "spawn",
    "world_border_expand": { "to": 200, "duration_seconds": 30 }
  },
  "on_death": {
    "world_border_expand": { "to": 500, "duration_seconds": 60 },
    "spawn_structure": "guardian_mod:altar",
    "spawn_structure_offset": { "x": 0, "y": 0, "z": 0 },
    "give_fragment": "guardian_mod:fragment_overworld",
    "set_flag": "overworldBossDefeated",
    "broadcast_title": "Хранитель Верхнего Мира повержен!",
    "allow_diamonds": true
  }
}
```

### boss_nether.json
```json
{
  "boss_id": "guardian_mod:boss_nether",
  "on_spawn": {
    "play_animation": "spawn",
    "world_border_expand": { "to": 800, "duration_seconds": 30 }
  },
  "on_death": {
    "world_border_expand": { "to": 2000, "duration_seconds": 120 },
    "spawn_structure": "guardian_mod:altar_nether",
    "spawn_structure_offset": { "x": 0, "y": 0, "z": 0 },
    "give_fragment": "guardian_mod:fragment_nether",
    "set_flag": "netherBossDefeated",
    "broadcast_title": "Хранитель Нижнего Мира повержен!"
  }
}
```

### boss_generic.json
```json
{
  "boss_id": "guardian_mod:boss_generic",
  "on_death": {
    "give_fragment": "guardian_mod:fragment_generic"
  }
}
```

### BossEventSystem class
Reads the JSON and executes:
- `spawn_structure` → calls `StructureSpawner.place(world, bossDeathPos, structureId)`
- `world_border_expand` → smooth WorldBorder expansion over `duration_seconds`
- `give_fragment` → gives item to all players in `damageContributors` map
- `set_flag` → updates `GuardianWorldState`
- `allow_diamonds` → sets `overworldBossDefeated = true`
- `broadcast_title` → sends title packet to all online players
- `play_animation` → sends animation packet to all clients near boss

All bosses have: `Map<UUID, Float> damageContributors` populated on every damage event.

---

## MODULE 8: BOSS AI TYPE 1 — Overworld Guardian

Entity ID: `guardian_mod:boss_overworld`  
Extends: `HostileEntity` + `GeoEntity` (GeckoLib)  
Model/texture/animation: in SERVER RESOURCEPACK only.  
Fallback model in client jar: cube geo model with "ERROR" texture.

### Stats (all configurable via attributes, set in entity default attributes)
- MaxHealth: 500
- AttackDamage: 15  
- MovementSpeed: 0.25
- FollowRange: 64
- KnockbackResistance: 0.8

### AI Goals (in order)
1. `SwimGoal`
2. `LookAtEntityGoal(PlayerEntity.class)`
3. `WanderAroundFarGoal`
4. `MeleeAttackGoal`
5. Custom `PhaseGoal` — at 75% HP: speed boost + summon 3× `boss_generic`
6. Custom `PhaseGoal` — at 50% HP: new attack (leap at player)
7. Custom `PhaseGoal` — at 25% HP: berserk (speed ×2, damage ×1.5)
8. Custom `ProjectileAttackGoal` — throws fireball every 5 seconds

### On death
Calls `BossEventSystem.triggerOnDeath(this, world)` which reads `boss_overworld.json`.

### GeckoLib animations
Controller name: `"main_controller"`  
Animation states: `idle`, `walk`, `attack`, `spawn`, `death`, `leap`, `berserk`

---

## MODULE 9: BOSS AI TYPE 2 — Nether Guardian

Entity ID: `guardian_mod:boss_nether`  
Extends: `HostileEntity` + `GeoEntity`  
FireImmune: true

### Stats
- MaxHealth: 750
- AttackDamage: 20
- MovementSpeed: 0.3
- FollowRange: 80
- KnockbackResistance: 1.0

### AI Goals (in order)
1. `SwimGoal`
2. `LookAtEntityGoal(PlayerEntity.class)`
3. `MeleeAttackGoal` — melee + sets target on fire for 5s
4. Custom `FireballBarrageGoal` — fires 5 fireballs in rapid sequence, 8s cooldown
5. Custom `TeleportBehindGoal` — teleports behind target if pathfinding fails for 3s
6. Custom `PhaseGoal` — at 50% HP: 3s invisibility + 5 fire pillars around player
7. Custom `PhaseGoal` — at 25% HP: all nearby entities get `Wither II` for 10s
8. Custom `SummonMinionGoal` — every 30s summons 2× `boss_generic` with `"variant": "nether_minion"`

### On death
`BossEventSystem.triggerOnDeath(this, world)` reading `boss_nether.json`.

---

## MODULE 10: BOSS AI TYPE 3 — Generic Boss (multi-variant)

Entity ID: `guardian_mod:boss_generic`  
Extends: `HostileEntity` + `GeoEntity`  
Has NBT tag `"variant"` (String) → determines model/texture path in resourcepack.

### Stats
- MaxHealth: 300
- AttackDamage: 12
- MovementSpeed: 0.28

### AI Goals (in order)
1. `SwimGoal`
2. `LookAtEntityGoal(PlayerEntity.class)`
3. `MeleeAttackGoal`
4. Custom `ChargeAttackGoal` — dashes at player, knockback, 10s cooldown
5. Custom `GroupBuffGoal` — if 3+ `boss_generic` nearby → all get Speed I + Strength I
6. Custom `PhaseGoal` — at 33% HP: summon 2× `boss_generic` same variant

### Fragment drop
Only gives `fragment_generic` if NBT `"isSummoned": false`.

### On death
`BossEventSystem.triggerOnDeath(this, world)` reading `boss_generic.json`.

---

## MODULE 11: ALTAR LOGIC

### altar_config.json
```json
{
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

### Fragment insertion (right-click altar block with fragment in hand)
- If `BlockEntity.fragment` is empty → store fragment + store player UUID as owner
- If fragment already present AND player == owner → return fragment to player (right-click empty hand)
- If fragment present AND player != owner → show chat message "Алтарь занят другим игроком"

### Distance check (server tick, every tick for active altars)
- If player (owner) moves more than **5 blocks** from the altar block → drop `ItemEntity` at altar position, clear `BlockEntity.fragment` and `owner`

### Ritual activation (right-click altar_core while standing on it)
Conditions:
- Player holds a `fragment_*` item
- Player's feet are on `altar_core` block
- At least one altar block (speed/protection/damage/recovery) within 5 blocks has a fragment with matching owner

Process:
1. Mark ritual as `isActive = true` on all matched altar blocks
2. Every server tick: spawn particle beam from each active altar block toward player  
   (use `CRIT` and `ENCHANT` particle types)
3. If player moves more than **5 blocks** from `altar_core` during ritual → cancel, fragments stay in altar blocks
4. After **5 seconds** (100 ticks): for each active altar block:
   - Read `altar_config.json` + `GuardianWorldState` to determine current stage (1 or 2)
   - Read player's current attribute level for that altar type (from persistent player NBT)
   - If current level < max for stage → apply attribute modifier + consume fragment + increment player NBT level
   - If current level >= max → show actionbar "Достигнут максимум для текущей стадии"

### Attribute application (permanent, stacking, stored in player NBT)
| Altar | Attribute | Per Level |
|---|---|---|
| Speed | `MOVEMENT_SPEED` | +0.02 |
| Protection | `ARMOR` | +1 |
| Damage | `ATTACK_DAMAGE` | +1 |
| Recovery | Custom (player NBT `"guardian_recovery_level"`) | +1 level |

Recovery implementation via Mixin:
- `PlayerEntityMixin` — intercept `addExhaustion()`: multiply exhaustion by `(1 - 0.1 * recovery_level)` (min 0)
- `FoodComponentMixin` or `PlayerEntityMixin.eat()` — multiply saturation gain by `(1 + 0.1 * recovery_level)`

Attribute modifiers use a **deterministic UUID per attribute + slot** stored in player persistent NBT so they survive relog.

---

## MODULE 12: STRUCTURES

### Files
- `data/guardian_mod/structures/altar.nbt` — placeholder (5 blocks: 4 altar + core in cross pattern)
- `data/guardian_mod/structures/boss_overworld_arena.nbt` — placeholder arena
- `data/guardian_mod/structures/boss_nether_arena.nbt` — placeholder arena

### StructureSpawner
```java
StructureSpawner.place(ServerWorld world, BlockPos center, String structureId)
// structureId example: "guardian_mod:altar"
// reads from data/guardian_mod/structures/altar.nbt via StructureTemplate API
```

Structure name in `boss_overworld.json` → `spawn_structure: "guardian_mod:altar"` → maps to `altar.nbt`.  
Change the JSON → different structure spawns. No code change needed.

Structures can be re-saved via Structure Block with matching namespace:name → new `.nbt` is used immediately.

---

## MODULE 13: SERVER RESOURCEPACK + FALLBACK

### server-resourcepack/ layout
```
server-resourcepack/
├── pack.mcmeta              (format version for 1.21.11)
└── assets/guardian_mod/
    ├── models/entity/
    │   ├── boss_overworld.geo.json       (GeckoLib geo, placeholder cube)
    │   ├── boss_nether.geo.json
    │   └── boss_generic/
    │       └── variant_default.geo.json
    ├── textures/entity/
    │   ├── boss_overworld.png            (placeholder 64x64 white)
    │   ├── boss_nether.png
    │   └── boss_generic_default.png
    └── animations/
        ├── boss_overworld.animation.json (GeckoLib placeholder)
        ├── boss_nether.animation.json
        └── boss_generic.animation.json
```

### Fallback model in CLIENT jar
- `assets/guardian_mod/models/entity/boss_fallback.geo.json` — GeckoLib cube
- `assets/guardian_mod/textures/entity/boss_fallback.png` — red texture with "ERROR: Load resource pack"

### Detection logic (client-side)
```java
// On ResourcePackLoadEvent or join:
boolean hasAssets = Minecraft.getInstance().getResourceManager()
    .getResource(new ResourceLocation("guardian_mod", "textures/entity/boss_overworld.png"))
    .isPresent();
ModState.resourcePackLoaded = hasAssets;
// All GeoEntity.getModelResource() check ModState.resourcePackLoaded
// If false → return FALLBACK model path
```

---

## MODULE 14: COMMANDS + FINALIZATION

### /guardian command tree (OP level 2+)
```
/guardian boss spawn <boss_id>        — spawn boss at player position
/guardian boss kill all               — kill all guardian bosses (triggers full death cycle)
/guardian whitelist add <player>
/guardian whitelist remove <player>
/guardian whitelist list
/guardian state                       — print GuardianWorldState
/guardian reset                       — reset GuardianWorldState (for testing)
/guardian stage <1|2>                 — force-set world stage
/guardian reload                      — reload all JSON configs from disk
```

### Config loading
All configs read from `FabricLoader.getInstance().getConfigDir().resolve("guardian_mod/")`.  
This is next to the server jar, NOT baked into the jar.

### fabric.mod.json
- client jar: `"environment": "client"`
- server jar: `"environment": "server"`
- Both have correct `entrypoints`

### Build verification
`./gradlew build` must succeed after each module. Each module is a checkpoint.

---

## EXECUTION ORDER
```
Module 1 → Module 2 → Module 5 → Module 3 → Module 4
→ Module 6 → Module 7 → Module 8 → Module 9 → Module 10
→ Module 11 → Module 12 → Module 13 → Module 14
```
