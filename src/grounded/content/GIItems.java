package grounded.content;

import arc.graphics.*;
import mindustry.type.*;

/** Items for the steelmaking chain. Coal is vanilla. */
public class GIItems{
    public static Item ironOre, ironConcentrate, coke, sinter, pigIron, steel;

    public static void load(){
        ironOre = new Item("iron-ore", Color.valueOf("a0705a")){{
            hardness = 2;
            cost = 0.8f;
        }};

        //magnetite: what a magnet pulls out of sand or crushed ore
        ironConcentrate = new Item("iron-concentrate", Color.valueOf("4c4c58")){{
            cost = 1f;
        }};

        coke = new Item("coke", Color.valueOf("5a5a5e")){{
            flammability = 1.1f;
            cost = 0.9f;
        }};

        sinter = new Item("sinter", Color.valueOf("8a5446")){{
            cost = 1.2f;
        }};

        pigIron = new Item("pig-iron", Color.valueOf("7c7c88")){{
            cost = 1.4f;
        }};

        steel = new Item("steel", Color.valueOf("b8c2cf")){{
            cost = 1.8f;
            healthScaling = 1.2f;
        }};
    }
}
