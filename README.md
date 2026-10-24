# RoN x TaCZ x Superb Warfare v0.4.9
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

## VNR vs VFSM v0.4.9 — two opposing military sides (first playable integration)
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

### v0.4.9 Mi-28 stability and selection
- Fixed unlimited rotor thrust / unbounded climb: RTS flight drives a capped, collision-aware step every server tick with gravity disabled while an RTS pilot is mounted.
- Pilot selection by a ray hit on the aircraft beyond RoN's 30-block ground cursor radius.
- Hover target uses previous hold position; new move command resets hold anchor.
- On dismount Mi-28 restores gravity.
- Important: this is controlled arcade flight, NOT the physical Superb Warfare flight model. Rotor animation may be limited; collision routing, missile autoattack, safe landing remain unsupported.

### v0.4.9 Mi-28 waypoint altitude fixes
- Visual test 2026-10-24: pilot selection works, and Mi-28 reaches waypoint horizontally but descends near tanks.
- Bug: old waypoint completion checked only X/Z and stopped climb prematurely.
- New arrival requires both X/Z within 4 blocks and altitude within 2.5 blocks.
- Two-stage path: first climb to at least 14.5 blocks above terrain, only then cruise horizontally to destination at +17 blocks.
- Mi-28 no longer starts with 0.16 thrust immediately on spawn.
- Hold anchor remains at reached safe height.
- This still needs in-game testing with video/logs.

### v0.4.9 Mi-28 stable single-controller flight
- Corrected root cause of ceiling-hitting and jerky flying: SBW native rotor travel and RTS position movement both affected helicopter concurrently.
- Cancel native VehicleEntity.travel() **only while** our Mi28CrewUnit occupies helicopter pilot seat; keep model, weapons, rotor animation and rendering.
- Smooth, acceleration-limited RTS travel with collision-aware movement, altitude cap, and slow-to-stop behavior.
- Vertical ascent first, horizontal move second, and stable waypoint hover after arrival.
- Tested: Gradle build only; physics still requires in-game verification.

### v0.4.9 Superb Warfare infantry armor
- All TaCZ riflemen, machine gunners, snipers are automatically equipped with Superb Warfare 3D armor.
- VNR rebels: Russian 6B47 helmet, 6B43 chest; bronze leather trousers/boots.
- VFSM regular army: US PASGT helmet, IOTV chest; cyan leather trousers/boots.
- Unassigned-faction infantry: US vest/helmet + neutral-grey leather pants/boots.
- Original SBW ballistic resistance / durability remain active because the armor items are the mod's own classes.
- Uniform reapplication does not heal existing chest/helmet armor durability.
- All tank/pilot crews retain their existing color-tinted lightweight armor.
- Already recruited infantry: use `/rontacz faction vnr` or `/rontacz faction vfsm` again to refresh their equipped gear.
- The armor textures are the original Superb Warfare texture colors, not dynamically dyed bronze/cyan.

### v0.4.9 Tank combat / survivability fix
- Tanks have 500 HP in Superb Warfare. They are naturally immune to small arms and melee by the original damage modifier JSON.
- Previous releases only aimed tank turrets. M1A2/T-90A now call native Superb Warfare `vehicleShoot` for real cannon shells at hostile military units, prioritizing armor, every ~6 seconds.
- RTS faction scoreboard's `allowFriendlyFire=true` ensures scoreboards alone cannot veto ALL damage. This also permits manually damaging your own side, so use care.
- Each tank can auto-feed **one native AP shell** in testing; native SBW still determines projectile hits, armor and 500 HP.
- Energy no longer auto-refills: native fuel consumption is respected. Neither health nor armor is forcibly overwritten.
- Flight v0.4.3 single-controller Mi-28 remains included; **gameplay confirmation is still required**.
- Test by having two different RTS players choose different sides and command tank attacks.

## v0.4.9 repair — no T-90 faction/spawn changes
- Mi-28 jumps: SBW baseTick has a second gravity+move step besides its travel() method; cancel this native step only while our RTS pilot is seated.
- Armor missing: supply Superb Warfare's actual 3D model textures to RoN custom armor renderer; re-equip after join if armor slots remain empty.
- Tanks not shooting: set server driver Mob.target as required by native SBW AI; acquire enemy RTS soldiers or nearby vanilla monsters, and log ammo/weapon gating.
- v0.4.5 fuel/damage rules retained; T-90A faction restriction remains VNR-only.
- Gameplay tests still required. Do not install together with older addon JAR.

### v0.4.9 strict player-targeted vehicle combat
- Removed autonomous enemy search; no firing without a forced RoN ATTACK order.
- Targets from `driver.getTargetGoal().getTarget()` only when `forced` true.
- Rejects friendly ownership, shared team, creative players, and friendly splash/line-of-fire.
- Full-size tank turrets rotate toward the actual clicked target using the real SBW turret vector API.
- Uses named SBW Cannon (seat 0) / Mi-28 pilot Rocket rather than whichever seat weapon was selected.
- Mi-28 moves yaw toward clicked target and fires real SBW rockets when aligned.
- Never modify target in TankCrewUnit.tick. T-90 recruitment unchanged.
- Gameplay testing still required (especially SBW rocket physics); **do not use v0.4.6 near friendly soldiers**.

### v0.4.9 forced attack on ANY chosen unit + infinite ammo
- Direct RTS attack commands (A + left-click) can deliberately target **any** other entity, regardless of team; RTS combat never searches arbitrary enemies.
- Tanks can target other M1A2 and T-90A by aiming at the mounted entity's parent vehicle.
- Mi-28 now pitches downward at ground targets and aligns the real Rocket firing vector instead of rejecting nearly all high-altitude shots.
- Infinite native ammunition: install actual Superb Warfare Creative Ammo Box inside each RTS tank/heli inventory, refill all its magazine weapons periodically.
- Friendly fire **is allowed when ordered** and collateral blast damage may occur. Always test in a separate world.
- Flight and live combat still need in-game verification.

## v0.4.9 — ATGM truck and missile infantry
- Actual Superb Warfare Sodayo Pick Up TOW is recruitable from Villagers Blacksmith via slot 8 (I).
- Actual Superb Warfare RPG infantry in Barracks via slot 9 (O).
- Igla 9K38 MANPADS infantry in Barracks via slot 10 (P).
- Javelin FGM-148 anti-tank infantry in Blacksmith via slot 7 (U).
- RPG, Javelin and Igla are native Superb Warfare weapon items using genuine missile/projectile entities, issued to selectable RoN pillager infantry.
- MANPADS prioritize rotor/aircraft passengers; Javelin and RPG use normal RTS targeting.
- The Sodayo TOW has an RTS selectable driver, wheeled motion using present vehicle movement system and server-directed turret plus genuine TOW missile.
- Every faction can recruit these additions; existing VFSM Abrams and VNR T-90 restrictions remain unchanged.
- Production layout avoids overwriting existing Barracks first eight slots or Blacksmith first six slots.
- The visual model of Barracks remains the original RoN building in v0.4.9. A realistic military barracks is a separate asset/replacement project.
- Compilation success alone DOES NOT verify behavior, missiles, movement or rendered 3D items; test in creative.

### v0.5.0: militarized RoN Barracks and corrected 9K38 Igla RTS air defense
- Replaces the vanilla Reign of Nether barracks structure on **both** client and server via a deterministic 15x13 military bunker blueprint with concrete foundation, olive-gray camo roof, defensive sandbags, iron windows, searchlights, supplies and a radio antenna.
- IMPORTANT: Back up your world before update. New barracks and existing save previews use a different block footprint; buildings created in previous versions may need rebuilding.
- Igla now tracks the native airborne Superb Warfare vehicle itself (not its hidden RoN pilot) and uses a line-of-sight ray toward aircraft hitbox.
- Igla launches from RTS target goal even if RoN's vanilla ranged goal cannot see a pilot obscured by the vehicle.
- Guidance re-applies vehicle UUID briefly to missiles fired by the Igla, without scanning/selecting other friendly targets.
- This requires real gameplay validation for 9K38 missile pathing and barracks building.

## v0.5.1 Hangar for all vehicles and barracks for all infantry
- Military Barracks hotkeys 3-8: TaCZ AK47, AWM, M249, RPG, Igla, Javelin.
- Blacksmith / vehicle hangar: original research 1-4 retained; vehicle hotkeys 7-10: M1A2, T-90A, Mi-28, Sodayo TOW.
- Hangar uses real 23x21 industrial building footprint and 9-wide gate; old Blacksmith and Superior Blacksmith structures replaced.
- **BACKUP WORLD**: hangar footprint changes existing Blacksmith blueprint and may require rebuilding old blacksmiths.
- Compile validation is not runtime testing: check the new structure in a separate creative world.

## v0.5.2 arched Quonset hangar (replaces flat-roof v0.5.1)
- Former Blacksmith/Vehicle Hangar now has a true semicircular cross-section, connected curved roof and structural ribs across its full 21-block depth. **No flat roof or rooftop landing H.**
- Same 23x21 floor footprint. Roof reaches y=11; the gate is 9 blocks wide with a rounded top for high vehicles.
- The front retains a vehicle entrance; workshop supplies and building functionality remain.
- **Back up worlds** and rebuild existing Blacksmith/Hangar structures as the blueprint geometry changed. Check RoN preview in creative. Runtime model not yet validated.

### v0.5.3 — construction and production safety
- RoN charges for queued units before attempting to spawn them. If an infantry unit or vehicle fails creation, the paid food/wood/ore/emerald are returned with an in-game warning and server log.
- Train infantry just OUTSIDE the military barracks, avoiding collisions with the new giant foundation and roof. Create vehicle crew at the hangar exit, not trapped inside the building.
- In case of no flat external vehicle spawn, fail and refund rather than silently generate the machine through the landscape.
- Newly constructed military barracks and vehicle hangars automatically continue slow construction after 6 seconds if villagers cannot reach the construction perimeter; assigned builders still work normally.
- Production failures logged under `RTS production failed` and `Refunded failed RTS production`.
