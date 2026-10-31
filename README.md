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
| Steelmaking chain | Planned |
| Turret magazines and barrel heat | Planned |
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

## Building

`./gradlew jar` builds `build/libs/<folder name>Desktop.jar`. `./gradlew deploy` also builds the Android version (needs the Android SDK's `d8` on your PATH). GitHub Actions builds both on every push, and attaches the jar to each published release.

## Checks

`test/LineLossCheck.java`, `test/GridCheck.java`, `test/HeatCheck.java` and `test/PressureCheck.java` run the real game code headlessly and check each system's numbers. Download `server-release.jar` for v160 from the Mindustry releases, then:

```
javac --release 17 -sourcepath src -cp server-release.jar -d build/check $(find src test -name '*.java')
java -cp server-release.jar:build/check LineLossCheck
java -cp server-release.jar:build/check GridCheck
java -cp server-release.jar:build/check HeatCheck
java -cp server-release.jar:build/check PressureCheck
```
