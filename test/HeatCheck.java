import arc.*;
import arc.backend.headless.*;
import arc.files.*;
import arc.util.*;
import grounded.heat.*;
import grounded.power.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.sandbox.*;

import static mindustry.Vars.*;

/**
 * Headless checks of machine heat against the real game code. Run with the Mindustry server jar on the classpath:
 * java -cp server-release.jar:build/check HeatCheck
 */
public class HeatCheck{
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
                add(logic = new Logic());
                content.init();
                LineLoss.addBars();
                GeneratorStartup.addBars();
                MachineHeat.addBars();
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
        check("surge smelter tracks heat", newSmelter() instanceof MachineHeat.Heated);
        check("surge smelter accepts water", newSmelter().acceptLiquid(null, Liquids.water));
        check("combustion generator accepts water and still spins up",
            place(Blocks.combustionGenerator).acceptLiquid(null, Liquids.water) && place(Blocks.combustionGenerator) instanceof GeneratorStartup.SpinBuild);

        //3 minutes flat out with no cooling: settles above the limit and throttles, but no damage
        var hot = newSmelter();
        simulate(hot, 3 * 60 * 60, null);
        float hotHeat = MachineHeat.heatOf(hot);
        System.out.printf("surge smelter, no coolant: heat %.0f%%, output %.0f%%, health %.0f%%%n", hotHeat * 100, MachineHeat.throttle(hotHeat) * 100, hot.healthf() * 100);
        check("uncooled surge smelter overheats and throttles", hotHeat > 1.05f && MachineHeat.throttle(hotHeat) < 0.95f);
        check("throttling keeps it below damage", hot.healthf() > 0.999f);

        //same with water piped in
        var cool = newSmelter();
        simulate(cool, 3 * 60 * 60, Liquids.water);
        float coolHeat = MachineHeat.heatOf(cool);
        System.out.printf("surge smelter, water cooled: heat %.0f%%%n", coolHeat * 100);
        check("water keeps it well under the limit", coolHeat < 0.6f);
        check("cooled smelter runs at full speed", MachineHeat.throttle(coolHeat) == 1f);

        //an extreme heat load does damage
        float old = MachineHeat.heatLoad.get(Blocks.surgeSmelter, 0f);
        MachineHeat.heatLoad.put(Blocks.surgeSmelter, 3f);
        var melting = newSmelter();
        simulate(melting, 2 * 60 * 60, null);
        MachineHeat.heatLoad.put(Blocks.surgeSmelter, old);
        System.out.printf("surge smelter at 3x heat load: heat %.0f%%, health %.0f%%%n", MachineHeat.heatOf(melting) * 100, melting.healthf() * 100);
        check("severe overheating damages the machine", melting.healthf() < 0.95f);

        Core.settings.put(MachineHeat.settingEnabled, false);
        var off = newSmelter();
        simulate(off, 60 * 60, null);
        Core.settings.put(MachineHeat.settingEnabled, true);
        check("toggle off disables heat", MachineHeat.heatOf(off) == 0f);
    }

    static Tiles tiles;

    static Building place(Block block){
        logic.reset();
        state.rules = new Rules();
        Time.setDeltaProvider(() -> 1f);
        tiles = world.resize(12, 12);
        world.beginMapLoad();
        tiles.fill();
        world.endMapLoad();
        tiles.get(6, 6).setBlock(block, Team.sharded);
        return tiles.get(6, 6).build;
    }

    static Building newSmelter(){
        var b = place(Blocks.surgeSmelter);
        ((PowerSource)Blocks.powerSource).powerProduction = 100f;
        tiles.get(2, 6).setBlock(Blocks.powerSource, Team.sharded);
        tiles.get(2, 6).build.configureAny(b.pos());
        return b;
    }

    /** Runs a machine with its inputs kept full and its output emptied. */
    static void simulate(Building b, int ticks, @Nullable mindustry.type.Liquid coolant){
        var graph = b.power.graph;
        for(int i = 0; i < ticks; i++){
            Time.update();
            for(var stack : ((mindustry.world.blocks.production.GenericCrafter)b.block).consumers){
                if(stack instanceof mindustry.world.consumers.ConsumeItems items){
                    for(var s : items.items) if(b.items.get(s.item) < 20) b.items.add(s.item, 20);
                }
            }
            b.items.remove(Items.surgeAlloy, b.items.get(Items.surgeAlloy));
            if(coolant != null && b.liquids.get(coolant) < 5f) b.liquids.add(coolant, 5f);
            for(var other : graph.all) other.update();
            graph.update();
        }
    }

    static void check(String name, boolean ok){
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if(!ok) failures++;
    }
}
