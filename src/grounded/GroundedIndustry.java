package grounded;

import arc.util.*;
import grounded.combat.*;
import grounded.heat.*;
import grounded.liquid.*;
import grounded.planet.*;
import grounded.power.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.gen.*;
import mindustry.mod.*;

import static mindustry.Vars.*;

public class GroundedIndustry extends Mod{

    public GroundedIndustry(){
        Log.info("Loaded Grounded Industry.");
    }

    @Override
    public void loadContent(){
        //runs after vanilla content is created but before it is initialized, so vanilla blocks can still gain consumers here
        LineLoss.apply();
        GeneratorStartup.apply();
        MachineHeat.apply();
        PipePressure.apply();
        TurretRealism.apply();
        GroundedPlanets.load();
    }

    @Override
    public void init(){
        LineLoss.addBars();
        GeneratorStartup.addBars();
        MachineHeat.addBars();
        PipePressure.addBars();
        TurretRealism.addBars();
        GridStability.init();

        //research the booster pump after the pulse conduit
        var parent = TechTree.all.find(n -> n.content == Blocks.pulseConduit);
        if(parent != null){
            new TechNode(parent, PipePressure.boosterPump, PipePressure.boosterPump.researchRequirements());
        }

        if(ui != null && ui.settings != null){
            ui.settings.addCategory("Grounded Industry", Icon.power, table -> {
                table.checkPref(LineLoss.settingEnabled, true);
                table.sliderPref(LineLoss.settingRate, LineLoss.defaultRate, 0, 100, 1, v -> Strings.autoFixed(v / 100f, 2) + "%");
                table.sliderPref(LineLoss.settingCap, LineLoss.defaultCap, 5, 100, 5, v -> v + "%");
                table.checkPref(GridStability.settingEnabled, true);
                table.sliderPref(GridStability.settingThreshold, GridStability.defaultThreshold, 30, 95, 5, v -> v + "%");
                table.checkPref(MachineHeat.settingEnabled, true);
                table.checkPref(PipePressure.settingEnabled, true);
                table.checkPref(TurretRealism.settingMagazines, true);
                table.checkPref(TurretRealism.settingBarrelHeat, true);
                table.checkPref(GeneratorStartup.settingEnabled, true);
                table.sliderPref(GeneratorStartup.settingScale, GeneratorStartup.defaultScale, 25, 300, 25, v -> v + "%");
                table.checkPref(Climate.settingEnabled, true);
            });
        }
    }
}
