package grounded.planet;

import arc.*;
import arc.math.*;
import arc.struct.*;
import mindustry.type.*;

import static mindustry.Vars.*;

/**
 * Planet climate.
 *
 * Each modded planet has an air temperature at its equator and at its poles, and a sector's temperature sits between
 * the two by latitude. The realism systems read the current sector's temperature:
 * <ul>
 * <li>Machine heat: machines settle hotter in warm air and colder in cold air ({@link #heatOffset()}).</li>
 * <li>Line loss: copper's resistance rises about 0.4% per degree, so hot grids lose more ({@link #lineLossScale()}).</li>
 * <li>Pipe pressure: cold liquid is thicker, so pipelines lose more pressure per tile ({@link #viscosityScale()}).</li>
 * <li>Generator startup: cold engines take longer to spin up ({@link #startupScale()}).</li>
 * </ul>
 * At the reference temperature of 15°C every effect is neutral, and vanilla planets have no climate, so they play
 * exactly as before.
 */
public class Climate{
    public static final String settingEnabled = "gi-climate";
    /** Temperature at which every effect is neutral, in °C. */
    public static final float reference = 15f;

    public static final ObjectMap<Planet, Climate> planets = new ObjectMap<>();

    /** Air temperature at the equator and at the poles, in °C. */
    public final float equator, poles;

    public Climate(float equator, float poles){
        this.equator = equator;
        this.poles = poles;
    }

    /** Temperature of a sector by its latitude. */
    public float temperatureAt(Sector sector){
        return Mathf.lerp(equator, poles, Mathf.clamp(Math.abs(sector.tile.v.y)));
    }

    public static boolean enabled(){
        return Core.settings.getBool(settingEnabled, true);
    }

    public static float temperatureOf(Sector sector){
        var climate = sector == null ? null : planets.get(sector.planet);
        return climate == null ? reference : climate.temperatureAt(sector);
    }

    /** Air temperature where the game is being played, in °C. */
    public static float temperature(){
        if(!enabled() || state == null || state.rules == null) return reference;
        if(state.rules.sector != null) return temperatureOf(state.rules.sector);
        //custom games on a modded planet use its average climate
        var climate = state.rules.planet == null ? null : planets.get(state.rules.planet);
        return climate == null ? reference : (climate.equator + climate.poles) / 2f;
    }

    /** Heat added to a machine's equilibrium, where 1 is its safe limit. +0.18 at 60°C, -0.24 at -45°C. */
    public static float heatOffset(){
        return (temperature() - reference) / 250f;
    }

    /** Multiplier on line loss from the resistance of warm or cold copper. */
    public static float lineLossScale(){
        return Math.max(1f + 0.0039f * (temperature() - reference), 0.5f);
    }

    /** Multiplier on pipe pressure drop from liquid viscosity. */
    public static float viscosityScale(){
        return Mathf.clamp(1f + 0.012f * (reference - temperature()), 0.6f, 2f);
    }

    /** Multiplier on generator startup time. Cold starts are slow, warm ones a little quicker. */
    public static float startupScale(){
        float t = temperature();
        return t < reference ? 1f + 0.01f * (reference - t) : Math.max(1f - 0.003f * (t - reference), 0.8f);
    }
}
