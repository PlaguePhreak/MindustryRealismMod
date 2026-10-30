package grounded;

import arc.util.*;
import grounded.power.*;
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
    }

    @Override
    public void init(){
        LineLoss.addBars();

        if(ui != null && ui.settings != null){
            ui.settings.addCategory("Grounded Industry", Icon.power, table -> {
                table.checkPref(LineLoss.settingEnabled, true);
                table.sliderPref(LineLoss.settingRate, LineLoss.defaultRate, 0, 100, 1, v -> Strings.autoFixed(v / 100f, 2) + "%");
                table.sliderPref(LineLoss.settingCap, LineLoss.defaultCap, 5, 100, 5, v -> v + "%");
            });
        }
    }
}
