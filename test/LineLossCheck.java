import arc.*;
import arc.backend.headless.*;
import arc.files.*;
import arc.util.*;
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
 * Headless check of power line loss against the real game code. Run with the Mindustry server jar on the classpath:
 * java -cp server-release.jar:build/classes LineLossCheck
 */
public class LineLossCheck{
    static int failures = 0;
    static float lastVoidSatisfaction;

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
                add(logic = new Logic());
                content.init();
                LineLoss.addBars();
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
        Time.setDeltaProvider(() -> 1f);
        state.rules = new Rules();

        //sandbox power source/void tuned to known numbers (per tick)
        ((PowerSource)Blocks.powerSource).powerProduction = 1f;
        Blocks.powerVoid.consPower.usage = 0.9f;

        check("power nodes consume power", Blocks.powerNode.consumesPower && Blocks.powerNode.consPower != null);
        check("power source untouched", !Blocks.powerSource.consumesPower);

        //short grid: source -> node -> void, everything within a few tiles
        float shortLoss = grid(3);
        //long grid: same, but a chain of nodes spanning ~60 tiles
        float longLoss = grid(60);

        System.out.printf("short grid loss %.2f%%, long grid loss %.2f%%%n", shortLoss * 100, longLoss * 100);
        check("short grid loses little power", shortLoss > 0f && shortLoss < 0.03f);
        check("long grid loses more power than short", longLoss > shortLoss * 5f);

        Core.settings.put(LineLoss.settingEnabled, false);
        float disabled = grid(60);
        Core.settings.put(LineLoss.settingEnabled, true);
        check("toggle off removes loss", disabled == 0f);

        //a grid with 5% headroom: fine over a short line, short of power over a long one
        Blocks.powerVoid.consPower.usage = 0.95f;
        grid(3);
        float shortSatisfaction = lastVoidSatisfaction;
        grid(60);
        float longSatisfaction = lastVoidSatisfaction;
        Blocks.powerVoid.consPower.usage = 0.9f;
        check("short line still fully powers the load", shortSatisfaction > 0.999f);
        check("long line browns out the same load", longSatisfaction < 0.97f);

        Core.settings.put(LineLoss.settingCap, 5);
        float capped = grid(60);
        Core.settings.remove(LineLoss.settingCap);
        System.out.printf("capped loss %.2f%%%n", capped * 100);
        check("cap limits loss", capped <= 0.05f + 0.001f);
    }

    /** Builds source -> node chain over `span` tiles -> void, runs it, and returns the fraction of power lost. */
    static float grid(int span){
        logic.reset();
        Tiles tiles = world.resize(span + 10, 5);
        world.beginMapLoad();
        tiles.fill();
        world.endMapLoad();

        int y = 2;
        tiles.get(0, y).setBlock(Blocks.powerSource, Team.sharded);
        tiles.get(span + 4, y).setBlock(Blocks.powerVoid, Team.sharded);

        //nodes every 5 tiles (laser range 6), first node next to the source's link range
        Building prev = null;
        int first = 2;
        for(int x = first; x <= span + 2; x += 5){
            tiles.get(x, y).setBlock(Blocks.powerNode, Team.sharded);
            var node = tiles.get(x, y).build;
            if(prev == null){
                node.configureAny(tiles.get(0, y).build.pos());
            }else{
                node.configureAny(prev.pos());
            }
            prev = node;
        }
        prev.configureAny(tiles.get(span + 4, y).build.pos());

        var graph = prev.power.graph;
        check("chain forms one grid (span " + span + ")", graph == tiles.get(0, y).build.power.graph && graph == tiles.get(span + 4, y).build.power.graph);

        for(int i = 0; i < 120; i++){
            Time.update();
            for(var b : graph.all) b.update();
            graph.update();
        }

        float lost = 0f;
        for(var b : graph.consumers){
            if(LineLoss.isLossNode(b.block)) lost += b.block.consPower.requestedPower(b);
        }
        float in = graph.getLastScaledPowerIn();
        System.out.printf("span %d: in %.3f/tick, node loss %.4f/tick, void satisfaction %.3f, bar %.2f%%%n",
            span, in, lost, tiles.get(span + 4, y).build.power.status, LineLoss.gridLossFraction(prev) * 100);
        lastVoidSatisfaction = tiles.get(span + 4, y).build.power.status;
        return in <= 0f ? 0f : lost / in;
    }

    static void check(String name, boolean ok){
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if(!ok) failures++;
    }
}
