package grounded.planet;

import arc.graphics.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.graphics.g3d.*;
import mindustry.type.*;

/** The mod's planets. Rime and Cinder will join Halden here. */
public class GroundedPlanets{
    public static Planet halden;

    public static void load(){
        halden = new Planet("halden", Planets.sun, 1f, 3){{
            generator = new HaldenGenerator();
            meshLoader = () -> new HexMesh(this, 6);
            cloudMeshLoader = () -> new MultiMesh(
                new HexSkyMesh(this, 5, 0.15f, 0.13f, 5, Color.valueOf("e8eef2").a(0.75f), 2, 0.45f, 0.9f, 0.38f),
                new HexSkyMesh(this, 9, 0.6f, 0.16f, 5, Color.valueOf("c9d4dc").a(0.65f), 2, 0.45f, 1f, 0.41f)
            );

            //shares Serpulo's blocks and research; Halden's own industry (the steel chain) will hang off the same tree
            techTree = Planets.serpulo.techTree;

            alwaysUnlocked = true;
            accessible = true;
            startSector = 15;
            sectorSeed = 7;
            orbitSpacing = 2.2f;
            atmosphereColor = Color.valueOf("4a7fb8");
            atmosphereRadIn = 0.02f;
            atmosphereRadOut = 0.3f;
            iconColor = Color.valueOf("c0643a");
            landCloudColor = Color.valueOf("dfe7ee").a(0.5f);
            allowLaunchLoadout = true;
            allowLaunchSchematics = true;
            allowSelfSectorLaunch = true;
            allowWaves = true;
            allowSectorInvasion = true;
            launchCapacityMultiplier = 0.5f;
            enemyFactoryActivationDelay = 60f * 60f * 2f;
            prebuildBase = true;

            ruleSetter = r -> {
                r.waveTeam = Team.crux;
                r.placeRangeCheck = false;
                r.hideSpawns = true;
                r.derelictRepair = true;
                r.coreDestroyClear = true;
            };
        }};
        //temperate at the equator, freezing at the poles
        Climate.planets.put(halden, new Climate(30f, -20f));
    }
}
