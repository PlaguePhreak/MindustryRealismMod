package grounded.combat;

import arc.*;
import arc.math.*;
import arc.util.*;
import mindustry.entities.bullet.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.blocks.defense.turrets.*;

import static mindustry.Vars.*;

/**
 * Turret magazines and barrel heat, for every vanilla item turret.
 *
 * Magazines: a turret fires a magazine's worth of shots (about {@link #magazineSeconds} of continuous fire), then
 * stops to reload for {@link #reloadBase} seconds plus {@link #reloadPerSize} per tile of turret size.
 *
 * Barrel heat: each shot heats the barrel by enough that nonstop fire would reach 100% in about
 * {@link #secondsToOverheat}. The barrel cools on its own, faster with coolant. Above {@link #slowHeat} the fire rate
 * falls, down to {@link #minFireRate} at 100%, and shots spread wider as the barrel warps.
 */
public class TurretRealism{
    public static final String settingMagazines = "gi-magazines", settingBarrelHeat = "gi-barrel-heat";

    public static float magazineSeconds = 6f, reloadBase = 2f, reloadPerSize = 0.75f;
    public static int minMagazine = 3, maxMagazine = 60;

    public static float secondsToOverheat = 8f, secondsToCool = 10f;
    public static float slowHeat = 0.7f, minFireRate = 0.25f, maxSpread = 6f;

    public static void apply(){
        for(var block : content.blocks()){
            //only vanilla item turrets whose building is the stock ItemTurretBuild
            if(block instanceof ItemTurret turret && block.minfo.mod == null
                && (block.getClass() == ItemTurret.class || block.getClass().getSuperclass() == ItemTurret.class)){
                turret.buildType = () -> new RealisticTurretBuild(turret);
            }
        }
    }

    public static void addBars(){
        for(var block : content.blocks()){
            if(block instanceof ItemTurret turret && block.minfo.mod == null
                && (block.getClass() == ItemTurret.class || block.getClass().getSuperclass() == ItemTurret.class)){
                turret.addBar("gi-magazine", (RealisticTurretBuild b) -> new Bar(
                    () -> b.reloadingMagazine() ?
                        Core.bundle.get("bar.gi-magazine-reloading") :
                        Core.bundle.format("bar.gi-magazine", magazineSize(turret) - b.shotsFired, magazineSize(turret)),
                    () -> b.reloadingMagazine() ? Pal.lightishGray : Pal.ammo,
                    () -> b.reloadingMagazine() ? b.magazineTimer / magazineReloadTicks(turret) : 1f - (float)b.shotsFired / magazineSize(turret)
                ));
                turret.addBar("gi-barrel-heat", (RealisticTurretBuild b) -> new Bar(
                    () -> Core.bundle.format("bar.gi-barrel-heat", (int)(b.barrelHeat * 100f)),
                    () -> Tmp.c1.set(Pal.lightOrange).lerp(Pal.remove, b.barrelHeat),
                    () -> b.barrelHeat
                ));
            }
        }
    }

    public static boolean magazinesEnabled(){
        return Core.settings.getBool(settingMagazines, true);
    }

    public static boolean barrelHeatEnabled(){
        return Core.settings.getBool(settingBarrelHeat, true);
    }

    /** Shots per magazine: about {@link #magazineSeconds} of continuous fire at the turret's base rate. */
    public static int magazineSize(Turret turret){
        return Mathf.clamp(Math.round(60f / turret.reload * magazineSeconds), minMagazine, maxMagazine);
    }

    public static float magazineReloadTicks(Turret turret){
        return (reloadBase + reloadPerSize * turret.size) * 60f;
    }

    /** Heat added per shot, so nonstop fire at the base rate overheats in about {@link #secondsToOverheat}. */
    public static float heatPerShot(Turret turret){
        float shotsPerSecond = 60f / turret.reload;
        return (1f / secondsToOverheat + 1f / secondsToCool) / shotsPerSecond;
    }

    /** Fire rate multiplier at this barrel heat. */
    public static float fireRate(float heat){
        if(heat <= slowHeat) return 1f;
        return Mathf.lerp(1f, minFireRate, Mathf.clamp((heat - slowHeat) / (1f - slowHeat)));
    }

    public static class RealisticTurretBuild extends ItemTurret.ItemTurretBuild{
        public int shotsFired;
        /** Shots since placed, for stats and checks. */
        public int totalShots;
        public float magazineTimer;
        public float barrelHeat;

        public RealisticTurretBuild(ItemTurret turret){
            turret.super();
        }

        Turret turret(){
            return (Turret)block;
        }

        public boolean reloadingMagazine(){
            return magazinesEnabled() && shotsFired >= magazineSize(turret());
        }

        @Override
        public void updateTile(){
            if(!magazinesEnabled()){
                shotsFired = 0;
            }else if(reloadingMagazine()){
                magazineTimer += edelta();
                if(magazineTimer >= magazineReloadTicks(turret())){
                    shotsFired = 0;
                    magazineTimer = 0f;
                }
            }

            if(barrelHeatEnabled()){
                float cooling = 1f;
                if(liquids != null && liquids.currentAmount() > 0.01f && liquids.current().coolant){
                    cooling += liquids.current().heatCapacity * 3f;
                }
                barrelHeat = Math.max(barrelHeat - cooling * delta() / (secondsToCool * 60f), 0f);
            }else{
                barrelHeat = 0f;
            }

            super.updateTile();
        }

        @Override
        protected void updateShooting(){
            if(reloadingMagazine()) return;
            super.updateShooting();
        }

        @Override
        protected float baseReloadSpeed(){
            return super.baseReloadSpeed() * (barrelHeatEnabled() ? fireRate(barrelHeat) : 1f);
        }

        @Override
        protected void shoot(BulletType type){
            super.shoot(type);
            totalShots++;
            if(magazinesEnabled()) shotsFired++;
            if(barrelHeatEnabled()) barrelHeat = Math.min(barrelHeat + heatPerShot(turret()), 1f);
        }

        @Override
        protected void bullet(BulletType type, float xOffset, float yOffset, float angleOffset, Mover mover){
            float spread = barrelHeatEnabled() ? Mathf.range(maxSpread * Mathf.clamp((barrelHeat - slowHeat) / (1f - slowHeat))) : 0f;
            super.bullet(type, xOffset, yOffset, angleOffset + spread, mover);
        }
    }
}
