package ua.sdocraft.rontacz.building;

import com.solegendary.reignofnether.building.BuildingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reign of Nether vehicle hangar, 23 x 21 blocks.
 * TRUE QUONSET HALF-CYLINDER: continuous semicircular cross-section from
 * both sides of the slab to the 11-block-high crown. No flat roof.
 * All blueprint blocks, including reserved air, are constructed by RoN.
 */
public final class VehicleHangarStructure {
    private VehicleHangarStructure() {}

    private static final int WIDTH = 23;
    private static final int DEPTH = 21;
    private static final int CENTER_X = 11;
    private static final int RADIUS = 10;
    private static final int HEIGHT = RADIUS + 1; // roof crown at block y=11

    /** Top of the steel vault at column x, using a real semicircle. */
    private static int roofY(int x) {
        int d = x - CENTER_X;
        return 1 + (int) Math.round(Math.sqrt(Math.max(0, RADIUS * RADIUS - d * d)));
    }

    /** Arched vehicle entrance, x 7..15. Clear height 8..9 blocks. */
    private static int gateY(int x) {
        int d = Math.abs(x - CENTER_X);
        return 6 + (int)Math.round(
            Math.sqrt(Math.max(0, 25 - d*d)) * 0.7);
    }

    private static BlockState steelSkin(int x, int y, int z) {
        // Periodic reinforced hoops follow the *curve*, not a flat roof.
        if (z == 2 || z == 7 || z == 13 || z == 19)
            return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        return (((x*3 + y + z*5) % 11) <= 2
            ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.GRAY_CONCRETE).defaultBlockState();
    }

    public static ArrayList<BuildingBlock> create(boolean superior) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        BlockState air = Blocks.AIR.defaultBlockState();

        // Clear exactly the same footprint as the former 23x21 hangar.
        for (int x=0; x<WIDTH; x++)
            for (int z=0; z<DEPTH; z++)
                for (int y=0; y<=HEIGHT; y++)
                    set(blocks, x,y,z,air);

        // Heavy walkable foundation.
        for (int x=0; x<WIDTH; x++) for (int z=0; z<DEPTH; z++)
            set(blocks,x,0,z, (x==0 || z==0 || x==22 || z==20
                ? Blocks.POLISHED_DEEPSLATE : Blocks.SMOOTH_STONE).defaultBlockState());

        // Front and back arched gable walls, filling only the shape inside
        // the half-circle. The front contains an arched vehicle opening.
        for (int x=1; x<=21; x++) for (int z:new int[]{2,19}) {
            int roof=roofY(x);
            for (int y=1; y<=roof; y++) {
                if (z==2 && x>=7 && x<=15 && y<=gateY(x))
                    continue;
                boolean lightPanel = (z==19 && y>=4 && y<=5
                    && (x==5 || x==11 || x==17));
                set(blocks,x,y,z, lightPanel
                    ? Blocks.TINTED_GLASS.defaultBlockState()
                    : ((x+y+z)%7==0 ? Blocks.LIGHT_GRAY_CONCRETE
                        : Blocks.GRAY_CONCRETE).defaultBlockState());
            }
        }

        // SEMICIRCULAR BARREL VAULT.
        // When steep near ground, bridge the Y-gaps between neighboring X
        // columns. Every row of the arch is connected; no floating roof strips.
        for (int x=1; x<=21; x++) {
            int crown=roofY(x);
            int adjacent = x<CENTER_X ? roofY(Math.max(1,x-1))
                         : x>CENTER_X ? roofY(Math.min(21,x+1)) : crown;
            int low=Math.min(crown,adjacent);
            for (int y=low; y<=crown; y++) {
                for (int z=2; z<=19; z++) {
                    // The vault frames the curved gate; roof itself stays intact.
                    set(blocks,x,y,z,steelSkin(x,y,z));
                }
            }
        }

        // Enlarge the lower half of the gate without cutting into curved ribs.
        for(int x=7;x<=15;x++)for(int y=1;y<=gateY(x);y++)
            set(blocks,x,y,2,air);

        // Concrete and iron trim around the arched portal.
        for (int x:new int[]{6,16}) for (int y=1;y<=7;y++)
            set(blocks,x,y,2,Blocks.IRON_BLOCK.defaultBlockState());
        for (int x=7;x<=15;x++) {
            int top=gateY(x)+1;
            if (top<roofY(x)) {
                set(blocks,x,top,2,Blocks.IRON_BLOCK.defaultBlockState());
            }
        }

        // Two yellow lanes under the portal, through to the service bay.
        for (int z=0;z<=12;z++) {
            set(blocks,7,0,z,Blocks.YELLOW_CONCRETE.defaultBlockState());
            set(blocks,15,0,z,Blocks.YELLOW_CONCRETE.defaultBlockState());
        }
        for(int x=8;x<=14;x++)for(int z=0;z<=5;z++)
            if((x+z)%7==0)set(blocks,x,0,z,Blocks.YELLOW_CONCRETE.defaultBlockState());

        // Keep a completely unobstructed central bay for vehicles.
        for(int x:new int[]{3,19})for(int z=5;z<=10;z+=2){
            set(blocks,x,1,z,Blocks.IRON_BLOCK.defaultBlockState());
            set(blocks,x,2,z,Blocks.IRON_BARS.defaultBlockState());
        }
        for(int x:new int[]{3,4,18,19}) {
            set(blocks,x,1,17,Blocks.BARREL.defaultBlockState());
            set(blocks,x,2,17,Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState());
        }
        if(superior) {
            set(blocks,5,1,17,Blocks.BLAST_FURNACE.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING,Direction.SOUTH));
            for(int x:new int[]{6,16})set(blocks,x,8,3,Blocks.SEA_LANTERN.defaultBlockState());
        } else {
            set(blocks,5,1,17,Blocks.FURNACE.defaultBlockState());
        }
        set(blocks,17,1,17,Blocks.SMITHING_TABLE.defaultBlockState());

        // Floodlights above the approach, away from the central gate opening.
        for(int x:new int[]{3,19}){
            set(blocks,x,1,0,Blocks.IRON_BLOCK.defaultBlockState());
            set(blocks,x,2,0,Blocks.SEA_LANTERN.defaultBlockState());
        }

        ArrayList<BuildingBlock> result=new ArrayList<>(blocks.size());
        blocks.forEach((pos,state)->result.add(new BuildingBlock(pos,state)));
        return result;
    }

    private static void set(Map<BlockPos,BlockState> blocks,int x,int y,int z,BlockState state) {
        blocks.put(new BlockPos(x,y,z),state);
    }
}
