# RoN x TaCZ x Superb Warfare v0.2.1
Minecraft Forge 1.20.1
Required mods: Reign of Nether 1.5.0b, TaCZ 1.1.8-hotfix2, Superb Warfare **0.8.9.2** (file 9019882), Kotlin for Forge 4.12.0.

## Units
Villager Barracks recruit (hotkeys 3-6): Rifleman AK47, Sniper AWM, Machine Gunner M249, M1A2 Abrams + RTS commander.
The tank is a real SBW M1A2 entity. The commander is a selectable RoN Unit passenger.
The prototype driver follows simple direct-bearing orders; **this is not obstacle-aware vehicle pathfinding**. Open terrain is recommended. When seated the crew controls AI turret/weapon through SBW.
No game runtime test has been performed. Keep backups. All units temporarily use Pillager recruitment cost; balancing TBD.

## Build
Use Java 17, Gradle 7.6.5: `gradle build --no-daemon`
Install resulting JAR on both client/server alongside dependencies.

## v0.2.1 changes
- Clicking M1A2 now selects its RTS TankCrewUnit via a larger selection area.
- Only this mounted crew is exempt from RoN's passenger selection block.
- Tank steering is driven in the unit tick and has corrected turning direction.
- Crew outside the tank uses vanilla/RTS navigation, not tank-only movement.
- Tank navigation is direct heading, not obstacle-aware.
