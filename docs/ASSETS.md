# Guardian Mod Assets

## Client Item And Block Assets

Client-side item textures:

```text
client/src/main/resources/assets/guardian_mod/textures/item
```

Client-side block textures:

```text
client/src/main/resources/assets/guardian_mod/textures/block
```

Classic model JSON files:

```text
client/src/main/resources/assets/guardian_mod/models/item
client/src/main/resources/assets/guardian_mod/models/block
```

Minecraft `1.21.11` item model definitions:

```text
client/src/main/resources/assets/guardian_mod/items
```

Blockstates:

```text
client/src/main/resources/assets/guardian_mod/blockstates
```

## Boss Assets

The client jar contains only fallback boss assets:

```text
client/src/main/resources/assets/guardian_mod/geckolib/models/entity/boss_fallback.geo.json
client/src/main/resources/assets/guardian_mod/geckolib/animations/entity/boss_fallback.animation.json
client/src/main/resources/assets/guardian_mod/textures/entity/boss_fallback.png
```

Real boss assets live outside the client jar in the server resource pack:

```text
server-resourcepack/assets/guardian_mod/geckolib/models/entity
server-resourcepack/assets/guardian_mod/geckolib/animations/entity
server-resourcepack/assets/guardian_mod/textures/entity
```

Do not place real `boss_overworld`, `boss_nether`, or `boss_generic` assets under `client/src/main/resources`.

## Replacing Textures

To replace an existing item or block texture, replace the `.png` with the same file name.

To add a new item texture, add:

1. `textures/item/<name>.png`
2. `models/item/<name>.json`
3. `items/<name>.json`
4. Java item registration, if the item does not already exist

To add a new block texture, add:

1. `textures/block/<name>.png`
2. `models/block/<name>.json`
3. `blockstates/<name>.json`
4. `items/<name>.json` for inventory rendering
5. Java block and block item registration

## Replacing Boss Models

Replace the server resource pack files:

```text
server-resourcepack/assets/guardian_mod/geckolib/models/entity/*.geo.json
server-resourcepack/assets/guardian_mod/geckolib/animations/entity/*.animation.json
server-resourcepack/assets/guardian_mod/textures/entity/*.png
```

Keep those real boss assets in `server-resourcepack`. The client module should keep only fallback assets so the client jar remains spoiler-safe.

## Packaging Server Resource Pack

Create a zip from the contents of `server-resourcepack`, not from its parent directory. The zip root should contain `assets/` and `pack.mcmeta`.

PowerShell example:

```powershell
Compress-Archive -Path server-resourcepack\assets,server-resourcepack\pack.mcmeta -DestinationPath guardian-server-resourcepack.zip -Force
```
