# Grounded Industry

Engineering realism for Mindustry (v160+). Early game stays close to vanilla; each realism system can be switched off in **Settings > Grounded Industry**.

## Systems

| System | Status |
| --- | --- |
| Power line loss | Done |
| Brownouts and load shedding | Done |
| Generator startup time | Done |
| Machine heat and cooling | Done |
| Pipe pressure and booster pump | Done |
| Planet climate and Halden | Done |
| Rime and Cinder planets | Planned |
| Steelmaking chain | Done |
| Turret magazines and barrel heat | Done |
| Hard Realism ruleset (finite ore veins, maintenance) | Planned |

### Power line loss

Every power node (power node, large power node, surge tower) becomes a small power consumer. Its demand is the power flowing into its grid times a loss rate per tile of laser it carries. Long laser chains waste power, so it pays to put generators near the factories they feed and to bridge long distances with fewer, longer links.

- Default rate: 0.15% per tile of laser, so a 60-tile line of power nodes loses about 10%.
- Loss per grid is capped (default 40%) so big bases still work.
- Each node shows a "Line loss" bar with its grid's current loss.
- Batteries, diodes, Erekir beam nodes and the sandbox power source are unaffected.

### Brownouts and load shedding

In vanilla, an overloaded grid quietly slows every machine down. Here, if a grid's supply stays below 75% of demand for half a second, its breakers trip. Consumers are cut off, biggest loads first, until the rest of the grid is fully powered again. Turrets and other defenses are cut last, after unit factories. Tripped machines show the disabled icon and restart after 4 to 7 seconds, staggered, then trip again if the grid still can't carry them. Batteries cover short gaps, as they would on a real grid.

- The trip point (30% to 95%) and an on/off switch are in settings.
- Breakers never stay tripped in a save: everything is switched back on just before saving.
- Only the server (or single player) trips breakers.

### Generator startup time

Fuel-burning generators ramp up to full output instead of starting instantly, and spin down twice as fast when fuel runs out. A generator that keeps running dry never reaches full power.

| Generator | Startup |
| --- | --- |
| Combustion generator | 3 s |
| Chemical combustion chamber | 5 s |
| Steam generator | 10 s |
| Pyrolysis generator | 10 s |
| Differential generator | 15 s |

The RTG, solar panels and thermal generators start instantly. A "Spin-up" bar shows progress, and a setting scales all startup times from 25% to 300%. A generator saved while running loads back at full speed.

### Machine heat and cooling

Furnaces, smelters and fuel generators heat up while they work and lose heat to the air. Each machine has a heat load: the heat it settles at when running flat out with no cooling, where 100% is its safe limit. Above 100% it throttles itself, and above 120% it starts taking damage (0.5% of its health per second). Heat settles over about 20 seconds.

Machines without a liquid tank gain an optional coolant input. Water roughly triples cooling and cryofluid gives about 5.5 times. The steam generator is cooled by its own water and the differential generator by its cryofluid.

| Machine | Heat load (no coolant) | Uncooled result |
| --- | --- | --- |
| Differential generator | 160% | Always cooled by its cryofluid |
| Surge smelter | 140% | Settles at 117%, runs at 83% speed |
| Steam generator | 130% | Always cooled by its water |
| Melter | 120% | Throttles slightly (can't take coolant) |
| Silicon smelter | 115% | Throttles slightly |
| Phase weaver | 110% | Throttles slightly |
| Kiln | 90% | Fine |
| Combustion generator | 80% | Fine |
| Plastanium compressor | 80% | Fine |

Each of these machines shows a heat bar. Heat isn't saved, so machines start cold after loading. This keeps saves compatible if the mod is removed.

### Pipe pressure and booster pump

Pumps push liquid at full pressure. Each conduit tile loses some of it: 2.5% per tile for conduits, and 1.5% for pulse and plated conduits. Tanks, routers, junctions and bridges hand liquid on at only 60% pressure, since they aren't pumps. Liquid sources and machines that output liquid count as pumps.

At 50% pressure or more, a conduit flows as in vanilla. Below that, its flow is capped at a share of its capacity that shrinks as pressure falls. At zero pressure a plain conduit carries 30/s, a pulse conduit 60/s and a plated conduit 75/s. So a rotary pump's 48/s won't make it down 40 tiles of plain conduit without help.

The **Booster Pump** (2 metaglass, 2 silicon, 2 titanium, 0.3 power/s) sits inline in a pipe and restores full pressure. Liquid goes in the back and out the front. It's researched after the pulse conduit. Unpowered, it passes liquid through like a short conduit. Conduits and boosters show a pressure bar.

The booster pump's sprites are placeholders.

### Turret magazines and barrel heat

Applies to every vanilla item turret (duo, scatter, hail, salvo, ripple, fuse, cyclone, spectre, foreshadow, swarmer, and the Erekir item turrets).

**Magazines.** A turret fires about 6 seconds' worth of shots, then stops to reload. A magazine holds 3 to 60 shots, and the reload takes 2 s plus 0.75 s per tile of turret size. A duo, for example, fires 18 shots, then reloads for 2.75 s. Ammo use is unchanged.

**Barrel heat.** Each shot heats the barrel, enough that nonstop fire would reach 100% in about 8 seconds. The barrel cools fully in about 10 seconds on its own. Coolant cools it faster: water by 2.2 times, cryofluid by 3.7 times. Coolant still speeds up reloading as in vanilla. Above 70% heat, the fire rate drops (down to 25% at 100%) and shots spread by up to 6 degrees.

Under sustained fire, a duo gets off 22 shots in the first 10 seconds and 14 in the next 10, compared with 30 per 10 seconds in vanilla. That's a big change to defense balance, so both systems have their own on/off switch. Each turret shows a magazine bar and a barrel heat bar.

### Steelmaking

Serpulo has no iron ore, so iron comes out of sand by magnet. Where a map does have iron ore, it can be mined with a mechanical drill. The ore can also be placed in the map editor, and every Halden sector generates it, richest in the iron hills.

| Step | Block | Size | In | Out | Power |
| --- | --- | --- | --- | --- | --- |
| 1a | Magnetic Separator | 2x2 | 4 sand | 1 iron concentrate / 1 s | 24/s |
| 1b | Ore Concentrator | 2x2 | 2 iron ore + water | 2 iron concentrate + 1 sand / 0.75 s | 18/s |
| 2 | Coke Oven | 2x2 | 2 coal | 1 coke / 1.5 s | none |
| 3 | Sinter Plant | 2x2 | 3 iron concentrate + 1 coke | 2 sinter / 1.33 s | 30/s |
| 4 | Blast Furnace | 3x3 | 3 sinter + 2 coke + 1 sand (flux) | 2 pig iron + 4 slag/s / 2 s | 60/s |
| 5 | Electric Arc Furnace | 3x3 | 2 pig iron + 1 scrap | 2 steel / 1.5 s | 240/s |

The blast furnace's slag has to be piped away, for example to a separator, which turns it back into copper, lead, graphite and titanium. If the slag has nowhere to go, the furnace stops. The coke oven, sinter plant, blast furnace and arc furnace all use machine heat, and the two big furnaces need water to run at full speed. The arc furnace's 240/s power draw is a good test of a grid's brownout margin.

A full line making 74 steel per minute takes 1 arc furnace, about 1.3 blast furnaces, 1 sinter plant, 2 to 3 magnetic separators and about 3 coke ovens.

**Built from steel:**

- **Steel Wall** (6 steel) and **Large Steel Wall**: 640 and 2560 health with armor 6, compared with 440 and armor 0 for titanium.
- **Transmission Tower** (10 steel, 8 silicon, 10 copper): a 2x2 power node with a 30-tile range and 4 links, which loses only a quarter of the usual line loss per tile.

Everything appears in the Serpulo tech tree: the separator after the pulverizer, the coke oven after the graphite press, walls after titanium walls, and the tower after the large power node. All sprites for this chain are placeholders.

### Planets and climate

**Halden** is the mod's home world, a temperate planet that orbits the sun next to Serpulo. It is unlocked from the start: pick it in the planet view and launch from the sector marked with a house icon ("Landing Site"). It builds with Serpulo's blocks (plus the steel chain), but has its own research tree, picked from the research dialog's tree selector. That tree is paid for with items from Halden's sectors, not Serpulo's, and leaves out Serpulo's story sectors. A block researched in either tree is unlocked in both, because the game tracks unlocks per block. The first node in Halden's tree is the planet itself, and its info page lists Halden's resources and climate. Every sector is generated in code, with no hand-made maps:

- The ground follows latitude and height. There are ice caps at the poles, grassland, mud flats and rivers in the temperate band, and rust-red ferric hills toward the equator.
- Copper and lead are everywhere. Coal basins form in the lowlands, titanium in the colder sectors, and thorium in the hottest hills. The landing sector has copper, lead and coal but no titanium.
- Sectors have derelict ruins on resources, and either wave spawns or an enemy base. About a quarter of sectors start with an enemy base, never next to the landing zone.

**Climate.** Each modded planet has an air temperature at its equator and at its poles. Halden runs from 30°C at the equator to -20°C at the poles. The current sector's temperature feeds the other systems:

| Effect | At -20°C (Halden poles) | At 30°C (Halden equator) |
| --- | --- | --- |
| Machine heat | Kiln settles at 76% instead of 90% | Kiln settles at 96% |
| Pipe pressure drop | x1.42 (cold water is thicker) | x0.82 |
| Generator startup time | x1.35 | x0.96 |
| Power line loss | x0.86 | x1.06 (hot copper resists more) |

At 15°C every effect is neutral. Vanilla planets have no climate, so they play as before. The climate can be switched off in settings.

Rime (a frozen moon) and Cinder (a volcanic world) are planned next.

## Building

`./gradlew jar` builds `build/libs/<folder name>Desktop.jar`. `./gradlew deploy` also builds the Android version (needs the Android SDK's `d8` on your PATH). GitHub Actions builds both on every push, and attaches the jar to each published release.

## Checks

`test/LineLossCheck.java`, `test/GridCheck.java`, `test/HeatCheck.java`, `test/PressureCheck.java`, `test/TurretCheck.java`, `test/SteelCheck.java` and `test/PlanetCheck.java` run the real game code headlessly and check each system's numbers. Download `server-release.jar` for v160 from the Mindustry releases, then:

```
javac --release 17 -sourcepath src -cp server-release.jar -d build/check $(find src test -name '*.java')
java -cp server-release.jar:build/check LineLossCheck
java -cp server-release.jar:build/check GridCheck
java -cp server-release.jar:build/check HeatCheck
java -cp server-release.jar:build/check PressureCheck
java -cp server-release.jar:build/check TurretCheck
java -cp server-release.jar:build/check SteelCheck
java -cp server-release.jar:build/check PlanetCheck
```
