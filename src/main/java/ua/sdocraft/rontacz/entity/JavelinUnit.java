package ua.sdocraft.rontacz.entity;

import com.atsuishio.superbwarfare.entity.projectile.JavelinMissileEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;
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
import ua.sdocraft.rontacz.RonTacZ;
import ua.sdocraft.rontacz.faction.MilitaryFaction;
import java.util.UUID;

/**
 * Player-ordered Javelin lock for real Superb Warfare vehicle entities.
 * RoN selects the seated tank crew; its vehicle is the actual tank entity.
 */
public final class JavelinUnit extends MissileSpecialistUnit {
    private int lastLaunchTick = -300;
    private UUID trackedVehicle;
    private int trackedUntilTick = -1;

    public JavelinUnit(EntityType<? extends Pillager> type, Level level) { super(type, level); }
    @Override protected Item launcher() { return ModItems.JAVELIN.get(); }
    @Override protected int fireInterval() { return 165; }
    @Override protected float engagementRange() { return 110f; }

    private VehicleEntity tank(LivingEntity target) {
        if (target == null) return null;
        if (target.getVehicle() instanceof VehicleEntity vehicle
            && (vehicle.getVehicleType() == VehicleType.TANK
                || vehicle.getVehicleType() == VehicleType.APC)) return vehicle;
        return null;
    }

    private boolean friendly(LivingEntity target) {
        if (isAlliedTo(target)) return true;
        if (target instanceof RiflemanUnit soldier) {
            String own = getOwnerName();
            String other = soldier.getOwnerName();
            if (own != null && own.equals(other)) return true;
            MilitaryFaction.Side mine = MilitaryFaction.sideOf(level(), own);
            MilitaryFaction.Side theirs = MilitaryFaction.sideOf(level(), other);
            return mine != null && mine == theirs;
        }
        return false;
    }

    @Override protected boolean canEngage(LivingEntity target) {
        return target != null && target.isAlive() && tank(target) != null && !friendly(target);
    }

    private boolean canSeeTank(VehicleEntity vehicle) {
        Vec3 start = getEyePosition();
        Vec3 dest = vehicle.getBoundingBox().getCenter();
        return level().clip(new ClipContext(start, dest,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
            .getType() == HitResult.Type.MISS;
    }

    @Override protected boolean canSeeTargetForLaunch(LivingEntity target, Entity actual) {
        return actual instanceof VehicleEntity vehicle && canSeeTank(vehicle);
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel world)) return;
        // Explicit ATTACK command only; never scan for nearby tanks or allies.
        var order = getTargetGoal();
        LivingEntity chosen = order != null && order.forced ? order.getTarget() : null;
        VehicleEntity vehicle = tank(chosen);
        if (vehicle != null && canEngage(chosen)
            && this.distanceToSqr(vehicle) <= engagementRange() * engagementRange()
            && tickCount - lastLaunchTick >= fireInterval()
            && canSeeTank(vehicle)) {
            performUnitRangedAttack(chosen, 20f);
        }

        // Native SBW Javelin missiles must lock the visible vehicle UUID,
        // NOT the invisible RTS driver. Reapply for six seconds after launch.
        if (trackedVehicle != null && tickCount <= trackedUntilTick && tickCount % 2 == 0) {
            Entity target = world.getEntity(trackedVehicle);
            if (!(target instanceof VehicleEntity veh) || veh.isWreck() || veh.isRemoved()) {
                trackedVehicle = null;
                return;
            }
            var missiles = world.getEntitiesOfClass(JavelinMissileEntity.class,
                getBoundingBox().inflate(180), missile ->
                    missile.getOwner() == this && missile.tickCount < 140);
            for (JavelinMissileEntity missile : missiles) {
                if (!trackedVehicle.toString().equals(missile.getTargetUUID())) {
                    missile.setTargetUUID(trackedVehicle.toString());
                    missile.setGuideType(0);
                }
            }
        } else if (tickCount > trackedUntilTick) {
            trackedVehicle = null;
        }
    }

    @Override protected void afterMissileLaunch(LivingEntity target, Entity actual) {
        if (actual instanceof VehicleEntity vehicle) {
            trackedVehicle = vehicle.getUUID();
            trackedUntilTick = tickCount + 125;
            lastLaunchTick = tickCount;
            RonTacZ.LOGGER.info("Javelin RTS tank lock: shooter={} tank={} UUID={} distance={}",
                getId(), vehicle.getId(), vehicle.getUUID(), distanceTo(vehicle));
        }
    }
}
