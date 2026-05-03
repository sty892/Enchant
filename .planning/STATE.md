# Guardian Mod Foundation State

## Completed in this pass

- Project identity aligned to `guardian_mod` with package root `me.guardian`.
- Common, client, and server Fabric metadata use separate mod ids.
- Build uses Minecraft `1.21.11`, Fabric Loader `0.16.9`, Fabric API `0.141.1+1.21.11`, GeckoLib `5.4.5`, Java 21, official Mojang mappings, and Fabric Loom `1.10-SNAPSHOT`.
- The broken gameplay implementation was reduced to a compiling skeleton.
- Runtime config bootstrapping writes defaults under Fabric Loader config dir `guardian_mod`.
- Client jar contains only client entrypoint and metadata; boss runtime assets are not packaged there.

## Build notes

- Fabric API `0.141.1+1.21.11` is built with newer Loom metadata than Loom `1.10.5` accepts as a `modImplementation` dependency.
- Fabric API and GeckoLib are therefore kept on the compile-only classpath for this skeleton pass, while runtime requirements remain declared in `fabric.mod.json`.
- Loom `enableModProvidedJavadoc` and `enableTransitiveAccessWideners` are disabled to avoid processing Fabric API metadata that is incompatible with Loom `1.10.5`.

## Next scope

- Reintroduce server-side boss entity registration in `common` or `server` without client-only model dependencies.
- Add a real server-side boss hitbox entity that is visible through vanilla debug hitbox rendering.
- Keep model, texture, and animation delivery server-authoritative; client jar should only contain a fallback if required.
