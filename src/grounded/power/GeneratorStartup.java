package grounded.power;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.io.*;
import grounded.heat.MachineHeat.*;
import grounded.planet.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;

/**
 * Generator spin-up.
 *
 * Fuel-burning generators no longer deliver full power the moment fuel arrives: output ramps up over a startup time
 * (turbines take longer than a combustion generator), and spins down twice as fast when fuel runs out. A generator
 * that sputters on and off therefore never reaches full output, which feeds into brownouts on the grid.
 */
public class GeneratorStartup{
    public static final String settingEnabled = "gi-startup", settingScale = "gi-startup-scale";
    public static final int defaultScale = 100;

    /** Startup time in seconds for each vanilla generator. Anything not listed starts instantly. */
    public static final ObjectFloatMap<Block> startupSeconds = new ObjectFloatMap<>();

    public static void apply(){
        startupSeconds.put(Blocks.combustionGenerator, 3f);
        startupSeconds.put(Blocks.steamGenerator, 10f);
        startupSeconds.put(Blocks.differentialGenerator, 15f);
        startupSeconds.put(Blocks.chemicalCombustionChamber, 5f);
        startupSeconds.put(Blocks.pyrolysisGenerator, 10f);
        //the RTG has no moving parts, so it stays instant

        for(var e : startupSeconds.entries()){
            if(e.key instanceof ConsumeGenerator gen){
                gen.buildType = () -> new SpinBuild(gen);
            }
        }
    }

    public static void addBars(){
        for(var e : startupSeconds.entries()){
            if(e.key instanceof ConsumeGenerator gen){
                gen.addBar("gi-startup", (SpinBuild build) -> new Bar(
                    () -> Core.bundle.format("bar.gi-startup", (int)(build.spin * 100f)),
                    () -> Pal.lightOrange,
                    () -> build.spin
                ));
            }
        }
    }

    public static boolean enabled(){
        return Core.settings.getBool(settingEnabled, true);
    }

    /** Advances spin toward running or stopped. Exposed for checks. */
    public static float nextSpin(Block block, float spin, boolean running, float delta){
        float seconds = startupSeconds.get(block, 0f) * Core.settings.getInt(settingScale, defaultScale) / 100f * Climate.startupScale();
        if(seconds <= 0f) return running ? 1f : 0f;

        float step = delta / (seconds * 60f);
        return Mathf.clamp(running ? spin + step : spin - step * 2f);
    }

    public static class SpinBuild extends ConsumeGenerator.ConsumeGeneratorBuild implements Heated{
        /** 0 = stopped, 1 = full speed. */
        public float spin;
        public final HeatModule heat = new HeatModule();

        public SpinBuild(ConsumeGenerator gen){
            gen.super();
        }

        @Override
        public void updateTile(){
            super.updateTile();
            spin = nextSpin(block, spin, productionEfficiency > 0f, delta());
            heat.update(this, productionEfficiency * (GeneratorStartup.enabled() ? spin : 1f));
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
        public float getPowerProduction(){
            return super.getPowerProduction() * (GeneratorStartup.enabled() ? spin : 1f);
        }

        @Override
        public float warmup(){
            return GeneratorStartup.enabled() ? Math.min(super.warmup(), spin) : super.warmup();
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            //spin isn't saved; a generator that was running when saved resumes at full speed
            spin = productionEfficiency > 0f ? 1f : 0f;
        }
    }
}
