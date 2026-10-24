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
 * A large 23 x 21 real-block hangar for Reign of Nether Blacksmith.
 * Clear internal floor, nine-block-wide gate, reinforced roof and helicopter
 * landing sign. RoN itself places, previews, damages, repairs and rotates blocks.
 */
public final class VehicleHangarStructure {
    private VehicleHangarStructure() {}

    public static ArrayList<BuildingBlock> create(boolean superior) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        final int width = 23, depth = 21;

        // Large reserved volume for placement previews and construction.
        for (int x=0; x<width; x++)
            for (int z=0; z<depth; z++)
                for (int y=0; y<=11; y++)
                    set(blocks,x,y,z,air);

        for (int x=0; x<width; x++) for (int z=0; z<depth; z++)
            set(blocks,x,0,z,(x==0 || z==0 || x==22 || z==20
                ? Blocks.POLISHED_DEEPSLATE : Blocks.SMOOTH_STONE).defaultBlockState());

        // Wall/roof: interior is eleven blocks wide at the center, unobstructed.
        for (int x=1; x<=21; x++) for (int z=2; z<=19; z++) for (int y=1; y<=8; y++) {
            if (x!=1 && x!=21 && z!=2 && z!=19) continue;
            boolean gate = z==2 && x>=7 && x<=15 && y<=7;
            if (gate) continue;
            boolean glass = (y==4 || y==5) &&
                (((x==1 || x==21) && (z==6 || z==11 || z==16)) ||
                (z==19 && (x==5 || x==11 || x==17)));
            set(blocks,x,y,z,glass ? Blocks.TINTED_GLASS.defaultBlockState() :
                ((x*3+z+y)%9<=3 ? Blocks.GRAY_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState());
        }

        // Structural frame at the entrance and corners.
        for (int x:new int[]{1,21}) for(int z:new int[]{2,19})
            for (int y=1; y<=9; y++)
                set(blocks,x,y,z,Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        for (int x:new int[]{6,16}) for (int y=1;y<=8;y++)
            set(blocks,x,y,2,Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        for (int x=7;x<=15;x++) set(blocks,x,8,2,Blocks.IRON_BLOCK.defaultBlockState());

        // Yellow safety lanes run through the entire vehicle gate.
        for (int z=0;z<=11;z++) {
            set(blocks,7,0,z,Blocks.YELLOW_CONCRETE.defaultBlockState());
            set(blocks,15,0,z,Blocks.YELLOW_CONCRETE.defaultBlockState());
        }
        for (int x=8;x<=14;x++) for (int z=0;z<=5;z++)
            if ((x+z)%7==0)
                set(blocks,x,0,z,Blocks.YELLOW_CONCRETE.defaultBlockState());

        // A heavy flat roof, concrete walls with camo panels.
        for (int x=1;x<=21;x++) for(int z=2;z<=19;z++) {
            BlockState roof=((x*5+z*3)%13<4
                ? Blocks.GREEN_CONCRETE : Blocks.GRAY_CONCRETE).defaultBlockState();
            set(blocks,x,9,z,roof);
            if(x==1 || x==21 || z==2 || z==19)
                set(blocks,x,10,z,Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        }

        // H marking and landing lights (visual only, not a spawn point).
        for (int z=8;z<=13;z++)for(int x:new int[]{8,14})
            set(blocks,x,9,z,Blocks.WHITE_CONCRETE.defaultBlockState());
        for (int x=8;x<=14;x++)
            set(blocks,x,9,10,Blocks.WHITE_CONCRETE.defaultBlockState());
        for (int x:new int[]{4,18})for(int z:new int[]{4,17})
            set(blocks,x,9,z,Blocks.SEA_LANTERN.defaultBlockState());

        // Workshop corners and supplies, leaving drive-through area clear.
        for (int x:new int[]{3,19}) for (int z=5;z<=10;z+=2) {
            set(blocks,x,1,z,Blocks.IRON_BLOCK.defaultBlockState());
            set(blocks,x,2,z,Blocks.IRON_BARS.defaultBlockState());
        }
        for (int x:new int[]{3,4,18,19}) {
            set(blocks,x,1,17,Blocks.BARREL.defaultBlockState());
            set(blocks,x,2,17,Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState());
        }

        // Base Blacksmith furnace; Superior uses the same vanilla upgrade block.
        if (superior) {
            set(blocks,5,1,17,Blocks.BLAST_FURNACE.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
            for (int x=3;x<=19;x+=4)
                set(blocks,x,10,2,Blocks.SEA_LANTERN.defaultBlockState());
        } else {
            set(blocks,5,1,17,Blocks.FURNACE.defaultBlockState());
        }
        set(blocks,17,1,17,Blocks.SMITHING_TABLE.defaultBlockState());

        // Floodlights visible when leaving the gate.
        for (int x:new int[]{3,19}) {
            set(blocks,x,1,0,Blocks.IRON_BLOCK.defaultBlockState());
            set(blocks,x,2,0,Blocks.SEA_LANTERN.defaultBlockState());
        }

        ArrayList<BuildingBlock> result = new ArrayList<>(blocks.size());
        blocks.forEach((pos,state)->result.add(new BuildingBlock(pos,state)));
        return result;
    }

    private static void set(Map<BlockPos, BlockState> blocks,int x,int y,int z,BlockState state) {
        blocks.put(new BlockPos(x,y,z),state);
    }
}
