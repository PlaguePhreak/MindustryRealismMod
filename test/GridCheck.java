import arc.*;
import arc.backend.headless.*;
import arc.files.*;
import arc.util.*;
import grounded.power.*;
import grounded.power.GeneratorStartup.*;
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
 * Headless checks of brownouts and generator startup against the real game code. Run with the Mindustry server jar
 * on the classpath: java -cp server-release.jar:build/check GridCheck
 */
public class GridCheck{
    static int failures = 0;
    static Block smallLoad, turretLoad;

    public static void main(String[] args) throws Exception{
        boolean[] ready = {false};
        Throwable[] error = {null};

        new HeadlessApplication(new ApplicationCore(){
            @Override
            public void setup(){
                Core.settings.setDataDirectory(new Fi(System.getProperty("java.io.tmpdir")).child("gi-test-data"));
                headless = true;
                net = new Net(null);
                tree = new FileTree();
                Vars.init();
                world = new World();
                content.createBaseContent();

                //simple loads with known demand (per tick)
                smallLoad = new PowerBlock("gi-test-load"){{
                    buildType = Building::create;
                    category = Category.crafting;
                    consumePower(0.2f);
                }};
                turretLoad = new PowerBlock("gi-test-turret"){{
                    buildType = Building::create;
                    category = Category.turret;
                    consumePower(0.4f);
                }};

                LineLoss.apply();
                GeneratorStartup.apply();
                add(logic = new Logic());
                content.init();
                LineLoss.addBars();
                GeneratorStartup.addBars();
                GridStability.init();
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
                startup();
                brownout();
            }catch(Throwable t){
                t.printStackTrace();
                failures++;
            }
            System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
            System.exit(failures == 0 ? 0 : 1);
        });
    }

    static Tiles map(int w, int h){
        logic.reset();
        state.rules = new Rules();
        Time.setDeltaProvider(() -> 1f);
        Tiles tiles = world.resize(w, h);
        world.beginMapLoad();
        tiles.fill();
        world.endMapLoad();
        return tiles;
    }

    static void startup(){
        var tiles = map(10, 10);
        tiles.get(4, 4).setBlock(Blocks.combustionGenerator, Team.sharded);
        var gen = tiles.get(4, 4).build;
        check("combustion generator uses the spin-up build", gen instanceof SpinBuild);
        var spin = (SpinBuild)gen;
        float full = ((PowerGenerator)Blocks.combustionGenerator).powerProduction;

        gen.items.add(Items.coal, 10);
        run(gen, 90);
        float half = gen.getPowerProduction() / full;
        run(gen, 200);
        float later = gen.getPowerProduction() / full;
        System.out.printf("combustion output after 1.5s: %.0f%%, after 4.8s: %.0f%%%n", half * 100, later * 100);
        check("output ramps up over the 3s startup", half > 0.4f && half < 0.6f);
        check("output reaches full power", later > 0.99f);

        //save and reload a running generator: it should come back at full speed, not restart
        try{
            var bytes = new java.io.ByteArrayOutputStream();
            gen.writeAll(new arc.util.io.Writes(new java.io.DataOutputStream(bytes)));
            tiles.get(7, 7).setBlock(Blocks.combustionGenerator, Team.sharded);
            var copy = (SpinBuild)tiles.get(7, 7).build;
            copy.readAll(new arc.util.io.Reads(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))), gen.version());
            check("running generator reloads at full speed", copy.spin > 0.99f);
        }catch(Exception e){
            throw new RuntimeException(e);
        }

        gen.items.clear();
        run(gen, 200);
        check("spins down when fuel runs out", spin.spin < 0.01f);

        Core.settings.put(GeneratorStartup.settingEnabled, false);
        gen.items.add(Items.coal, 10);
        run(gen, 5);
        check("toggle off gives instant full power", gen.getPowerProduction() / full > 0.99f);
        Core.settings.put(GeneratorStartup.settingEnabled, true);
    }

    static void run(Building b, int ticks){
        for(int i = 0; i < ticks; i++){
            Time.update();
            b.update();
        }
    }

    static void brownout(){
        var tiles = map(20, 20);
        int cx = 10, cy = 10;
        ((PowerSource)Blocks.powerSource).powerProduction = 1f;
        tiles.get(cx, cy).setBlock(Blocks.powerNode, Team.sharded);
        var node = tiles.get(cx, cy).build;

        tiles.get(cx - 3, cy).setBlock(Blocks.powerSource, Team.sharded);
        node.configureAny(tiles.get(cx - 3, cy).build.pos());

        //5 x 0.2 factory load + 0.4 turret = 1.4 demand on 1.0 supply: 71%, below the 75% trip point
        Building[] smalls = new Building[5];
        int[][] spots = {{cx + 3, cy}, {cx, cy + 3}, {cx, cy - 3}, {cx + 3, cy + 3}, {cx + 3, cy - 3}};
        for(int i = 0; i < 5; i++){
            tiles.get(spots[i][0], spots[i][1]).setBlock(smallLoad, Team.sharded);
            smalls[i] = tiles.get(spots[i][0], spots[i][1]).build;
            node.configureAny(smalls[i].pos());
        }
        tiles.get(cx - 3, cy + 3).setBlock(turretLoad, Team.sharded);
        var turret = tiles.get(cx - 3, cy + 3).build;
        node.configureAny(turret.pos());

        var graph = node.power.graph;
        check("everything on one grid", graph.consumers.size == 7);

        //no line loss here, so the numbers stay exact
        Core.settings.put(LineLoss.settingEnabled, false);

        int tripTick = -1;
        for(int i = 0; i < 120; i++){
            tick(graph);
            if(tripTick < 0 && !smalls[0].enabled | !smalls[1].enabled | !smalls[2].enabled) tripTick = i;
        }

        int off = 0;
        for(var b : smalls) if(!b.enabled) off++;
        System.out.printf("tripped after %d ticks; %d of 5 factory loads off; turret %s; satisfaction now %.2f%n",
            tripTick, off, turret.enabled ? "on" : "off", graph.getSatisfaction());
        check("grid trips after the delay, not instantly", tripTick >= 29 && tripTick < 40);
        check("sheds just enough factory loads", off == 3);
        check("keeps the turret running", turret.enabled);
        check("remaining loads are fully powered", graph.getSatisfaction() > 0.999f);

        //tripped loads come back within the restart window, then trip again because the grid is still short
        boolean allBack = false;
        for(int i = 0; i < 7 * 60 + 10; i++){
            tick(graph);
            if(GridStability.isTripped(smalls[0]) == false && smalls[0].enabled) allBack = true;
        }
        check("tripped loads restart", allBack);

        //a save never captures a tripped breaker
        for(int i = 0; i < 60; i++) tick(graph);
        Events.fire(new EventType.SaveWriteEvent());
        boolean anyOff = false;
        for(var b : smalls) anyOff |= !b.enabled;
        check("saving restores tripped loads", !anyOff);

        //with enough supply nothing trips
        ((PowerSource)Blocks.powerSource).powerProduction = 2f;
        for(int i = 0; i < 300; i++) tick(graph);
        off = 0;
        for(var b : smalls) if(!b.enabled) off++;
        check("no trips with enough supply", off == 0);

        Core.settings.put(LineLoss.settingEnabled, true);
    }

    static void tick(PowerGraph graph){
        Time.update();
        for(var b : graph.all.copy()) b.update();
        graph.update();
        GridStability.update();
    }

    static void check(String name, boolean ok){
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if(!ok) failures++;
    }
}
