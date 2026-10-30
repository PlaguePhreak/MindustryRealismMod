# Grounded Industry

Engineering realism for Mindustry (v160+). Early game stays close to vanilla; each realism system can be switched off in **Settings > Grounded Industry**.

## Systems

| System | Status |
| --- | --- |
| Power line loss | Done |
| Grid stability (brownouts, generator startup) | Planned |
| Machine heat and cooling | Planned |
| Pipe pressure | Planned |
| Steelmaking chain | Planned |
| Turret magazines and barrel heat | Planned |
| Hard Realism ruleset (finite ore veins, maintenance) | Planned |

### Power line loss

Every power node (power node, large power node, surge tower) becomes a small power consumer. Its demand is the power flowing into its grid times a loss rate per tile of laser it carries. Long laser chains waste power, so it pays to put generators near the factories they feed and to bridge long distances with fewer, longer links.

- Default rate: 0.15% per tile of laser, so a 60-tile line of power nodes loses about 10%.
- Loss per grid is capped (default 40%) so big bases still work.
- Each node shows a "Line loss" bar with its grid's current loss.
- Batteries, diodes, Erekir beam nodes and the sandbox power source are unaffected.

## Building

`./gradlew jar` builds `build/libs/<folder name>Desktop.jar`. `./gradlew deploy` also builds the Android version (needs the Android SDK's `d8` on your PATH). GitHub Actions builds both on every push, and attaches the jar to each published release.

## Checks

`test/LineLossCheck.java` runs the real game code headlessly and checks the loss numbers. Download `server-release.jar` for v160 from the Mindustry releases, then:

```
javac --release 17 -sourcepath src -cp server-release.jar -d build/check $(find src test -name '*.java')
java -cp server-release.jar:build/check LineLossCheck
```
