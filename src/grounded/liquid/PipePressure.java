package grounded.liquid;

import arc.*;
import arc.math.*;
import arc.struct.*;
import grounded.planet.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.liquid.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.sandbox.*;

/**
 * Pipe pressure.
 *
 * Pumps push liquid at full pressure. Every conduit tile it passes through loses a little, and tanks, routers,
 * junctions and bridges hand it on at only {@link #passiveHead} since they aren't pumps. At or above
 * {@link #fullFlowPressure} a conduit flows as in vanilla. Below it, flow per tick is capped at a fraction of the
 * conduit's capacity, falling from {@link #highCap} to {@link #lowCap} as pressure drops to zero. For a plain conduit
 * that's 300/s down to 30/s, so a rotary pump's 48/s no longer makes it down a long, unboosted line. Long runs need
 * a booster pump partway along, just like a real pipeline.
 */
public class PipePressure{
    public static final String settingEnabled = "gi-pressure";

    /** Pressure lost per conduit tile. */
    public static final ObjectFloatMap<Block> dropPerTile = new ObjectFloatMap<>();
    /** Pressure of liquid leaving a tank, router, junction or bridge. */
    public static float passiveHead = 0.6f;
    /** Pressure at or above which a conduit runs at full throughput. */
    public static float fullFlowPressure = 0.5f;
    /** Flow cap per tick, as a fraction of the conduit's liquid capacity, just below full pressure and at zero. */
    public static float highCap = 0.25f, lowCap = 0.025f;

    public static BoosterPump boosterPump;

    public static void apply(){
        dropPerTile.put(Blocks.conduit, 0.025f);
        dropPerTile.put(Blocks.pulseConduit, 0.015f);
        dropPerTile.put(Blocks.platedConduit, 0.015f);

        for(var e : dropPerTile.entries()){
            if(e.key instanceof ArmoredConduit armored){
                armored.buildType = () -> new PressureArmoredConduitBuild(armored);
            }else if(e.key instanceof Conduit conduit){
                conduit.buildType = () -> new PressureConduitBuild(conduit);
            }
        }

        boosterPump = new BoosterPump("booster-pump"){{
            requirements(Category.liquid, ItemStack.with(Items.metaglass, 2, Items.silicon, 2, Items.titanium, 2));
        }};
    }

    public static void addBars(){
        for(var e : dropPerTile.entries()){
            e.key.addBar("gi-pressure", PipePressure::pressureBar);
        }
        boosterPump.addBar("gi-pressure", PipePressure::pressureBar);
    }

    static Bar pressureBar(Building build){
        return new Bar(
            () -> Core.bundle.format("bar.gi-pressure", (int)(pressureOf(build) * 100f)),
            () -> Pal.accent,
            () -> pressureOf(build)
        );
    }

    public static boolean enabled(){
        return Core.settings.getBool(settingEnabled, true);
    }

    /** Pressure lost per tile of this conduit, after climate. */
    public static float drop(Block block){
        return dropPerTile.get(block, 0f) * Climate.viscosityScale();
    }

    public static float pressureOf(Building build){
        return build instanceof Pressurized p ? p.pressure() : 0f;
    }

    /** Pressure of liquid coming out of this building. */
    public static float sourcePressure(Building source){
        if(source == null || !source.isValid() || !source.block.hasLiquids) return 0f;
        if(source instanceof Pressurized p) return p.pressure();
        var block = source.block;
        if(block instanceof Pump || block instanceof LiquidSource) return 1f;
        //crafters that output liquid push it out themselves; storage and routing blocks don't
        if(block.outputsLiquid && !(block instanceof LiquidBlock) && !(block instanceof LiquidBridge)) return 1f;
        return passiveHead;
    }

    /** Most liquid a block may pass on per tick at this pressure, or infinity when pressure is high enough. */
    public static float flowCap(Block block, float pressure){
        if(pressure >= fullFlowPressure) return Float.POSITIVE_INFINITY;
        return block.liquidCapacity * Mathf.lerp(lowCap, highCap, pressure / fullFlowPressure);
    }

    /** Runs a normal liquid transfer, then hands back anything above the pressure's flow cap. */
    static float limitedMove(Building self, Building next, Liquid liquid, float pressure, MoveLiquid move){
        if(next == null) return 0f;
        var dest = next.getLiquidDestination(self, liquid);
        float moved = move.get(next, liquid);
        if(moved <= 0f || !enabled()) return moved;

        float excess = moved - flowCap(self.block, pressure) * self.delta();
        if(excess > 0f && dest.liquids != null && dest.liquids.get(liquid) >= excess){
            dest.liquids.remove(liquid, excess);
            self.liquids.add(liquid, excess);
            return moved - excess;
        }
        return moved;
    }

    interface MoveLiquid{
        float get(Building next, Liquid liquid);
    }

    /** Buildings that track the pressure of the liquid in them. */
    public interface Pressurized{
        float pressure();
    }

    public static class PressureConduitBuild extends Conduit.ConduitBuild implements Pressurized{
        public float pressure;
        public Building lastSource;

        public PressureConduitBuild(Conduit conduit){
            conduit.super();
        }

        @Override
        public float pressure(){
            return enabled() ? pressure : 1f;
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount){
            super.handleLiquid(source, liquid, amount);
            if(source != this) lastSource = source;
        }

        @Override
        public void updateTile(){
            pressure = Math.max(sourcePressure(lastSource) - drop(block), 0f);
            super.updateTile();
        }

        @Override
        public float moveLiquid(Building next, Liquid liquid){
            return limitedMove(this, next, liquid, pressure(), super::moveLiquid);
        }
    }

    public static class PressureArmoredConduitBuild extends ArmoredConduit.ArmoredConduitBuild implements Pressurized{
        public float pressure;
        public Building lastSource;

        public PressureArmoredConduitBuild(ArmoredConduit conduit){
            conduit.super();
        }

        @Override
        public float pressure(){
            return enabled() ? pressure : 1f;
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount){
            super.handleLiquid(source, liquid, amount);
            if(source != this) lastSource = source;
        }

        @Override
        public void updateTile(){
            pressure = Math.max(sourcePressure(lastSource) - drop(block), 0f);
            super.updateTile();
        }

        @Override
        public float moveLiquid(Building next, Liquid liquid){
            return limitedMove(this, next, liquid, pressure(), super::moveLiquid);
        }
    }
}
