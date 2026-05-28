# Технический план: Overworld Guardian — атаки, AI, модели

## Ограничения (обязательно)

- **Не трогать** Nether-босса, квесты, алтари, dimension triggers, лут-таблицы (если не нужны для тестовых предметов), общий рефакторинг мода.
- **Трогать только:** `OverworldGuardianEntity.java`, `OverworldGuardianAttackController.java`, сущности/блоки атак, клиентские рендереры, `OverworldGuardianAttackConfig.java`, `GuardianCommand.java` (tab-complete ID), минимальные fallback-ассеты в `client/src/main/resources`.

## Текущее состояние

| #   | ID (предлагаемый) | Статус   | Где код                                                                                  |
| --- | ----------------- | -------- | ---------------------------------------------------------------------------------------- |
| 1   | `right_hand_wave` | ~90%     | `ArmWaveAttack`                                                                          |
| 2   | `left_hand_wave`  | ~90%     | `ArmWaveAttack`                                                                          |
| 3   | `two_hand_wave`   | частично | `DoubleHandWaveAttack` + `CeilingFallingBlockEntity`                                     |
| 4   | `hands_slam_line` | ~70%     | `HandsSlamLineAttack`                                                                    |
| 5   | `stomp_players`   | ~60%     | `StompPlayersAttack`                                                                     |
| 6   | `charge_ram`      | **нет**  | —                                                                                        |
| 7   | `ground_vines`    | **нет**  | —                                                                                        |
| 8   | (часть #3)        | частично | 50% только P1; P2+ всегда 2 блока — **не по спеке**                                      |
| 9   | `statue_revival`  | ~50%     | `StatueRevivalAttack` + `TempleStatueEntity`                                             |
| 10  | `vine_pull`       | **нет**  | референс: `NetherGuardianAttackController.WhipGrabAttack`                                |
| 11  | `bomb_traps`      | ~80%     | `BombTrapsAttack` + `BombTrapEntity`                                                     |
| 12  | `arena_walls`     | **нет**  | не путать с `closeGates()`                                                               |
| 13  | `leap_attack`     | **нет**  | —                                                                                        |
| 14  | `healing_shield`  | ~40%     | `HealingShieldAttack` + `HealingShieldEntity`                                            |
| 15  | gates             | ~40%     | `OverworldGuardianEntity.closeGates()` — **закрываются на P3 step 3**, не при старте боя |

---

## Стратегия моделей и визуала

| Сущность                    | Fallback сейчас                          | Что сделать в моде (до resource pack)                                                                                                                                                           |
| --- | ---------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Босс                        | `boss_fallback`                          | без изменений                                                                                                                                                                                   |
| `BombTrapEntity`            | `BombTrapRenderer` → `TEMPLE_BOMB` block | оставить block-модель; улучшить частицы/масштаб                                                                                                                                                 |
| `TempleStatueEntity`        | `HuskRenderer`                           | `GeoEntityRenderer` + `geckolib/models/entity/temple_statue_fallback.geo.json` + текстура `temple_statue.png` |
| `HealingShieldEntity`       | `InvisibleRenderer`                      | `Display.ItemDisplay` или Geo-сфера ~2.5 блока; **обязателен hitbox**                                                                                                                           |
| `CeilingFallingBlockEntity` | vanilla `FallingBlockRenderer`           | ок                                                                                                                                                                                              |
| Новые: стена, лиана         | —                                        | `BlockDisplay` / `ItemDisplay` с `temple_gate.png` / vine item texture в JAR                                                                                                                    |
| Врата                       | `TEMPLE_GATE` block                      | проверить `blockstates`, `models/block`, `temple_gate.png`                                                                                                                                      |

---

## Фаза A — Исправить существующие 8 атак (приоритет 1)

### A1–A2: Удары руками (`right_hand_wave`, `left_hand_wave`)
**Спека:** ближний 10/9 только **перед** боссом и слегка справа/слева; волна 5 в радиусе ~5.5.
**Баг:** ближний урон по `distance(handPos) <= 1.5` без сектора «перед боссом».

### A3: Двуручный удар (`two_hand_wave`)
**Спека:** ближний сектор 4 блока, **14** урона; волна 5 блоков, **7** урона; не стакать — **max** из двух.

### A4: Удар по линии (`hands_slam_line`)
**Баги (CheckAttacks):** тайминги, направление.

### A5: Топот (`stomp_players`)
**Баг:** откидывание на `hit_tick`, волна кольцами на +2/+4/+6 — ощущение «не вовремя».

### A9: Статуи (`statue_revival`)
**Спека:** до 3 статуй **по краям храма**, 120 HP, зомби-AI, 90% DR босса, не респавн в фазе.

### A11: Бомбы (`bomb_traps`)
**Спека:** взрыв при **наступании**, без поломки блоков, урон < TNT (~6 ок), poison.

### A14: Щит (`healing_shield`)
**Баг (CheckAttacks):** «нельзя сломать» — `InvisibleRenderer` + `noPhysics` без AABB.

---

## Фаза B — Реализовать 7 отсутствующих атак (приоритет 2)

### B6: `charge_ram` (Таран)
- Самый дальний игрок в 25 блоках, 30 тиков windup, 20 блоков рывок, 14 dmg + KB, стан 10 сек.

### B7: `ground_vines` (Лианы под игроками)
- Выбрать 3 игроков, за 20 тиков частицы, удар 12 dmg + подброс, follow-up melee.

### B10: `vine_pull` (Лиана-притягивание)
- Притянуть игрока к боссу, сразу провести `two_hand_wave`.

### B12: `arena_walls` (Стены вокруг)
- При 2+ игроках в радиусе 5м. Кольцо из 8-12 сегментов (HP 150), таймеры 5/20/30/40 сек.

### B13: `leap_attack` (Прыжок)
- Подготовка 5 сек, прыжок на target, 19 dmg, KB.

### B15: Врата храма
- Закрывать при начале боя на уровне пола, открыть при смерти босса, wipe + cooldown 1 час.

---

## Acceptance / тест-план

| ID                                   | Критерий готовности                                   |
| ------------------------------------ | ----------------------------------------------------- |
| `right_hand_wave` / `left_hand_wave` | 10/9 спереди; 5 в волне; за спиной нет ближнего       |
| `two_hand_wave`                      | 14 в 4б, 7 в 5б, без двойного урона; KB заметный      |
| `hands_slam_line`                    | урон только на линии взгляда                          |
| `stomp_players`                      | KB на кольцах волны, не только в tick 0               |
| `charge_ram`                         | 1.5с прицел, 20б рывок, стан 10с, волна после         |
| `ground_vines`                       | 3 цели, 12 dmg + подброс, follow-up melee             |
| `vine_pull`                          | притягивание + instant `two_hand_wave`                |
| `bomb_traps`                         | шаг = взрыв, нет крушения блоков, poison              |
| `statue_revival`                     | 3 моба, DR 90%, блоки на краю                         |
| `arena_walls`                        | 2+ игрока, HP 150, таймеры 5/20/30/40                 |
| `leap_attack`                        | 5с windup, 19 dmg, KB                                 |
| `healing_shield`                     | видимый щит, 300 HP, босс не получает урон до слома   |
| gates                                | закрыты при агро; открыты при смерти; wipe + cooldown |
