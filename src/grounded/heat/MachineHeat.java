package grounded.heat;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import grounded.planet.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.production.*;
import mindustry.world.consumers.*;

import static mindustry.Vars.*;

/**
 * Machine heat.
 *
 * Furnaces, smelters and fuel generators heat up while they work and shed heat to the air. Each machine has a heat
 * load: the temperature it settles at when running flat out with no cooling, where 100% is its safe limit. Above
 * 100% it throttles itself; well above it, it starts taking damage. Piping in water roughly triples cooling and
 * cryofluid gives about five and a half times, so hot machines want a coolant line, just like real plants.
 */
public class MachineHeat{
    public static final String settingEnabled = "gi-heat";

    /** Heat load per block: equilibrium heat at full output with no coolant. 1 = safe limit. */
    public static final ObjectFloatMap<Block> heatLoad = new ObjectFloatMap<>();
    /** Seconds for heat to close most of the gap to its equilibrium. */
    public static float timeConstant = 20f;
    /** Throttling starts at 1, and output bottoms out at this fraction. */
    public static float minThrottle = 0.3f;
    /** Above this heat the machine takes damage. */
    public static float damageHeat = 1.2f;
    /** Fraction of max health lost per second while above {@link #damageHeat}. */
    public static float damagePerSecond = 0.005f;
    /** Coolant drawn per tick by machines that gain a coolant input. */
    public static float coolantUse = 0.1f;

    public static void apply(){
        //Serpulo crafters
        heatLoad.put(Blocks.siliconSmelter, 1.15f);
        heatLoad.put(Blocks.kiln, 0.9f);
        heatLoad.put(Blocks.plastaniumCompressor, 0.8f);
        heatLoad.put(Blocks.phaseWeaver, 1.1f);
        heatLoad.put(Blocks.surgeSmelter, 1.4f);
        heatLoad.put(Blocks.melter, 1.2f);
        //Serpulo fuel generators; these also spin up, see GeneratorStartup
        heatLoad.put(Blocks.combustionGenerator, 0.8f);
        heatLoad.put(Blocks.steamGenerator, 1.3f);
        heatLoad.put(Blocks.differentialGenerator, 1.6f);

        for(var e : heatLoad.entries()){
            var block = e.key;

            //machines without a liquid tank get an optional coolant input; ones with a tank are cooled by whatever
            //coolant already flows through it (the steam generator's water, the differential generator's cryofluid)
            if(!block.hasLiquids){
                block.consume(new ConsumeCoolant(coolantUse)).optional(true, false);
            }

            if(block instanceof GenericCrafter crafter && !(block instanceof AttributeCrafter)){
                crafter.buildType = () -> new HeatCrafterBuild(crafter);
            }
        }
    }

    public static void addBars(){
        for(var e : heatLoad.entries()){
            e.key.addBar("gi-heat", (Building build) -> new Bar(
                () -> Core.bundle.format("bar.gi-heat", (int)(heatOf(build) * 100f)),
                () -> Tmp.c1.set(Pal.lightOrange).lerp(Pal.remove, Mathf.clamp(heatOf(build))),
                () -> Mathf.clamp(heatOf(build) / damageHeat)
            ));
        }
    }

    public static boolean enabled(){
        return Core.settings.getBool(settingEnabled, true);
    }

    public static float heatOf(Building build){
        return build instanceof Heated h ? h.heat().value : 0f;
    }

    /** Cooling multiplier from coolant currently in the machine's tank; 1 with none. */
    public static float cooling(Building build){
        if(build.liquids == null) return 1f;
        var liquid = build.liquids.current();
        if(liquid == null || !liquid.coolant || liquid.temperature > 0.5f || build.liquids.get(liquid) < 0.01f) return 1f;
        return 1f + liquid.heatCapacity * 5f;
    }

    /** Output multiplier for a machine at this heat. */
    public static float throttle(float heat){
        return heat <= 1f ? 1f : Mathf.clamp(2f - heat, minThrottle, 1f);
    }

    /** Implemented by buildings that track heat. */
    public interface Heated{
        HeatModule heat();
    }

    public static class HeatModule{
        public float value;

        /**
         * Advances heat by one update.
         * @param activity how hard the machine is working this tick, 0-1, already throttled
         */
        public void update(Building build, float activity){
            if(!enabled()){
                value = 0f;
                return;
            }

            float load = heatLoad.get(build.block, 0f);
            float rate = build.delta() / (timeConstant * 60f);
            //Newtonian cooling toward (load * activity + ambient) / cooling; warm planets add heat, cold ones remove it
            value += (load * activity + Climate.heatOffset() - value * cooling(build)) * rate;
            value = Math.max(value, 0f);

            if(value > damageHeat && !net.client()){
                build.damage(build.maxHealth * damagePerSecond * build.delta() / 60f);
                if(!headless && Mathf.chanceDelta(0.05f)){
                    Fx.fireSmoke.at(build.x + Mathf.range(build.block.size * 4f), build.y + Mathf.range(build.block.size * 4f));
                }
            }
        }

        public float throttle(){
            return enabled() ? MachineHeat.throttle(value) : 1f;
        }
    }

    public static class HeatCrafterBuild extends GenericCrafter.GenericCrafterBuild implements Heated{
        public final HeatModule heat = new HeatModule();

        public HeatCrafterBuild(GenericCrafter crafter){
            crafter.super();
        }

        @Override
        public HeatModule heat(){
            return heat;
        }

        @Override
        public float efficiencyScale(){
            return super.efficiencyScale() * heat.throttle();
        }

        @Override
        public void updateTile(){
            super.updateTile();
            heat.update(this, efficiency);
        }
    }
}
