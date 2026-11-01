package grounded.planet;

import arc.graphics.*;
import arc.scene.style.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.game.Objectives.*;
import mindustry.game.*;
import mindustry.graphics.g3d.*;
import mindustry.type.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

/** The mod's planets. Rime and Cinder will join Halden here. */
public class GroundedPlanets{
    public static final Stat resources = new Stat("gi-resources"), climate = new Stat("gi-climate");

    public static Planet halden;

    public static void load(){
        halden = new Planet("halden", Planets.sun, 1f, 3){
            @Override
            public void setStats(){
                //shown when the planet is opened from the research tree or the core database
                stats.add(resources, StatValues.content(HaldenGenerator.resources()));
                var c = Climate.planets.get(this);
                if(c != null) stats.add(climate, "@ to @°C", (int)c.poles, (int)c.equator);
            }
        {
            generator = new HaldenGenerator();
            meshLoader = () -> new HexMesh(this, 6);
            cloudMeshLoader = () -> new MultiMesh(
                new HexSkyMesh(this, 5, 0.15f, 0.13f, 5, Color.valueOf("e8eef2").a(0.75f), 2, 0.45f, 0.9f, 0.38f),
                new HexSkyMesh(this, 9, 0.6f, 0.16f, 5, Color.valueOf("c9d4dc").a(0.65f), 2, 0.45f, 1f, 0.41f)
            );

            //builds with Serpulo's blocks, so they are shown on Halden; Halden gets its own tree in buildTechTree()
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

    /** Halden's own research tree, which appears in the research dialog's tree selector. */
    public static TechNode haldenTree;

    /**
     * Gives Halden its own research tree. It mirrors Serpulo's, since Halden builds with the same blocks, minus
     * Serpulo's story sectors. Research is shared: unlocking a block in either tree unlocks it in both. Call after
     * every other node has been added to Serpulo's tree.
     */
    public static void buildTechTree(){
        TechNode serpulo = Planets.serpulo.techTree;
        haldenTree = new TechNode(null, serpulo.content, serpulo.requirements);
        haldenTree.name = "halden";
        haldenTree.planet = halden;
        TechTree.roots.add(haldenTree);
        new TechNode(haldenTree, halden, ItemStack.empty);

        for(TechNode child : serpulo.children){
            mirror(child, haldenTree);
        }

        //blocks keep pointing at their Serpulo node, which the rest of the game expects
        serpulo.each(n -> n.content.techNode = n);

        halden.techTree = haldenTree;
        haldenTree.addPlanet(halden);
        if(!headless && halden.uiIcon != null && halden.uiIcon.found()){
            haldenTree.icon = new TextureRegionDrawable(halden.uiIcon);
        }
    }

    static void mirror(TechNode source, TechNode parent){
        //Serpulo's story sectors and the planet itself don't belong on Halden; their children move up a level
        if(source.content instanceof SectorPreset || source.content == halden){
            for(TechNode child : source.children) mirror(child, parent);
            return;
        }

        TechNode copy = new TechNode(parent, source.content, source.requirements);
        for(var objective : source.objectives){
            //keep research and production goals; drop ones tied to Serpulo's sectors
            if(objective instanceof Produce || (objective instanceof Research res && !copy.objectives.contains(o -> o instanceof Research r && r.content == res.content))){
                copy.objectives.add(objective);
            }
        }
        for(TechNode child : source.children){
            mirror(child, copy);
        }
    }
}
