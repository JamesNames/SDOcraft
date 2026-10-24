# RoN x TaCZ x Superb Warfare v0.2.3
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

## v0.2.3: M1A2 vehicle driver
- Drive commands processed every server level tick, independent of mounted mob goal ticking.
- M1A2 engine input on correct tracked vehicle left/right yaw directions.
- If tank does not physically advance despite valid orders, a slow collision-aware vehicle movement fallback engages after 45 ticks.
- Info on move orders and fallback in `latest.log` under `M1A2 RTS`.
- Avoid obstacles: full vehicle obstacle pathfinding is not implemented.

## v0.2.3: M1A2 correct steering direction (critical fix)
- GitHub compilation and logs from 2026-10-24 confirmed input commands reach the vehicle, energy and engineInfo are valid, but it spins without making horizontal progress.
- Corrected tracked engine steering sign: right (bit 1) increases yaw, left (bit 0) decreases yaw.
- Logs `M1A2 RTS drive` every 60 ticks while active: turn direction, engine input, horizontal speed, onGround, energy, target.
- For first test, drive over completely flat terrain. Forward movement still needs in-game validation.
