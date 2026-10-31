import arc.*;
import arc.backend.headless.*;
import arc.files.*;
import arc.util.*;
import grounded.heat.*;
import grounded.liquid.*;
import grounded.power.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.*;
import mindustry.world.*;
import mindustry.world.blocks.sandbox.*;

import static mindustry.Vars.*;

/**
 * Headless checks of pipe pressure against the real game code. Run with the Mindustry server jar on the classpath:
 * java -cp server-release.jar:build/check PressureCheck
 */
public class PressureCheck{
    static int failures = 0;

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
                LineLoss.apply();
                GeneratorStartup.apply();
                MachineHeat.apply();
                PipePressure.apply();
                add(logic = new Logic());
                content.init();
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
        check("booster pump exists as mod content", PipePressure.boosterPump != null && PipePressure.boosterPump.name.endsWith("booster-pump"));

        float shortRun = flow(8, -1, false);
        float longRun = flow(60, -1, false);
        float boosted = flow(60, 30, true);
        float unpowered = flow(60, 30, false);
        Core.settings.put(PipePressure.settingEnabled, false);
        float off = flow(60, -1, false);
        Core.settings.put(PipePressure.settingEnabled, true);

        System.out.printf("water flow (units/s): 8 tiles %.1f, 60 tiles %.1f, 60 tiles + booster %.1f, unpowered booster %.1f, pressure off %.1f%n",
            shortRun, longRun, boosted, unpowered, off);
        check("short run flows as in vanilla", shortRun > off * 0.95f);
        check("long run is choked to about 30/s", longRun < 35f);
        check("powered booster lifts it well past a rotary pump (48/s)", boosted > 100f);
        check("unpowered booster doesn't help", unpowered < 35f);
        check("toggle off restores vanilla flow", Math.abs(off - shortRun) < shortRun * 0.1f);

        //the bar reads the conduit's pressure: full next to the source, falling along the line
        flow(30, -1, false);
        float near = PipePressure.pressureOf(world.tile(1, 4).build), far = PipePressure.pressureOf(world.tile(30, 4).build);
        System.out.printf("pressure at tile 1: %.0f%%, tile 30: %.0f%%%n", near * 100, far * 100);
        check("pressure falls 2.5% per conduit tile", Math.abs(near - 0.975f) < 0.001f && Math.abs(far - 0.25f) < 0.001f);

        //a tank in the middle hands liquid on at low pressure
        float viaTankPressure = PipePressure.sourcePressure(placeTank());
        check("tanks pass liquid on at 60% pressure", viaTankPressure == PipePressure.passiveHead);
    }

    /** Water flow per second from a liquid source through `length` conduits into a tank, optionally with a booster. */
    static float flow(int length, int boosterAt, boolean powered){
        logic.reset();
        state.rules = new Rules();
        Time.setDeltaProvider(() -> 1f);
        Tiles tiles = world.resize(length + 10, 9);
        world.beginMapLoad();
        tiles.fill();
        world.endMapLoad();

        int y = 4;
        tiles.get(0, y).setBlock(Blocks.liquidSource, Team.sharded);
        tiles.get(0, y).build.configureAny(Liquids.water);
        for(int x = 1; x <= length; x++){
            tiles.get(x, y).setBlock(x == boosterAt ? PipePressure.boosterPump : Blocks.conduit, Team.sharded, 0);
        }
        tiles.get(length + 2, y).setBlock(Blocks.liquidTank, Team.sharded);
        var tank = tiles.get(length + 2, y).build;

        if(boosterAt > 0 && powered){
            ((PowerSource)Blocks.powerSource).powerProduction = 10f;
            tiles.get(boosterAt, y + 3).setBlock(Blocks.powerSource, Team.sharded);
            tiles.get(boosterAt, y + 3).build.configureAny(tiles.get(boosterAt, y).build.pos());
        }

        var builds = new arc.struct.Seq<Building>();
        for(var t : tiles){
            if(t.build != null && t.isCenter() && !builds.contains(t.build)) builds.add(t.build);
        }

        //warm up until the line is full, then measure
        int warm = 1200, measure = 600;
        //the tank is drained every tick so it never fills up and backs the line up
        for(int i = 0; i < warm; i++){
            step(builds);
            tank.liquids.clear();
        }
        float total = 0f;
        for(int i = 0; i < measure; i++){
            step(builds);
            total += tank.liquids.get(Liquids.water);
            tank.liquids.clear();
        }
        return total / (measure / 60f);
    }

    static Building placeTank(){
        world.tile(4, 1).setBlock(Blocks.liquidTank, Team.sharded);
        return world.tile(4, 1).build;
    }

    static void step(arc.struct.Seq<Building> builds){
        Time.update();
        for(var b : builds){
            b.update();
            if(b.power != null && b.power.graph != null && b.block == Blocks.powerSource) b.power.graph.update();
        }
    }

    static void check(String name, boolean ok){
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if(!ok) failures++;
    }
}
