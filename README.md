# RoN x TaCZ x Superb Warfare v0.4.4
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

## v0.3.0: T-90A and Mi-28 helicopter
- Recruitable in Villager Barracks: ability slots 7 (T-90A) and 8 (Mi-28).
- Both use real vehicles from the exact Superb Warfare 0.8.9.2 version.
- Both are selectable using RoN's mounted-crew hitbox and can receive move orders.
- T-90A reuses tracked-vehicle RTS driving with collision-aware fallback.
- Mi-28 has an **experimental** server-tick autopilot: native rotor thrust and steering, rise toward ~18 blocks over terrain, turn and pitch toward waypoint, and hold position upon arrival.
- Airborne routing, landing, terrain avoidance, missile targeting, combat and safe rotor behavior are **not proven in live gameplay**. Spawn on open flat ground; test in creative.
- Both currently trained at Barracks using pillager cost; separate factories/helipad and balance later.

## VNR vs VFSM v0.4.4 — two opposing military sides (first playable integration)
Choose a side as a Reign of Nether **Villagers** RTS player:
```
/rontacz faction vnr
/rontacz faction vfsm
/rontacz faction status
/rontacz promote <unitEntityId>
```
VNR (ВНР): bronze (#CD7F32) insurgents, military equipment T-90A.
VFSM (ВФСМ): cyan (#57C7FF) regular forces, M1A2 Abrams.
Both sides recruit identical TaCZ AK47 riflemen, AWM snipers, M249 gunners; Mi-28 remains shared.
RoN unit owner/alliance rules determine enemy hostility: use different RTS players and do not ally.
Faction membership, team colors persist with world scoreboard. Infantry has dyed-leather
faction kit and Cyrillic name/rank label. Rank can be promoted via entity numeric ID.

**Limitations:** This is NOT yet two entries in RoN's hard-coded match-start faction picker.
Both players still choose the Villagers base faction and assign a military side via command.
Faction buildings, rank-specific combat stats, faction AI bots, own colored 3D skins,
and automatic war in single-player are not yet implemented.

### v0.4.4 Mi-28 stability and selection
- Fixed unlimited rotor thrust / unbounded climb: RTS flight drives a capped, collision-aware step every server tick with gravity disabled while an RTS pilot is mounted.
- Pilot selection by a ray hit on the aircraft beyond RoN's 30-block ground cursor radius.
- Hover target uses previous hold position; new move command resets hold anchor.
- On dismount Mi-28 restores gravity.
- Important: this is controlled arcade flight, NOT the physical Superb Warfare flight model. Rotor animation may be limited; collision routing, missile autoattack, safe landing remain unsupported.

### v0.4.4 Mi-28 waypoint altitude fixes
- Visual test 2026-10-24: pilot selection works, and Mi-28 reaches waypoint horizontally but descends near tanks.
- Bug: old waypoint completion checked only X/Z and stopped climb prematurely.
- New arrival requires both X/Z within 4 blocks and altitude within 2.5 blocks.
- Two-stage path: first climb to at least 14.5 blocks above terrain, only then cruise horizontally to destination at +17 blocks.
- Mi-28 no longer starts with 0.16 thrust immediately on spawn.
- Hold anchor remains at reached safe height.
- This still needs in-game testing with video/logs.

### v0.4.4 Mi-28 stable single-controller flight
- Corrected root cause of ceiling-hitting and jerky flying: SBW native rotor travel and RTS position movement both affected helicopter concurrently.
- Cancel native VehicleEntity.travel() **only while** our Mi28CrewUnit occupies helicopter pilot seat; keep model, weapons, rotor animation and rendering.
- Smooth, acceleration-limited RTS travel with collision-aware movement, altitude cap, and slow-to-stop behavior.
- Vertical ascent first, horizontal move second, and stable waypoint hover after arrival.
- Tested: Gradle build only; physics still requires in-game verification.

### v0.4.4 Superb Warfare infantry armor
- All TaCZ riflemen, machine gunners, snipers are automatically equipped with Superb Warfare 3D armor.
- VNR rebels: Russian 6B47 helmet, 6B43 chest; bronze leather trousers/boots.
- VFSM regular army: US PASGT helmet, IOTV chest; cyan leather trousers/boots.
- Unassigned-faction infantry: US vest/helmet + neutral-grey leather pants/boots.
- Original SBW ballistic resistance / durability remain active because the armor items are the mod's own classes.
- Uniform reapplication does not heal existing chest/helmet armor durability.
- All tank/pilot crews retain their existing color-tinted lightweight armor.
- Already recruited infantry: use `/rontacz faction vnr` or `/rontacz faction vfsm` again to refresh their equipped gear.
- The armor textures are the original Superb Warfare texture colors, not dynamically dyed bronze/cyan.
