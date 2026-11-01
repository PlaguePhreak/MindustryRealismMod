import arc.*;
import arc.backend.headless.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import grounded.content.*;
import grounded.heat.*;
import grounded.liquid.*;
import grounded.planet.*;
import grounded.power.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.sandbox.*;

import static mindustry.Vars.*;

/**
 * Headless checks of the Halden planet and planet climate against the real game code. Run with the Mindustry server
 * jar on the classpath:
 * java -cp server-release.jar:build/check PlanetCheck
 */
public class PlanetCheck{
    static int failures = 0;

    public static void main(String[] args) throws Exception{
        boolean[] ready = {false};
        Throwable[] error = {null};

        new HeadlessApplication(new ApplicationCore(){
            @Override
            public void setup(){
                Core.settings.setDataDirectory(new Fi(System.getProperty("java.io.tmpdir")).child("gi-test-data"));
                //sector info is saved as JSON, which needs the game's content serializers
                Core.settings.setJson(mindustry.io.JsonIO.json);
                headless = true;
                net = new Net(null);
                tree = new FileTree();
                Vars.init();
                world = new World();
                content.createBaseContent();
                GIItems.load();
                GIBlocks.load();
                LineLoss.apply();
                GeneratorStartup.apply();
                MachineHeat.apply();
                PipePressure.apply();
                GroundedPlanets.load();
                HaldenGenerator.extraOres.add(GIBlocks.oreIron);
                add(logic = new Logic());
                content.init();
                bases.load();
                LineLoss.addBars();
                GeneratorStartup.addBars();
                MachineHeat.addBars();
                PipePressure.addBars();
            }

            @Override
            public void init(){
                super.init();
                ready[0] = true;
            }
        }, t -> error[0] = t);

        while(!ready[0]){
            if(error[0] != null) throw new RuntimeException(error[0]);
            Thread.sleep(10);
        }

        Core.app.post(() -> {
            try{
                run();
            }catch(Throwable t){
                t.printStackTrace();
                failures++;
            }
            System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
            System.exit(failures == 0 ? 0 : 1);
        });
    }

    static void run(){
        Planet halden = GroundedPlanets.halden;
        Sector start = halden.getStartSector();
        int bases = halden.sectors.count(Sector::hasEnemyBase);
        System.out.printf("Halden: %d sectors, %d enemy bases, start sector %d%n", halden.sectors.size, bases, start.id);
        check("Halden has sectors", halden.sectors.size > 100);
        check("landing sector has no enemy base", !start.hasEnemyBase());
        check("some sectors have enemy bases", bases > 5 && bases < halden.sectors.size / 2);
        check("Halden appears in the research tree", halden.techNode != null && Planets.serpulo.techTree.children.contains(halden.techNode));
        check("Halden lists its resources", HaldenGenerator.resources().contains(Items.coal) && HaldenGenerator.resources().contains(Liquids.water));
        check("landing site is marked", start.info.name != null && start.info.icon != null);
        check("Halden shares Serpulo's research", halden.techTree == Planets.serpulo.techTree && Blocks.mechanicalDrill.isOnPlanet(halden));

        //climate by latitude
        Sector polar = halden.sectors.max(s -> Math.abs(s.tile.v.y));
        Sector equator = halden.sectors.min(s -> Math.abs(s.tile.v.y));
        float tp = Climate.temperatureOf(polar), te = Climate.temperatureOf(equator), ts = Climate.temperatureOf(start);
        System.out.printf("temperatures: polar %.0f°C, equator %.0f°C, landing %.0f°C, Serpulo %.0f°C%n", tp, te, ts, Climate.temperatureOf(Planets.serpulo.sectors.first()));
        check("poles are freezing", tp < -10f);
        check("equator is warm", te > 25f);
        check("vanilla planets are neutral", Climate.temperatureOf(Planets.serpulo.sectors.first()) == Climate.reference);

        //generate sectors: landing, polar, equatorial and an enemy base
        int ironTotal = 0;
        Seq<Sector> samples = Seq.with(start, polar, equator, halden.sectors.find(s -> s.hasEnemyBase() && s != polar && s != equator));
        for(Sector sector : samples){
            if(sector == null) continue;
            long t0 = Time.millis();
            world.loadSector(sector);
            long ms = Time.timeSinceMillis(t0);

            ObjectIntMap<Block> floors = new ObjectIntMap<>(), ores = new ObjectIntMap<>();
            int open = 0;
            for(Tile t : world.tiles){
                floors.increment(t.floor());
                if(t.overlay() != Blocks.air) ores.increment(t.overlay());
                if(!t.solid()) open++;
            }
            var top = floors.entries().toArray();
            top.sort(e -> -e.value);
            StringBuilder sb = new StringBuilder();
            for(int i = 0; i < Math.min(4, top.size); i++) sb.append(top.get(i).key.name).append(' ');

            boolean core = state.rules.defaultTeam.core() != null;
            boolean enemy = state.rules.waveTeam.cores().any() || spawner.countSpawns() > 0;
            System.out.printf("sector %d (%.0f°C): %dx%d in %dms, %d%% open, floors: %s| copper %d, lead %d, coal %d, titanium %d, thorium %d, iron %d, enemy cores %d, spawns %d%n",
                sector.id, Climate.temperatureOf(sector), world.width(), world.height(), ms, open * 100 / (world.width() * world.height()), sb,
                ores.get(Blocks.oreCopper), ores.get(Blocks.oreLead), ores.get(Blocks.oreCoal), ores.get(Blocks.oreTitanium), ores.get(Blocks.oreThorium), ores.get(GIBlocks.oreIron),
                state.rules.waveTeam.cores().size, spawner.countSpawns());

            ironTotal += ores.get(GIBlocks.oreIron);
            check("sector " + sector.id + " has a player core", core);
            check("sector " + sector.id + " has copper and lead", ores.get(Blocks.oreCopper) > 50 && ores.get(Blocks.oreLead) > 50);
            check("sector " + sector.id + " has enemies", enemy);
            check("sector " + sector.id + " has room to build", open > world.width() * world.height() / 5);
            if(sector.hasEnemyBase()) check("sector " + sector.id + " enemy base has cores", state.rules.waveTeam.cores().any() && state.rules.attackMode);
        }

        check("Halden generates iron ore", ironTotal > 50);
        check("iron ore is listed in Halden's resources", HaldenGenerator.resources().contains(GIItems.ironOre));

        //climate effects on the realism systems
        state.rules.sector = polar;
        float polarHeat = MachineHeat.heatOf(runKiln(polar)), polarVisc = Climate.viscosityScale(), polarStart = Climate.startupScale(), polarLoss = Climate.lineLossScale();
        float equatorHeat = MachineHeat.heatOf(runKiln(equator)), equatorVisc = Climate.viscosityScale(), equatorLoss = Climate.lineLossScale();
        float baseHeat = MachineHeat.heatOf(runKiln(null));
        System.out.printf("kiln heat after 3 min: polar %.0f%%, Serpulo %.0f%%, equator %.0f%%%n", polarHeat * 100, baseHeat * 100, equatorHeat * 100);
        System.out.printf("polar: pipe drop x%.2f, startup x%.2f, line loss x%.2f; equator: pipe drop x%.2f, line loss x%.2f%n", polarVisc, polarStart, polarLoss, equatorVisc, equatorLoss);
        check("machines run cooler at the poles", polarHeat < baseHeat - 0.1f);
        check("machines run hotter at the equator", equatorHeat > baseHeat + 0.04f);
        check("cold thickens water in pipes", polarVisc > 1.2f);
        check("cold slows generator startup", polarStart > 1.2f);
        check("heat raises line loss", equatorLoss > 1.03f && polarLoss < 1f);

        Core.settings.put(Climate.settingEnabled, false);
        state.rules.sector = polar;
        check("toggle off makes climate neutral", Climate.temperature() == Climate.reference && Climate.viscosityScale() == 1f);
        Core.settings.put(Climate.settingEnabled, true);
    }

    /** Runs a kiln flat out for 3 minutes with no coolant in the given sector's climate. */
    static Building runKiln(Sector sector){
        logic.reset();
        state.rules = new Rules();
        state.rules.sector = sector;
        Time.setDeltaProvider(() -> 1f);
        Tiles tiles = world.resize(12, 12);
        world.beginMapLoad();
        tiles.fill();
        world.endMapLoad();
        tiles.get(6, 6).setBlock(Blocks.kiln, Team.sharded);
        var b = tiles.get(6, 6).build;
        ((PowerSource)Blocks.powerSource).powerProduction = 100f;
        tiles.get(2, 6).setBlock(Blocks.powerSource, Team.sharded);
        tiles.get(2, 6).build.configureAny(b.pos());

        var graph = b.power.graph;
        for(int i = 0; i < 3 * 60 * 60; i++){
            Time.update();
            for(var s : Blocks.kiln.consumers){
                if(s instanceof mindustry.world.consumers.ConsumeItems items){
                    for(var st : items.items) if(b.items.get(st.item) < 20) b.items.add(st.item, 20);
                }
            }
            b.items.remove(Items.metaglass, b.items.get(Items.metaglass));
            for(var other : graph.all) other.update();
            graph.update();
        }
        return b;
    }

    static void check(String name, boolean ok){
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if(!ok) failures++;
    }
}
