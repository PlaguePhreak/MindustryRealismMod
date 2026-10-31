import arc.*;
import arc.backend.headless.*;
import arc.files.*;
import arc.util.*;
import grounded.combat.*;
import grounded.combat.TurretRealism.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.net.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;

import static mindustry.Vars.*;

/**
 * Headless checks of turret magazines and barrel heat against the real game code. Run with the Mindustry server jar on
 * the classpath: java -cp server-release.jar:build/check TurretCheck
 */
public class TurretCheck{
    static int failures = 0;

    public static void main(String[] args) throws Exception{
        boolean[] ready = {false};
        Throwable[] error = {null};

        new HeadlessApplication(new ApplicationCore(){
            @Override
            public void setup(){
                Core.settings.setDataDirectory(new Fi(System.getProperty("java.io.tmpdir")).child("gi-test-data"));
                headless = true;
                net = new Net(null);
                tree = new FileTree();
                Vars.init();
                world = new World();
                content.createBaseContent();
                TurretRealism.apply();
                add(logic = new Logic());
                content.init();
                TurretRealism.addBars();
            }

            @Override
            public void init(){
                super.init();
                ready[0] = true;
            }
        }, t -> error[0] = t);

        while(!ready[0]){
            if(error[0] != null) throw new RuntimeException(error[0]);
            Thread.sleep(10);
        }

        Core.app.post(() -> {
            try{
                run();
            }catch(Throwable t){
                t.printStackTrace();
                failures++;
            }
            System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
            System.exit(failures == 0 ? 0 : 1);
        });
    }

    static void run(){
        var duo = (ItemTurret)Blocks.duo;
        int mag = TurretRealism.magazineSize(duo);
        System.out.printf("duo: magazine %d shots, magazine reload %.2fs, heat per shot %.3f%n", mag, TurretRealism.magazineReloadTicks(duo) / 60f, TurretRealism.heatPerShot(duo));
        check("duo uses the realistic turret build", place(duo) instanceof RealisticTurretBuild);
        check("ripple and foreshadow too", place(Blocks.ripple) instanceof RealisticTurretBuild && place(Blocks.foreshadow) instanceof RealisticTurretBuild);

        //fire for 10 seconds: one magazine, then a reload pause
        var t = (RealisticTurretBuild)place(duo);
        int[] shots = fire(t, 10 * 60);
        System.out.printf("10s of fire: %d shots, first gap after shot %d lasted %d ticks%n", shots[0], shots[1], shots[2]);
        check("fires exactly one magazine before pausing", shots[1] == mag);
        check("pause lasts the magazine reload", Math.abs(shots[2] - TurretRealism.magazineReloadTicks(duo)) < 25);

        //sustained fire: barrel heat builds up across magazines and slows the turret
        t = (RealisticTurretBuild)place(duo);
        int early = fire(t, 10 * 60)[0];
        int late = fire(t, 10 * 60)[0];
        System.out.printf("sustained fire: %d shots in first 10s, %d in next 10s, barrel heat %.0f%%%n", early, late, t.barrelHeat * 100);
        check("barrel heats up under sustained fire", t.barrelHeat > TurretRealism.slowHeat);
        check("hot barrel fires slower", late < early);

        //it cools down when idle
        for(int i = 0; i < 15 * 60; i++) tick(t, false);
        check("barrel cools when idle", t.barrelHeat < 0.01f);

        //water cools it faster
        t = (RealisticTurretBuild)place(duo);
        t.barrelHeat = 1f;
        var dry = (RealisticTurretBuild)place(duo);
        dry.barrelHeat = 1f;
        for(int i = 0; i < 120; i++){
            t.liquids.add(Liquids.water, 1f);
            tick(t, false);
            tick(dry, false);
        }
        System.out.printf("after 2s idle: dry barrel %.0f%%, water-cooled %.0f%%%n", dry.barrelHeat * 100, t.barrelHeat * 100);
        check("water cools the barrel faster", t.barrelHeat < dry.barrelHeat - 0.05f);

        //toggles restore vanilla
        Core.settings.put(TurretRealism.settingMagazines, false);
        Core.settings.put(TurretRealism.settingBarrelHeat, false);
        t = (RealisticTurretBuild)place(duo);
        int vanilla = fire(t, 10 * 60)[0];
        Core.settings.put(TurretRealism.settingMagazines, true);
        Core.settings.put(TurretRealism.settingBarrelHeat, true);
        System.out.printf("toggled off: %d shots in 10s%n", vanilla);
        check("toggled off fires continuously", vanilla > early && t.barrelHeat == 0f && t.shotsFired == 0);
    }

    static Building place(Block block){
        logic.reset();
        state.rules = new Rules();
        Time.setDeltaProvider(() -> 1f);
        Tiles tiles = world.resize(20, 20);
        world.beginMapLoad();
        tiles.fill();
        world.endMapLoad();
        tiles.get(10, 10).setBlock(block, Team.sharded, 0);
        return tiles.get(10, 10).build;
    }

    /** Fires at a point east of the turret for `ticks`. Returns total shots, shots before the first pause, and the length of that pause. */
    static int[] fire(RealisticTurretBuild t, int ticks){
        int total = 0, beforeGap = -1, gap = 0, sinceShot = 0;
        for(int i = 0; i < ticks; i++){
            int before = t.totalShots;
            tick(t, true);
            if(t.totalShots > before){
                if(beforeGap < 0 && sinceShot > 30){
                    beforeGap = total;
                    gap = sinceShot;
                }
                total += t.totalShots - before;
                sinceShot = 0;
            }else{
                sinceShot++;
            }
        }
        return new int[]{total, beforeGap, gap};
    }

    static void tick(RealisticTurretBuild t, boolean shooting){
        Time.update();
        if(t.totalAmmo < 10) t.handleItem(null, Items.copper);
        if(shooting) t.control(LAccess.shoot, World.conv(t.x + 60f), World.conv(t.y), 1, 0);
        t.update();
    }

    static void check(String name, boolean ok){
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if(!ok) failures++;
    }
}
