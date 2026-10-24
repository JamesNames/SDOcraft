package ua.sdocraft.rontacz.entity;

import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;
import com.atsuishio.superbwarfare.entity.projectile.IglaMissileEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ua.sdocraft.rontacz.faction.MilitaryFaction;
import ua.sdocraft.rontacz.RonTacZ;

import java.util.UUID;

/**
 * 9K38 Igla MANPADS RTS anti-air unit.
 * A vehicle pilot is an invisible RoN unit riding inside the real Superb Warfare
 * hitbox. Base RoN LOS checks that hidden rider instead of the visible aircraft,
 * and cannot reliably make the Igla track it.
 */
public final class IglaUnit extends MissileSpecialistUnit {
    private UUID assignedAircraft;
    private int assignedUntilTick=0;
    private int lastLaunchTick=-180;

    public IglaUnit(EntityType<? extends Pillager> type, Level level) {super(type,level);}
    @Override protected Item launcher() {return ModItems.IGLA_9K38.get();}
    @Override protected int fireInterval() {return 145;}
    @Override protected float engagementRange() {return 150f;}

    private VehicleEntity aircraft(LivingEntity target) {
        if(target!=null && target.getVehicle() instanceof VehicleEntity v
            && (v.getVehicleType()==VehicleType.HELICOPTER
                || v.getVehicleType()==VehicleType.AIRPLANE))
            return v;
        return null;
    }

    @Override protected boolean canEngage(LivingEntity target) {
        return target!=null && target.isAlive() && aircraft(target)!=null
            && !isFriendly(target);
    }

    private boolean isFriendly(LivingEntity target) {
        if(isAlliedTo(target))return true;
        if(target instanceof RiflemanUnit soldier) {
            String a=getOwnerName(),b=soldier.getOwnerName();
            if(a!=null && a.equals(b))return true;
            MilitaryFaction.Side mySide=MilitaryFaction.sideOf(level(),a);
            MilitaryFaction.Side otherSide=MilitaryFaction.sideOf(level(),b);
            return mySide!=null && mySide==otherSide;
        }
        return false;
    }

    private boolean hasVehicleLineOfSight(VehicleEntity air) {
        Vec3 from=getEyePosition(), to=air.getBoundingBox().getCenter();
        return level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel world))return;

        // Combat target is the RTS attack order, not a nearby random friendly.
        LivingEntity selected=getTargetGoal()!=null?getTargetGoal().getTarget():null;
        if(canEngage(selected)) {
            VehicleEntity air=aircraft(selected);
            double distSq=air.distanceToSqr(this);
            if(distSq<=engagementRange()*engagementRange()
                && tickCount-lastLaunchTick>=fireInterval()
                && hasVehicleLineOfSight(air))
                performUnitRangedAttack(selected,20f);
        }

        // Lock the REAL aircraft UUID, not its invisible, moving pilot seat.
        // Reapply for the first 6 sec as the SBW missile is created/ticked on
        // the server. Never guide toward neutral or allied aircraft.
        if(assignedAircraft!=null && tickCount<=assignedUntilTick && tickCount%2==0) {
            Entity target=world.getEntity(assignedAircraft);
            if(!(target instanceof VehicleEntity air) || air.isRemoved() || air.isWreck()) {
                assignedAircraft=null;
                return;
            }
            var missiles=world.getEntitiesOfClass(IglaMissileEntity.class,
                getBoundingBox().inflate(160), m->m.getOwner()==this && m.tickCount<130);
            for (IglaMissileEntity missile : missiles) {
                if(!assignedAircraft.toString().equals(missile.getTargetUUID())) {
                    missile.setTargetUUID(assignedAircraft.toString());
                    missile.setLostTarget(false);
                    missile.setLost(false);
                }
            }
        } else if(tickCount>assignedUntilTick)assignedAircraft=null;
    }

    @Override protected boolean canSeeTargetForLaunch(LivingEntity target, Entity actual) {
        return actual instanceof VehicleEntity air && hasVehicleLineOfSight(air);
    }

    @Override protected void afterMissileLaunch(LivingEntity target, Entity actual) {
        if(actual instanceof VehicleEntity air) {
            lastLaunchTick=tickCount;
            assignedAircraft=air.getUUID();
            assignedUntilTick=tickCount+125;
            RonTacZ.LOGGER.info("Igla RTS air lock: shooter={} aircraft={} y={} distance={}",
                getId(),air.getId(),air.getY(),distanceTo(air));
        }
    }
}
