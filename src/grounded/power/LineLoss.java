package grounded.power;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import grounded.planet.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.power.PowerNode.*;

import static mindustry.Vars.*;

/**
 * Transmission losses on power laser links.
 *
 * Every vanilla power node becomes a small dynamic power consumer. Its demand is the power flowing into its grid,
 * times a loss rate per tile of laser it carries, so long laser chains waste power and generators placed near
 * their consumers are rewarded. Total loss per grid is capped so huge grids still work.
 */
public class LineLoss{
    public static final String settingEnabled = "gi-lineloss", settingRate = "gi-lineloss-rate", settingCap = "gi-lineloss-cap";
    /** Loss per tile of laser, in hundredths of a percent (15 = 0.15%). */
    public static final int defaultRate = 15;
    /** Maximum loss per grid, in percent. */
    public static final int defaultCap = 40;

    /** Per-grid cache of total laser length, recomputed at most once per tick. */
    private static final IntMap<GridCache> cache = new IntMap<>();

    public static void apply(){
        for(var block : content.blocks()){
            if(isLossNode(block)){
                block.consumesPower = true;
                block.consumePowerDynamic(LineLoss::requested);
            }
        }
    }

    public static void addBars(){
        for(var block : content.blocks()){
            if(isLossNode(block)){
                block.addBar("gi-lineloss", (PowerNodeBuild build) -> new Bar(
                    () -> Core.bundle.format("bar.gi-lineloss", Strings.autoFixed(gridLossFraction(build) * 100f, 1)),
                    () -> Pal.remove,
                    () -> gridLossFraction(build)
                ));
            }
        }
    }

    /**
     * Blocks that carry laser links and pay for them. Batteries, diodes and Erekir beam nodes are not PowerNodes;
     * the sandbox power source is a PowerNode that outputs power, and is left alone too.
     */
    public static boolean isLossNode(Block block){
        return block instanceof PowerNode && !block.outputsPower;
    }

    public static boolean enabled(){
        return Core.settings.getBool(settingEnabled, true);
    }

    static float rate(){
        return Core.settings.getInt(settingRate, defaultRate) / 10000f * Climate.lineLossScale();
    }

    static float cap(){
        return Core.settings.getInt(settingCap, defaultCap) / 100f;
    }

    /** Power this node asks for per tick: its share of the grid's transmission loss. */
    static float requested(Building build){
        if(!enabled() || build.power == null || build.power.graph == null) return 0f;

        var graph = build.power.graph;
        float throughput = Math.max(graph.getLastScaledPowerIn(), 0f);
        if(Mathf.zero(throughput)) return 0f;

        float own = laserTiles(build);
        if(Mathf.zero(own)) return 0f;

        float total = gridTiles(graph);
        float lossFraction = Math.min(total * rate(), cap());
        return throughput * lossFraction * (own / total);
    }

    /** Fraction of the grid's incoming power lost to transmission, for display. */
    public static float gridLossFraction(Building build){
        if(!enabled() || build.power == null || build.power.graph == null) return 0f;
        return Math.min(gridTiles(build.power.graph) * rate(), cap());
    }

    /**
     * Laser length carried by this node, in tiles. Links are stored on both ends, so a node-to-node link counts
     * half on each node; a link from a node to any other block counts fully here.
     */
    static float laserTiles(Building build){
        float sum = 0f;
        var links = build.power.links;
        for(int i = 0; i < links.size; i++){
            var other = world.build(links.items[i]);
            if(other == null) continue;
            float len = build.dst(other) / tilesize;
            sum += isLossNode(other.block) ? len / 2f : len;
        }
        return sum;
    }

    static float gridTiles(PowerGraph graph){
        var entry = cache.get(graph.getID());
        if(entry == null){
            cache.put(graph.getID(), entry = new GridCache());
        }

        if(entry.time != Time.time){
            entry.time = Time.time;
            float total = 0f;
            var all = graph.all;
            for(int i = 0; i < all.size; i++){
                var b = all.items[i];
                if(isLossNode(b.block)) total += laserTiles(b);
            }
            entry.tiles = total;
        }
        return entry.tiles;
    }

    static class GridCache{
        float time = -1f, tiles;
    }
}
