# Gemini Continue

Current goal: continue from a compiling repair/skeleton foundation, not from the removed broken implementation.

Verified commands in this pass:

```powershell
.\gradlew.bat :common:compileJava --stacktrace
.\gradlew.bat build --stacktrace
```

Both commands passed after the skeleton repair.

Important constraints for the next pass:

- Do not move boss model, texture, or animation assets into the client jar.
- Keep boss gameplay logic server-side.
- Do not reintroduce the old `me.sty892.enchant` package.
- Do not implement full boss AI yet.
- First next feature should be a minimal real boss entity with a real hitbox that works with vanilla hits and debug hitbox rendering.

Runtime config location:

```java
FabricLoader.getInstance().getConfigDir().resolve("guardian_mod")
```

Default configs that must continue to exist:

- `boss_overworld.json`
- `boss_nether.json`
- `boss_generic.json`
- `altar_config.json`
- `keys_config.json`
- `guardian_config.json`

Known build compromise:

Fabric API `0.141.1+1.21.11` declares Loom metadata produced by Loom `1.13.3`, while the requested build uses Fabric Loom `1.10-SNAPSHOT` (`1.10.5` resolved locally). Keeping Fabric API as `modImplementation` fails during configuration before Java compilation. The current skeleton keeps Fabric API and GeckoLib as `compileOnly` dependencies and declares runtime dependencies in `fabric.mod.json`.
