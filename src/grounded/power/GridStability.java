package grounded.power;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.blocks.power.*;

import static mindustry.Vars.*;

/**
 * Brownouts and load shedding.
 *
 * In vanilla, an overloaded grid quietly runs every machine at reduced speed. Here, once a grid's supply stays below a
 * threshold of its demand for a moment, breakers trip: consumers are cut off, biggest loads first and defenses last,
 * until the rest of the grid is fully powered again. Tripped machines restart after a few seconds, staggered, and trip
 * again if the grid still can't carry them. Batteries cover the gap, as they would in a real grid.
 */
public class GridStability{
    public static final String settingEnabled = "gi-brownout", settingThreshold = "gi-brownout-threshold";
    /** Supply as a % of demand below which breakers trip. */
    public static final int defaultThreshold = 75;
    /** Ticks a grid must stay below the threshold before it trips. */
    public static float tripDelay = 30f;
    /** Ticks a tripped consumer stays off, plus up to {@link #restartJitter} more so they don't all return at once. */
    public static float restartDelay = 4f * 60f, restartJitter = 3f * 60f;
    /** Shed until demand is at most this fraction of supply. */
    public static float shedTarget = 0.97f;

    /** Tripped buildings, mapped to the time they restart. */
    static final ObjectFloatMap<Building> tripped = new ObjectFloatMap<>();
    /** Ticks each grid has spent below the threshold, by graph id. */
    static final IntFloatMap underTime = new IntFloatMap();
    static final Seq<Building> candidates = new Seq<>();

    public static void init(){
        Events.run(Trigger.update, () -> {
            if(state.isPlaying() && !net.client()) update();
        });

        //enabled is saved with each building, so never write a tripped breaker into a save; it re-trips next tick if needed
        Events.on(SaveWriteEvent.class, e -> restoreAll());
        Events.on(WorldLoadEvent.class, e -> {
            tripped.clear();
            underTime.clear();
        });
    }

    public static boolean enabled(){
        return Core.settings.getBool(settingEnabled, true);
    }

    static float threshold(){
        return Core.settings.getInt(settingThreshold, defaultThreshold) / 100f;
    }

    public static boolean isTripped(Building build){
        return tripped.containsKey(build);
    }

    public static void update(){
        restoreDue();

        if(!enabled()){
            underTime.clear();
            return;
        }

        float threshold = threshold();
        for(var updater : Groups.powerGraph){
            var graph = updater.graph();
            if(graph == null) continue;

            float needed = graph.getLastPowerNeeded(), produced = graph.getLastPowerProduced();
            if(needed <= 0f || produced / needed >= threshold){
                underTime.remove(graph.getID(), 0f);
                continue;
            }

            float time = underTime.increment(graph.getID(), 0f, Time.delta);
            if(time < tripDelay) continue;

            underTime.remove(graph.getID(), 0f);
            shed(graph, needed, produced);
        }
    }

    /** Trips consumers on this grid until its demand fits its supply. */
    static void shed(PowerGraph graph, float needed, float produced){
        candidates.clear();
        for(var b : graph.consumers){
            if(b.enabled && b.shouldConsumePower && !b.block.consPower.buffered && !LineLoss.isLossNode(b.block)){
                candidates.add(b);
            }
        }

        candidates.sort(Structs.comps(
            Structs.comparingInt(GridStability::priority),
            Structs.comparingFloat(b -> -request(b))
        ));

        float target = produced * shedTarget;
        for(var b : candidates){
            if(needed <= target) break;
            needed -= request(b);
            trip(b);
        }
        candidates.clear();
    }

    static float request(Building b){
        return b.block.consPower.requestedPower(b) * b.delta();
    }

    /** Lower sheds first. Defenses and unit production are kept running longest. */
    static int priority(Building b){
        var cat = b.block.category;
        if(cat == Category.turret || cat == Category.effect) return 2;
        if(cat == Category.units) return 1;
        return 0;
    }

    static void trip(Building b){
        b.enabled = false;
        tripped.put(b, Time.time + restartDelay + Mathf.random(restartJitter));
        if(!headless) Fx.smoke.at(b.x, b.y);
    }

    static void restoreDue(){
        if(tripped.isEmpty()) return;

        var it = tripped.entries().iterator();
        while(it.hasNext()){
            var e = it.next();
            if(!e.key.isValid()){
                it.remove();
            }else if(Time.time >= e.value){
                e.key.enabled = true;
                it.remove();
            }
        }
    }

    static void restoreAll(){
        for(var e : tripped.entries()){
            if(e.key.isValid()) e.key.enabled = true;
        }
        tripped.clear();
    }
}
