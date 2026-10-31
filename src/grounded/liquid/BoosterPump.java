package grounded.liquid;

import arc.*;
import arc.graphics.g2d.*;
import arc.math.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static grounded.liquid.PipePressure.*;

/**
 * An inline pump that restores full pressure to a pipeline. Liquid goes in the back and out the front. Unpowered, it
 * passes liquid through like a short conduit.
 */
public class BoosterPump extends Block{
    public TextureRegion bottomRegion, topRegion;

    public BoosterPump(String name){
        super(name);
        rotate = true;
        update = true;
        solid = false;
        underBullets = true;
        hasLiquids = true;
        outputsLiquid = true;
        liquidCapacity = 20f;
        group = BlockGroup.liquids;
        envEnabled = Env.any;
        consumePower(0.3f / 60f);
    }

    @Override
    public void load(){
        super.load();
        bottomRegion = Core.atlas.find(name + "-bottom");
        topRegion = Core.atlas.find(name + "-top");
    }

    @Override
    protected TextureRegion[] icons(){
        return new TextureRegion[]{bottomRegion, topRegion};
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.output, "Restores full pipe pressure");
    }

    public class BoosterPumpBuild extends Building implements Pressurized{
        public float pressure;
        public Building lastSource;

        @Override
        public float pressure(){
            return enabled() ? pressure : 1f;
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            //only from behind, and only one liquid at a time
            return source == back() && (liquids.current() == liquid || liquids.currentAmount() < 0.2f);
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount){
            super.handleLiquid(source, liquid, amount);
            lastSource = source;
        }

        @Override
        public void updateTile(){
            float passive = Math.max(sourcePressure(lastSource) - 0.05f, 0f);
            pressure = Mathf.lerp(passive, 1f, efficiency);
            if(liquids.currentAmount() > 0.0001f){
                moveLiquidForward(false, liquids.current());
            }
        }

        @Override
        public float moveLiquid(Building next, Liquid liquid){
            return limitedMove(this, next, liquid, pressure(), super::moveLiquid);
        }

        @Override
        public void draw(){
            Draw.rect(bottomRegion, x, y, rotdeg());
            if(liquids.currentAmount() > 0.001f){
                Drawf.liquid(bottomRegion, x, y, liquids.currentAmount() / liquidCapacity, liquids.current().color, rotdeg());
            }
            Draw.rect(topRegion, x, y, rotdeg());
        }
    }
}
