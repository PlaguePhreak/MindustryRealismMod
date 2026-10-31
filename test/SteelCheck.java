import arc.*;
import arc.backend.headless.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import grounded.combat.*;
import grounded.content.*;
import grounded.heat.*;
import grounded.liquid.*;
import grounded.power.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.sandbox.*;
import mindustry.world.consumers.*;

import static grounded.content.GIItems.*;
import static grounded.content.GIBlocks.*;
import static mindustry.Vars.*;

/**
 * Headless checks of the steelmaking chain against the real game code, loading the mod's content in the same order as
 * the mod does. Run with the Mindustry server jar on the classpath: java -cp server-release.jar:build/check SteelCheck
 */
public class SteelCheck{
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
                GIItems.load();
                GIBlocks.load();
                LineLoss.apply();
                GeneratorStartup.apply();
                MachineHeat.apply();
                PipePressure.apply();
                TurretRealism.apply();
                add(logic = new Logic());
                content.init();
                LineLoss.addBars();
                MachineHeat.addBars();
                GIBlocks.addResearch();
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
        check("iron ore is minable by a mechanical drill", ((Drill)Blocks.mechanicalDrill).tier >= ironOre.hardness && oreIron.itemDrop == ironOre);

        //each step of the chain, run for a minute with inputs kept topped up
        float conc = produce(magneticSeparator, ironConcentrate, 60, null);
        float ore = produce(oreConcentrator, ironConcentrate, 60, Liquids.water);
        float cokeRate = produce(cokeOven, coke, 60, null);
        float sinterRate = produce(sinterPlant, sinter, 60, null);
        float pig = produce(blastFurnace, pigIron, 60, Liquids.water);
        float steelRate = produce(arcFurnace, steel, 60, null);
        System.out.printf("per minute: concentrate %.0f (sand) / %.0f (ore), coke %.0f, sinter %.0f, pig iron %.0f (water-cooled), steel %.0f%n",
            conc, ore, cokeRate, sinterRate, pig, steelRate);
        check("magnetic separator makes concentrate from sand", conc >= 55f);
        check("ore concentrator makes concentrate from ore", ore >= 150f);
        check("coke oven makes coke", cokeRate >= 35f);
        check("sinter plant makes sinter", sinterRate >= 80f);
        check("blast furnace makes pig iron", pig >= 55f);
        check("arc furnace makes steel", steelRate >= 70f);

        //the blast furnace stops when its slag has nowhere to go
        var furnace = placePowered(blastFurnace);
        run(furnace, 60 * 60, null, false);
        int stuck = furnace.items.get(pigIron);
        System.out.printf("blast furnace with slag never drained: slag %.1f/%.0f, pig iron made %d%n", furnace.liquids.get(Liquids.slag), blastFurnace.liquidCapacity, stuck);
        check("undrained slag stops the blast furnace", stuck < 20 && furnace.liquids.get(Liquids.slag) > blastFurnace.liquidCapacity * 0.9f);

        //heat: furnaces are tracked and coolant matters
        var hot = placePowered(arcFurnace);
        run(hot, 3 * 60 * 60, null, true);
        var cool = placePowered(arcFurnace);
        run(cool, 3 * 60 * 60, Liquids.water, true);
        System.out.printf("arc furnace after 3 min: uncooled heat %.0f%%, water-cooled %.0f%%%n", MachineHeat.heatOf(hot) * 100, MachineHeat.heatOf(cool) * 100);
        check("arc furnace overheats without coolant", MachineHeat.heatOf(hot) > 1f);
        check("water keeps the arc furnace in range", MachineHeat.heatOf(cool) < 0.7f);

        //transmission towers lose a quarter as much per tile
        check("transmission tower is a low-loss power node", LineLoss.isLossNode(transmissionTower) && LineLoss.lossFactor.get(transmissionTower, 1f) == 0.25f);

        check("steel wall is tougher than titanium", steelWall.health > Blocks.titaniumWall.health && steelWall.armor > Blocks.titaniumWall.armor);

        var missing = new Seq<String>();
        for(var c : new mindustry.ctype.UnlockableContent[]{magneticSeparator, oreConcentrator, cokeOven, sinterPlant, blastFurnace, arcFurnace, steelWall, steelWallLarge, transmissionTower, ironConcentrate, ironOre, coke, sinter, pigIron, steel}){
            if(!TechTree.all.contains(n -> n.content == c)) missing.add(c.name);
        }
        check("everything is in the tech tree" + (missing.isEmpty() ? "" : " (missing " + missing + ")"), missing.isEmpty());
    }

    static Building placePowered(Block block){
        logic.reset();
        state.rules = new Rules();
        Time.setDeltaProvider(() -> 1f);
        Tiles tiles = world.resize(14, 14);
        world.beginMapLoad();
        tiles.fill();
        world.endMapLoad();
        tiles.get(7, 7).setBlock(block, Team.sharded);
        var b = tiles.get(7, 7).build;
        if(block.consPower != null){
            ((PowerSource)Blocks.powerSource).powerProduction = 100f;
            tiles.get(1, 7).setBlock(Blocks.powerSource, Team.sharded);
            tiles.get(1, 7).build.configureAny(b.pos());
        }
        return b;
    }

    /** Output per minute over `seconds`, with inputs kept full and outputs (items and liquids) emptied. */
    static float produce(Block block, Item output, int seconds, @Nullable Liquid liquid){
        var b = placePowered(block);
        int made = run(b, seconds * 60, liquid, true);
        return made / (seconds / 60f);
    }

    static int run(Building b, int ticks, @Nullable Liquid liquid, boolean drain){
        var crafter = (GenericCrafter)b.block;
        int made = 0;
        var graph = b.power != null ? b.power.graph : null;
        for(int i = 0; i < ticks; i++){
            Time.update();
            for(var cons : crafter.consumers){
                if(cons instanceof ConsumeItems items){
                    for(var s : items.items) if(b.items.get(s.item) < s.amount * 2) b.items.add(s.item, s.amount * 2);
                }
            }
            if(liquid != null && b.liquids.get(liquid) < 10f) b.liquids.add(liquid, 10f);
            if(drain){
                for(var out : crafter.outputItems){
                    if(b.items.get(out.item) > 0 && out == crafter.outputItems[0]) made += b.items.get(out.item);
                    b.items.remove(out.item, b.items.get(out.item));
                }
                if(crafter.outputLiquids != null){
                    for(var out : crafter.outputLiquids) b.liquids.remove(out.liquid, b.liquids.get(out.liquid));
                }
            }
            if(graph != null){
                for(var other : graph.all) other.update();
                graph.update();
            }else{
                b.update();
            }
        }
        if(drain) made += b.items.get(crafter.outputItems[0].item);
        return made;
    }

    static void check(String name, boolean ok){
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if(!ok) failures++;
    }
}
