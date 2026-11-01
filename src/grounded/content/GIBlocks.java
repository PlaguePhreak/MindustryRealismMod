package grounded.content;

import arc.graphics.*;
import grounded.heat.*;
import grounded.power.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.game.Objectives.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.consumers.*;
import mindustry.world.draw.*;

import static grounded.content.GIItems.*;
import static mindustry.type.ItemStack.*;

/**
 * Steelmaking chain and the blocks built from steel.
 *
 * Iron comes from sand (Magnetic Separator) anywhere, or from iron ore where a map has it. It is concentrated, sintered
 * with coke, smelted to pig iron in a blast furnace (which also gives off slag that must go somewhere), and refined to
 * steel in an electric arc furnace together with scrap.
 */
public class GIBlocks{
    public static Block oreIron, magneticSeparator, oreConcentrator, cokeOven, sinterPlant, blastFurnace, arcFurnace,
        steelWall, steelWallLarge, transmissionTower;

    public static void load(){
        oreIron = new OreBlock("ore-iron", ironOre);

        magneticSeparator = new GenericCrafter("magnetic-separator"){{
            requirements(Category.crafting, with(Items.copper, 40, Items.lead, 30, Items.graphite, 15));
            size = 2;
            craftTime = 60f;
            outputItem = new ItemStack(ironConcentrate, 1);
            craftEffect = Fx.pulverizeSmall;
            consumeItems(with(Items.sand, 4));
            consumePower(0.4f);
        }};

        oreConcentrator = new GenericCrafter("ore-concentrator"){{
            requirements(Category.crafting, with(Items.copper, 50, Items.lead, 40, Items.graphite, 20));
            size = 2;
            craftTime = 45f;
            //washing crushed ore leaves sand tailings behind
            outputItems = with(ironConcentrate, 2, Items.sand, 1);
            craftEffect = Fx.pulverizeMedium;
            consumeItems(with(ironOre, 2));
            hasLiquids = true;
            consumeLiquid(Liquids.water, 0.1f);
            consumePower(0.3f);
        }};

        cokeOven = new GenericCrafter("coke-oven"){{
            requirements(Category.crafting, with(Items.copper, 40, Items.lead, 20, Items.graphite, 10));
            size = 2;
            craftTime = 90f;
            outputItem = new ItemStack(coke, 1);
            craftEffect = Fx.smeltsmoke;
            drawer = new DrawMulti(new DrawDefault(), new DrawFlame(Color.valueOf("ffb070")));
            consumeItems(with(Items.coal, 2));
        }};

        sinterPlant = new GenericCrafter("sinter-plant"){{
            requirements(Category.crafting, with(Items.copper, 60, Items.lead, 40, Items.graphite, 30, Items.silicon, 15));
            size = 2;
            craftTime = 80f;
            outputItem = new ItemStack(sinter, 2);
            craftEffect = Fx.smeltsmoke;
            drawer = new DrawMulti(new DrawDefault(), new DrawFlame(Color.valueOf("ffc880")));
            consumeItems(with(ironConcentrate, 3, coke, 1));
            consumePower(0.5f);
        }};

        blastFurnace = new GenericCrafter("blast-furnace"){{
            requirements(Category.crafting, with(Items.copper, 120, Items.lead, 80, Items.graphite, 60, Items.silicon, 40, Items.titanium, 40));
            size = 3;
            craftTime = 120f;
            itemCapacity = 20;
            outputItem = new ItemStack(pigIron, 2);
            //slag has to be piped away (a separator turns it back into ore); a full slag tank stops the furnace
            outputLiquid = new LiquidStack(Liquids.slag, 4f / 60f);
            dumpExtraLiquid = false;
            hasLiquids = true;
            liquidCapacity = 30f;
            craftEffect = Fx.smeltsmoke;
            drawer = new DrawMulti(new DrawDefault(), new DrawFlame(Color.valueOf("ff9a50")));
            //sand stands in for limestone flux
            consumeItems(with(sinter, 3, coke, 2, Items.sand, 1));
            consumePower(1f);
            consume(new ConsumeCoolant(0.1f)).optional(true, false);
        }};

        arcFurnace = new GenericCrafter("arc-furnace"){{
            requirements(Category.crafting, with(Items.copper, 150, Items.lead, 100, Items.silicon, 80, Items.titanium, 60, Items.graphite, 60));
            size = 3;
            craftTime = 90f;
            itemCapacity = 20;
            outputItem = new ItemStack(steel, 2);
            craftEffect = Fx.smeltsmoke;
            drawer = new DrawMulti(new DrawDefault(), new DrawFlame(Color.valueOf("c8e0ff")));
            //recycled scrap goes in alongside the pig iron, as in a real arc furnace
            consumeItems(with(pigIron, 2, Items.scrap, 1));
            consumePower(4f);
        }};

        int wallHealthMultiplier = 4;
        steelWall = new Wall("steel-wall"){{
            requirements(Category.defense, with(steel, 6));
            health = 160 * wallHealthMultiplier;
            armor = 6f;
        }};

        steelWallLarge = new Wall("steel-wall-large"){{
            requirements(Category.defense, mult(steelWall.requirements, 4));
            health = 160 * 4 * wallHealthMultiplier;
            armor = 6f;
            size = 2;
        }};

        //high-voltage line: long reach and a quarter of the usual line loss
        transmissionTower = new PowerNode("transmission-tower"){{
            requirements(Category.power, with(steel, 10, Items.silicon, 8, Items.copper, 10));
            size = 2;
            maxNodes = 4;
            laserRange = 30f;
        }};
        LineLoss.lossFactor.put(transmissionTower, 0.25f);

        MachineHeat.heatLoad.put(cokeOven, 0.9f);
        MachineHeat.heatLoad.put(sinterPlant, 1.05f);
        MachineHeat.heatLoad.put(blastFurnace, 1.3f);
        MachineHeat.heatLoad.put(arcFurnace, 1.35f);
    }

    /** Adds everything to the Serpulo tech tree. Call after content init. */
    public static void addResearch(){
        node(Blocks.pulverizer, magneticSeparator);
        node(magneticSeparator, oreConcentrator);
        node(Blocks.graphitePress, cokeOven);
        node(magneticSeparator, sinterPlant);
        node(sinterPlant, blastFurnace);
        node(blastFurnace, arcFurnace);
        node(Blocks.titaniumWall, steelWall);
        node(steelWall, steelWallLarge);
        node(Blocks.powerNodeLarge, transmissionTower);

        produce(Items.sand, ironConcentrate);
        produce(ironConcentrate, ironOre);
        produce(Items.coal, coke);
        produce(ironConcentrate, sinter);
        produce(sinter, pigIron);
        produce(pigIron, steel);
    }

    static TechNode find(mindustry.ctype.UnlockableContent content){
        return TechTree.all.find(n -> n.content == content);
    }

    static void node(mindustry.ctype.UnlockableContent parent, mindustry.ctype.UnlockableContent child){
        var p = find(parent);
        if(p != null && find(child) == null){
            new TechNode(p, child, child.researchRequirements());
        }
    }

    static void produce(mindustry.ctype.UnlockableContent parent, Item item){
        var p = find(parent);
        if(p != null && find(item) == null){
            var node = new TechNode(p, item, ItemStack.empty);
            node.objectives.add(new Produce(item));
        }
    }
}
