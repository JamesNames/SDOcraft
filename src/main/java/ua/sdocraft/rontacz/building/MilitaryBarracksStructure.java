package ua.sdocraft.rontacz.building;

import com.solegendary.reignofnether.building.BuildingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Military 15x13 RTS barracks, constructed as real Minecraft blocks by RoN.
 *
 * Layout: fortified concrete foundation, olive-grey camo walls, iron-framed
 * windows, accessible center entrance, protected courtyard, vehicle supplies,
 * floodlights, camouflaged roof, radio mast and defensive sandbag posts.
 *
 * Same deterministic blueprint for client previews, server build / repair and
 * saved placement checks. No after-the-fact block replacements.
 */
public final class MilitaryBarracksStructure {
    private MilitaryBarracksStructure() {}
    private static final int SIZE_X=15, SIZE_Z=13;

    public static ArrayList<BuildingBlock> create() {
        Map<BlockPos,BlockState> map=new LinkedHashMap<>();
        BlockState air=Blocks.AIR.defaultBlockState();

        // Reserve the entire volume: RoN preview and world creation see
        // identical clearance, even if the build site has tall grass.
        for(int x=0;x<SIZE_X;x++)
            for(int z=0;z<SIZE_Z;z++)
                for(int y=0;y<=9;y++)
                    map.put(new BlockPos(x,y,z),air);

        // Reinforced polished-deepslate / stone foundation with walkable floor.
        for(int x=0;x<SIZE_X;x++)for(int z=0;z<SIZE_Z;z++) {
            boolean edge=x==0 || z==0 || x==SIZE_X-1 || z==SIZE_Z-1;
            set(map,x,0,z,(edge?Blocks.POLISHED_DEEPSLATE:Blocks.SMOOTH_STONE).defaultBlockState());
        }

        // Main bunker: outer walls 1..13, 2..11. Broad door at x6..8/z2.
        for(int y=1;y<=4;y++)for(int x=1;x<=13;x++)for(int z=2;z<=11;z++) {
            if(x!=1 && x!=13 && z!=2 && z!=11)continue;
            boolean entrance=z==2 && x>=6 && x<=8 && y<=3;
            boolean window=(y==2 || y==3) && (
                (z==2 && (x==4 || x==10)) ||
                (z==11 && (x==4 || x==7 || x==10)) ||
                ((x==1 || x==13) && (z==5 || z==8)));
            if(entrance)continue;
            BlockState state=window?Blocks.IRON_BARS.defaultBlockState():
                ((x==1||x==13)&&(z==2||z==11) ?
                    Blocks.POLISHED_DEEPSLATE: (x+z+y)%7==0 ?
                    Blocks.GRAY_CONCRETE:Blocks.GREEN_TERRACOTTA).defaultBlockState();
            set(map,x,y,z,state);
        }

        // Structural steel posts and a wide accessible entrance with staircase.
        for(int y=1;y<=4;y++)for(int z: new int[]{2,11})
            for(int x: new int[]{1,13})
                set(map,x,y,z,Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        for(int x=6;x<=8;x++) {
            set(map,x,0,1,Blocks.POLISHED_ANDESITE.defaultBlockState());
            set(map,x,0,2,Blocks.POLISHED_ANDESITE.defaultBlockState());
        }
        set(map,7,0,0,Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState()
            .setValue(StairBlock.FACING,Direction.SOUTH));

        // Roof in two camo tones, a contrasting reinforced rim.
        for(int x=1;x<=13;x++)for(int z=2;z<=11;z++)
            set(map,x,5,z,((x*5+z*3)%8<4?
                Blocks.GREEN_TERRACOTTA:Blocks.GRAY_CONCRETE).defaultBlockState());
        for(int x=1;x<=13;x++)for(int z=2;z<=11;z++)
            if(x==1||x==13||z==2||z==11)
                set(map,x,6,z,Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState());

        // Steel canopy over the entrance, flanked by searchlights.
        for(int x=5;x<=9;x++)set(map,x,4,1,Blocks.DARK_OAK_SLAB.defaultBlockState());
        for(int x:new int[]{4,10}) {
            set(map,x,3,1,Blocks.IRON_BLOCK.defaultBlockState());
            set(map,x,4,1,Blocks.SEA_LANTERN.defaultBlockState());
        }

        // Defensive sandbags in the front courtyard (keeps central walk clear).
        for(int x=1;x<=4;x++)set(map,x,1,0,Blocks.SMOOTH_SANDSTONE.defaultBlockState());
        for(int x=10;x<=13;x++)set(map,x,1,0,Blocks.SMOOTH_SANDSTONE.defaultBlockState());
        for(int z=3;z<=5;z++)for(int x:new int[]{0,14})
            set(map,x,1,z,Blocks.SMOOTH_SANDSTONE.defaultBlockState());

        // Interior supplies near rear wall, away from unit spawn and path.
        for(int x:new int[]{3,4,11,12}) {
            set(map,x,1,9,Blocks.BARREL.defaultBlockState());
            set(map,x,2,9,Blocks.SPRUCE_SLAB.defaultBlockState());
        }
        for(int x=4;x<=10;x++)set(map,x,0,7,Blocks.POLISHED_ANDESITE.defaultBlockState());

        // Rooftop radio mast visible from a distance, within reserved height.
        for(int y=6;y<=8;y++)set(map,12,y,9,Blocks.IRON_BARS.defaultBlockState());
        set(map,12,9,9,Blocks.LIGHTNING_ROD.defaultBlockState());

        ArrayList<BuildingBlock> result=new ArrayList<>(map.size());
        map.forEach((pos,state)->result.add(new BuildingBlock(pos,state)));
        return result;
    }

    private static void set(Map<BlockPos,BlockState> m,int x,int y,int z,BlockState state){
        m.put(new BlockPos(x,y,z),state);
    }
}
