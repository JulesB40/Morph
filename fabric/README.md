# Fabric 26.2 prototype

This is a separate Fabric build of the community Morph port, not the old Forge JAR. Install it on both the client and the server with Fabric API. Do not install the NeoForge JAR alongside it.

## Build

From this directory, run `./gradlew build` (Windows: `gradlew.bat build`). From the repository root use `fabric/gradlew -p fabric build`. Java 25 is provisioned by Gradle if necessary. The Fabric wrapper uses Gradle 9.5.1 because Loom 1.17.20 requires Gradle 9.5 or newer; the root NeoForge wrapper is independent.

Output: `build/libs/Morph-Fabric-26.2-11.0.0-alpha.2.jar`.

Pinned official dependencies, checked 2026-09-08:

- Minecraft 26.2, Fabric Loader 0.19.5: https://meta.fabricmc.net/v2/versions/loader/26.2
- Fabric API 0.160.0+26.2: https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml
- Loom 1.17.20: https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml

## Features

Kill a supported vanilla living entity to unlock its type. Press `[` to open the paginated collection selector. Selection is server-authoritative with a one-second cooldown. Reset has no cooldown; changing to a larger shape requires enough room.

- `/morph list`: list owned forms.
- `/morph select minecraft:cow`: choose an owned form.
- `/morph reset`: return to player appearance.
- `/morph grant <player> minecraft:cow`: operator-only grant (gamemaster permission).
- `/morph menu`: refresh collection data for the selector.

Ownership and the selected form persist by UUID in overworld SavedData, surviving logout and dimension changes. Death resets the selected appearance while retaining owned forms. Appearance updates go to connected clients and joining clients receive current player appearances. Collection data is sent only to its owner. There is no client payload capable of granting ownership.

Bat, bee, and parrot forms grant flight and fall immunity; aquatic forms replenish air underwater. Flight is temporary, is excluded from saved player abilities, and is removed on reset, death, and logout. Pre-existing flight permissions are preserved; other mods granting flight during a Morph flight lease should use the shared external-flight integration hook.

The Fabric build shares the loader-independent ownership model, vanilla SavedData codec, selector screen, and renderer extraction adapter with the NeoForge build. Fabric supplies native events, payload registration, key binding, and client-only renderer mixins.

Both builds share the five-second black transformation effect and the original six morph sounds. The renderer interpolates captured posed vertices; it approximates the legacy part/box algorithm. The effect is third-person; first-person hands remain vanilla.

## Scope and remaining validation

This is an early development prototype, not original-mod feature parity. Morphs use default vanilla entity appearances; variants/NBT, custom mod entities, first-person arms, exact legacy part/box interpolation, the remaining original abilities, and original UI behavior remain to be ported. Unsupported render extraction falls back to the player. Standing/crouching hitbox and eye height follow the default entity shape, with collision checks before selection/reset. Other movement and combat retain player rules.

Automated `build` runs shared ownership unit tests and a dedicated Fabric GameTest server. Run only the integration server with `gradlew runGameTest`. Tests cover acquisition through a lethal attack, command parsing/ownership/cooldown, geometry/reset, save isolation, and flight/fall cleanup. Runtime smoke checks verified the native selector, active-form marker, and pig rendering in both third-person views; see `../docs/screenshots/fabric-pig.png`. Before release, complete two-client observation/late-join checks and the remaining gameplay cases: logout/rejoin, death/respawn, dimension changes, world save/restart, and non-operator grant rejection. These manual gameplay checks are not implied by a successful compilation or title-screen launch.



The development client uses the stable offline username `MorphTester`, so its UUID and saved collection remain consistent between `runClient` launches.
