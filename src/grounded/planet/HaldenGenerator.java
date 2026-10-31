package grounded.planet;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.noise.*;
import mindustry.ai.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.maps.generators.*;
import mindustry.type.*;
import mindustry.world.*;

import static mindustry.Vars.*;

/**
 * Procedural terrain for Halden, the mod's temperate home world.
 *
 * Biomes come from latitude and height: ice caps at the poles, grassland and mud flats in the temperate band, and
 * rust-red ferric hills toward the equator. Coal is common in the lowlands, titanium shows up in colder sectors and
 * thorium in the hot, high equatorial hills. Each sector gets a landing zone, a few clearings joined by paths, rivers,
 * derelict ruins, and either wave spawns or an enemy base.
 */
public class HaldenGenerator extends PlanetGenerator{
    /** Noise scale of the planet surface. */
    float scl = 4.2f;
    /** Height below which the surface is open water. */
    float seaLevel = 0.17f;

    /** Biome table. Rows run from the poles (0) to the equator (last); columns from sea floor to peaks. */
    Block[][] biomes = {
        {Blocks.deepwater, Blocks.water, Blocks.iceSnow, Blocks.ice, Blocks.snow, Blocks.snow, Blocks.iceSnow, Blocks.ice, Blocks.ice},
        {Blocks.deepwater, Blocks.water, Blocks.darksandWater, Blocks.darksand, Blocks.snow, Blocks.stone, Blocks.snow, Blocks.iceSnow, Blocks.ice},
        {Blocks.deepwater, Blocks.water, Blocks.darksandWater, Blocks.darksand, Blocks.dirt, Blocks.stone, Blocks.stone, Blocks.basalt, Blocks.snow},
        {Blocks.deepwater, Blocks.water, Blocks.sandWater, Blocks.sand, Blocks.grass, Blocks.grass, Blocks.dirt, Blocks.ferricStone, Blocks.stone},
        {Blocks.deepwater, Blocks.water, Blocks.darksandWater, Blocks.mud, Blocks.grass, Blocks.dirt, Blocks.ferricStone, Blocks.ferricStone, Blocks.basalt},
        {Blocks.deepwater, Blocks.water, Blocks.sandWater, Blocks.sand, Blocks.dirt, Blocks.shale, Blocks.ferricStone, Blocks.redStone, Blocks.denseRedStone},
        {Blocks.deepwater, Blocks.sandWater, Blocks.sand, Blocks.sand, Blocks.shale, Blocks.salt, Blocks.redStone, Blocks.denseRedStone, Blocks.basalt}
    };

    /**
     * Extra ores generated in every Halden sector, for other parts of the mod (such as the steel chain's iron ore).
     * They come out richest on the ferric and red stone of the iron hills. Add to this before a sector is generated.
     */
    public static final Seq<Block> extraOres = new Seq<>();

    /** Everything a Halden sector can yield, for the planet's info page. */
    public static Seq<UnlockableContent> resources(){
        Seq<UnlockableContent> out = Seq.with(Items.copper, Items.lead, Items.sand, Items.coal, Items.scrap, Items.titanium, Items.thorium);
        for(Block ore : extraOres){
            if(ore.itemDrop != null && !out.contains(ore.itemDrop)) out.add(ore.itemDrop);
        }
        out.add(Liquids.water);
        return out;
    }

    BaseGenerator basegen = new BaseGenerator();

    /** Raw surface height, 0-1. */
    float rawHeight(Vec3 p){
        float h = Simplex.noise3d(seed, 7, 0.5f, 1f / 3f, p.x * scl, p.y * scl, p.z * scl);
        return Mathf.clamp(Mathf.pow(h, 1.6f) * 1.15f);
    }

    /** 0 at the poles, 1 at the equator, with noise so biome borders aren't straight lines. */
    float warmth(Vec3 p){
        float lat = 1f - Math.abs(p.y);
        float n = Simplex.noise3d(seed + 3, 5, 0.55f, 1f / 2f, p.x * scl, p.y * scl + 300f, p.z * scl);
        return Mathf.clamp(Mathf.lerp(lat, n, 0.35f));
    }

    Block biome(Vec3 p){
        float h = rawHeight(p);
        int row = Mathf.clamp((int)(warmth(p) * biomes.length), 0, biomes.length - 1);
        int col;
        if(h < seaLevel){
            col = h < seaLevel * 0.6f ? 0 : 1;
        }else{
            col = 2 + Mathf.clamp((int)((h - seaLevel) / (1f - seaLevel) * (biomes[0].length - 2)), 0, biomes[0].length - 3);
        }
        return biomes[row][col];
    }

    @Override
    public float getHeight(Vec3 position){
        return Math.max(rawHeight(position), seaLevel) * 0.9f;
    }

    @Override
    public void getColor(Vec3 position, Color out){
        Block block = biome(position);
        out.set(block.mapColor).a(1f - block.albedo);
    }

    @Override
    public void generateSector(Sector sector){
        //enemy bases appear on a patchy band of sectors, never next to the landing zone
        Sector start = sector.planet.getStartSector();
        if(sector == start){
            //mark the suggested landing site on the globe, unless the player renamed it
            if(sector.info.name == null) sector.info.name = Core.bundle == null ? "Landing Site" : Core.bundle.get("gi-landing-site", "Landing Site");
            if(sector.info.icon == null && sector.info.contentIcon == null) sector.info.icon = "home";
        }
        if(sector == start || start.near().contains(sector)) return;

        Vec3 v = sector.tile.v;
        float noise = Simplex.noise3d(seed + 7, 2, 0.6f, 1f / 0.6f, v.x, v.y, v.z);
        if(noise > 0.6f){
            sector.generateEnemyBase = true;
        }
    }

    @Override
    public void genTile(Vec3 position, TileGen tile){
        tile.floor = biome(position);
        tile.block = tile.floor.asFloor().wall;

        if(Ridged.noise3d(seed + 1, position.x, position.y, position.z, 2, 20) > 0.28){
            tile.block = Blocks.air;
        }
    }

    @Override
    protected float noise(float x, float y, double octaves, double falloff, double scl, double mag){
        Vec3 v = sector.rect.project(x, y).scl(5f);
        return Simplex.noise3d(seed, octaves, falloff, 1f / scl, v.x, v.y, v.z) * (float)mag;
    }

    /** A clearing in the sector. */
    static class Clearing{
        final int x, y, radius;

        Clearing(int x, int y, int radius){
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
    }

    void connect(Clearing a, Clearing b){
        //bend the path through a random midpoint so it doesn't run dead straight
        Tmp.v1.set(a.x, a.y).add(b.x, b.y).scl(0.5f).add(Tmp.v2.setToRandomDirection(rand).scl(Mathf.dst(a.x, a.y, b.x, b.y) * 0.4f));
        Tmp.v1.sub(width / 2f, height / 2f).limit(width / 2f / Mathf.sqrt3).add(width / 2f, height / 2f);
        int mx = (int)Tmp.v1.x, my = (int)Tmp.v1.y;
        path(a.x, a.y, mx, my);
        path(mx, my, b.x, b.y);
    }

    void path(int x1, int y1, int x2, int y2){
        float nscl = rand.random(100f, 140f) * 6f;
        int stroke = rand.random(3, 8);
        brush(pathfind(x1, y1, x2, y2, t -> (t.solid() ? 50f : 0f) + noise(t.x, t.y, 2, 0.4f, 1f / nscl) * 500, Astar.manhattan), stroke);
    }

    @Override
    protected void generate(){
        cells(4);
        distort(10f, 12f);

        float radius = width / 2f / Mathf.sqrt3;
        Seq<Clearing> clearings = new Seq<>();
        int count = rand.random(2, 5);
        for(int i = 0; i < count; i++){
            Tmp.v1.trns(rand.random(360f), rand.random(radius / 1.3f));
            float maxrad = radius - Tmp.v1.len();
            clearings.add(new Clearing((int)(width / 2f + Tmp.v1.x), (int)(height / 2f + Tmp.v1.y), (int)Math.min(rand.random(9f, Math.max(maxrad / 2f, 10f)), 30f)));
        }

        //landing zone near the edge, on dry ground if possible; enemies on the far side
        Clearing spawn = null;
        Seq<Clearing> enemies = new Seq<>();
        int offset = rand.nextInt(360);
        float length = width / 2.55f - rand.random(13, 23);
        for(int i = 0; i < 360; i += 5){
            int angle = offset + i;
            int cx = (int)(width / 2 + Angles.trnsx(angle, length)), cy = (int)(height / 2 + Angles.trnsy(angle, length));

            int wet = 0;
            for(int rx = -5; rx <= 5; rx++){
                for(int ry = -5; ry <= 5; ry++){
                    Tile tile = tiles.get(cx + rx, cy + ry);
                    if(tile == null || tile.floor().isLiquid) wet++;
                }
            }

            if(wet <= 4 || i + 5 >= 360){
                clearings.add(spawn = new Clearing(cx, cy, rand.random(9, 15)));
                int enemySpawns = rand.random(1, Math.max((int)(sector.threat * 4), 1));
                for(int j = 0; j < enemySpawns; j++){
                    Tmp.v1.set(cx - width / 2, cy - height / 2).rotate(180f + rand.range(60f)).add(width / 2, height / 2);
                    Clearing e = new Clearing((int)Tmp.v1.x, (int)Tmp.v1.y, rand.random(8, 16));
                    clearings.add(e);
                    enemies.add(e);
                }
                break;
            }
        }

        for(Clearing c : clearings){
            erase(c.x, c.y, c.radius);
        }
        for(int i = rand.random(1, 4); i > 0; i--){
            connect(clearings.random(rand), clearings.random(rand));
        }
        for(Clearing c : clearings){
            if(c != spawn) connect(spawn, c);
        }

        cells(1);
        distort(10f, 6f);

        //rivers; frozen ground stays frozen
        Clearing land = spawn;
        pass((x, y) -> {
            if(block.solid || floor.asFloor().isLiquid) return;
            if(floor == Blocks.ice || floor == Blocks.iceSnow || floor == Blocks.snow) return;

            Vec3 v = sector.rect.project(x, y);
            float wobble = Simplex.noise2d(sector.id, 2, 0.6f, 1f / 7f, x, y) * 0.1f;
            float river = Ridged.noise3d(seed + 2, v.x, v.y, v.z, 1, 1f / 55f) + wobble;
            float keepOut = 12 + wobble * 44 - 2;

            if(river > 0.18f && !Mathf.within(x, y, land.x, land.y, keepOut)){
                boolean deep = river > 0.28f && !Mathf.within(x, y, land.x, land.y, keepOut + 3);
                floor = deep ? Blocks.water : (floor == Blocks.sand || floor == Blocks.salt ? Blocks.sandWater : Blocks.darksandWater);
            }
        });

        //ores: copper and lead everywhere, coal basins in the lowlands, titanium in the cold, thorium in the hot hills
        Vec3 sv = sector.tile.v;
        float warm = 1f - Math.abs(sv.y);
        Seq<Block> ores = Seq.with(Blocks.oreCopper, Blocks.oreLead);
        if(Simplex.noise3d(seed + 11, 2, 0.5, 1, sv.x, sv.y, sv.z) * 0.5f + 0.3f > 0.25f) ores.add(Blocks.oreCoal);
        if(Simplex.noise3d(seed + 12, 2, 0.5, 1, sv.x, sv.y, sv.z) * 0.5f + (1f - warm) > 0.55f) ores.add(Blocks.oreTitanium);
        if(Simplex.noise3d(seed + 13, 2, 0.5, 1, sv.x, sv.y, sv.z) * 0.5f + warm > 1.2f) ores.add(Blocks.oreThorium);
        if(rand.chance(0.3)) ores.add(Blocks.oreScrap);
        ores.addAll(extraOres);

        FloatSeq freqs = new FloatSeq();
        for(int i = 0; i < ores.size; i++){
            freqs.add(rand.random(-0.1f, 0.01f) - i * 0.01f);
        }

        pass((x, y) -> {
            if(!floor.asFloor().hasSurface()) return;
            //coal favours mud and dark sand, the old swamp floors
            float coalBonus = floor == Blocks.mud || floor == Blocks.darksand ? 0.04f : 0f;
            //ores from other systems (iron) favour the iron hills
            float hillBonus = floor == Blocks.ferricStone || floor == Blocks.redStone || floor == Blocks.denseRedStone ? 0.06f : 0f;

            int ox = x - 4, oy = y + 23;
            for(int i = ores.size - 1; i >= 0; i--){
                Block entry = ores.get(i);
                float freq = freqs.get(i) - (entry == Blocks.oreCoal ? coalBonus : 0f) - (extraOres.contains(entry) ? hillBonus : 0f);
                if(Math.abs(0.5f - noise(ox, oy + i * 999, 2, 0.7, 40 + i * 2)) > 0.22f + i * 0.01f &&
                    Math.abs(0.5f - noise(ox, oy - i * 999, 1, 1, 30 + i * 4)) > 0.37f + freq){
                    ore = entry;
                    break;
                }
            }
        });

        trimDark();
        median(2);
        inverseFloodFill(tiles.getn(spawn.x, spawn.y));
        tech();

        //scattered boulders and shrubs
        pass((x, y) -> {
            if(block != Blocks.air || !floor.asFloor().hasSurface()) return;
            for(Point2 p : Geometry.d4){
                Tile near = tiles.get(x + p.x, y + p.y);
                if(near != null && near.block() != Blocks.air) return;
            }
            if(rand.chance(0.01)){
                block = floor.asFloor().decoration;
            }
        });

        //derelict ruins on resources
        float difficulty = sector.threat;
        int ruins = rand.random(-2, 4);
        if(ruins > 0){
            IntSeq spots = new IntSeq();
            int pad = 25;
            for(int x = pad; x < width - pad; x++){
                for(int y = pad; y < height - pad; y++){
                    Tile t = tiles.getn(x, y);
                    if(!t.solid() && t.drop() != null) spots.add(t.pos());
                }
            }
            spots.shuffle(rand);

            int placed = 0;
            for(int i = 0; i < spots.size && placed < ruins; i++){
                int pos = spots.items[i];
                int x = Point2.x(pos), y = Point2.y(pos);
                if(Mathf.within(x, y, spawn.x, spawn.y, 18f)) continue;

                Tile t = tiles.getn(x, y);
                var forRes = t.overlay().itemDrop != null ? bases.forResource(t.drop()) : null;
                var part = forRes != null && forRes.any() ? forRes.getFrac(difficulty + rand.random(0.4f)) : null;
                if(part != null && BaseGenerator.tryPlace(part, x, y, Team.derelict, rand)){
                    placed++;
                }
            }
        }

        for(Tile t : tiles){
            if(t.overlay().needsSurface && !t.floor().hasSurface()){
                t.setOverlay(Blocks.air);
            }
        }

        Schematics.placeLaunchLoadout(spawn.x, spawn.y);

        for(Clearing e : enemies){
            tiles.getn(e.x, e.y).setOverlay(Blocks.spawn);
        }

        if(sector.hasEnemyBase()){
            basegen.generate(tiles, enemies.map(r -> tiles.getn(r.x, r.y)), tiles.get(spawn.x, spawn.y), state.rules.waveTeam, sector, difficulty);
            state.rules.attackMode = sector.info.attack = true;
        }else{
            state.rules.winWave = sector.info.winWave = 10 + 5 * (int)Math.max(difficulty * 10, 1);
        }

        state.rules.waveSpacing = Mathf.lerp(60 * 65 * 2, 60f * 60f, Math.max(difficulty - 0.4f, 0f));
        state.rules.waves = true;
        state.rules.env = sector.planet.defaultEnv;
        state.rules.enemyCoreBuildRadius = 600f;
        state.rules.spawns = Waves.generate(difficulty, new Rand(sector.id), state.rules.attackMode, state.rules.attackMode && spawner.countGroundSpawns() == 0, false);
    }

    @Override
    public void postGenerate(Tiles tiles){
        if(sector.hasEnemyBase()){
            basegen.postGenerate();
            if(spawner.countGroundSpawns() == 0){
                state.rules.spawns = Waves.generate(sector.threat, new Rand(sector.id), state.rules.attackMode, true, false);
            }
        }
    }
}
