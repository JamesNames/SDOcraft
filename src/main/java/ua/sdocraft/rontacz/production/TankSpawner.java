package ua.sdocraft.rontacz.production;

import com.atsuishio.superbwarfare.entity.vehicle.M1A2Entity;
import com.atsuishio.superbwarfare.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import ua.sdocraft.rontacz.RonTacZ;
import ua.sdocraft.rontacz.entity.TankCrewUnit;

/** Builds an SBW entity and seats the player's RTS unit in its driver seat. */
public final class TankSpawner {
    private TankSpawner(){}
    public static void spawnTank(ServerLevel world, TankCrewUnit commander) {
        M1A2Entity tank=ModEntities.M_1A_2.get().create(world);
        if(tank==null){RonTacZ.LOGGER.error("Failed to create Superb Warfare M1A2");return;}
        BlockPos center=commander.blockPosition();
        BlockPos pos=findPosition(world,center);
        tank.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,commander.getYRot(),0);
        tank.setEnergy(tank.getMaxEnergy());
        tank.setEngineStart(true);
        tank.setEngineStartOver(true);
        if(!world.addFreshEntity(tank)){
            RonTacZ.LOGGER.error("Failed to add RTS M1A2 to the level");
            return;
        }
        commander.startRiding(tank,true);
        RonTacZ.LOGGER.info("RTS M1A2 spawned with commander {} for {}",commander.getId(),commander.getOwnerName());
    }
    private static BlockPos findPosition(ServerLevel world, BlockPos near) {
        for(int radius=5;radius<=14;radius+=3) {
            for(int angle=0;angle<8;angle++) {
                double a=angle*Math.PI/4;
                BlockPos pos=near.offset((int)Math.round(radius*Math.cos(a)),0,(int)Math.round(radius*Math.sin(a)));
                if(clear(world,pos)) return pos;
            }
        }
        return near.offset(8,2,8);
    }
    private static boolean clear(ServerLevel level,BlockPos center) {
        // Large armour requires an open floor with clearance.
        for(int x=-3;x<=3;x++)for(int z=-4;z<=4;z++) {
            BlockPos floor=center.offset(x,-1,z);
            if(!level.getBlockState(floor).isSolidRender(level,floor))return false;
            for(int h=0;h<4;h++)if(!level.getBlockState(center.offset(x,h,z)).isAir())return false;
        }
        return true;
    }
}
