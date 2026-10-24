package ua.sdocraft.rontacz.entity;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.init.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ua.sdocraft.rontacz.RonTacZ;
import ua.sdocraft.rontacz.faction.MilitaryFaction;

/**
 * Player-command-only vehicle combat. Never search for nearby mobs.
 *
 * RoN directly sets SelectedTargetGoal.forced for an explicit ATTACK order.
 * The driver's ordinary Mob.getTarget() is not authoritative; passive vanilla
 * aggro caused friendly fire in v0.4.6.
 */
public final class RTSTankCombatController {
    private RTSTankCombatController() {}

    private static final double MAX_RANGE = 180.0;
    private static final int TANK_RELOAD_TICKS = 110;
    private static final int HELI_ROCKET_TICKS = 45;

    public static void tick(TankCrewUnit driver) {
        if (!(driver.level() instanceof ServerLevel world) || !driver.isAlive()) return;
        if (!(driver.getVehicle() instanceof VehicleEntity vehicle)) return;
        if (vehicle.isWreck() || vehicle.getHealth() <= 0
            || vehicle.getFirstPassenger() != driver) return;

        InfiniteVehicleAmmo.tick(vehicle, world.getGameTime());

        // If a player gave a MOVE / STOP command, the forced target is cleared
        // by Reign of Nether. No shooting, no scanning, no fallback enemies.
        var selected = driver.getTargetGoal();
        LivingEntity target = selected != null && selected.forced
            ? selected.getTarget() : null;
        if (target == null || !isExplicitTargetValid(driver, target)) {
            vehicle.setAiTurretTargetUUID("undefined");
            // Suppress residual vanilla targeting and SBW native auto-fire.
            if (driver.getTarget() != null) driver.setTarget(null);
            return;
        }

        Entity victim = target.getVehicle() instanceof VehicleEntity t
            ? t : target;
        if (victim == vehicle || !victim.isAlive()) return;

        Vec3 aim = victim.getBoundingBox().getCenter();
        double distanceSqr = aim.distanceToSqr(vehicle.getBoundingBox().getCenter());
        if (distanceSqr > MAX_RANGE * MAX_RANGE) return;

        // Only a direct player target reaches this point.
        driver.setTarget(target);
        vehicle.setAiTurretTargetUUID(target.getStringUUID());
        if (driver instanceof SodayoTowCrewUnit) {
            aimTow(vehicle,aim);
            if (world.getGameTime() % 160 != vehicle.getId() % 160) return;
            fireTow(world,vehicle,driver,target,victim,aim);
        } else if (vehicle.getVehicleType() == VehicleType.TANK) {
            aimTank(vehicle, aim);
            if (world.getGameTime() % TANK_RELOAD_TICKS != vehicle.getId() % TANK_RELOAD_TICKS) return;
            fireTank(world, vehicle, driver, target, victim, aim);
        } else if (driver instanceof Mi28CrewUnit
                && vehicle.getVehicleType() == VehicleType.HELICOPTER) {
            aimHelicopter(vehicle, aim);
            if (world.getGameTime() % HELI_ROCKET_TICKS != vehicle.getId() % HELI_ROCKET_TICKS) return;
            fireHelicopter(world, vehicle, driver, target, victim, aim);
        }
    }


    private static void aimTow(VehicleEntity tow, Vec3 aim) {
        Vec3 origin=tow.getShootPos("Missile",1f);
        Vec3 diff=aim.subtract(origin);
        if(diff.lengthSqr()>0.01) tow.turretAutoAimFromVector(diff.normalize());
    }

    private static void fireTow(ServerLevel world, VehicleEntity tow, TankCrewUnit driver,
                                LivingEntity target, Entity victim, Vec3 aim) {
        GunData missile=tow.getGunData("Missile");
        if(missile==null)return;
        Vec3 launch=tow.getShootPos("Missile",1f);
        Vec3 desired=aim.subtract(launch).normalize();
        Vec3 barrel=tow.getBarrelVector(1f).normalize();
        if(desired.dot(barrel)<0.970)return;
        if(!missile.canShoot(tow)) {
            RonTacZ.LOGGER.warn("RTS Sodayo TOW cannot fire: vehicle={} ammo={} target={}",
                tow.getId(),missile.ammo.get(),victim.getId());
            return;
        }
        tow.vehicleShoot(driver,"Missile",target.getUUID(),aim);
        RonTacZ.LOGGER.info("RTS Sodayo TOW missile launched: vehicle={} target={}",
            tow.getId(),victim.getId());
    }

    private static void aimTank(VehicleEntity tank, Vec3 aim) {
        // Drive the real turret, not the transform of an invisible RTS rider.
        Vec3 barrelOrigin=tank.getShootPos("Cannon", 1f);
        Vec3 desired=aim.subtract(barrelOrigin);
        if (desired.lengthSqr() > 0.1) tank.turretAutoAimFromVector(desired.normalize());
    }

    private static void aimHelicopter(VehicleEntity heli, Vec3 aim) {
        // The Mi-28 pilot has Rocket / DriverMissile, not the gunner's Cannon.
        // Level flight remains controlled by HelicopterFlightController.
        Vec3 diff=aim.subtract(heli.position());
        if (diff.lengthSqr() > 1.0) {
            float yaw=(float)Math.toDegrees(Math.atan2(-diff.x,diff.z));
            float pitch=(float)Math.toDegrees(Math.atan2(-diff.y, Math.hypot(diff.x,diff.z)));
            heli.setYRot(Mth.approachDegrees(heli.getYRot(),yaw,6.0f));
            heli.setXRot(Mth.approach(heli.getXRot(),Mth.clamp(pitch,-45f,55f),4.0f));
        }
    }

    private static void fireTank(ServerLevel world, VehicleEntity vehicle, TankCrewUnit driver,
                                 LivingEntity target, Entity victim, Vec3 aim) {
        GunData gun=vehicle.getGunData("Cannon");
        if (gun == null) {
            RonTacZ.LOGGER.warn("RTS tank {}: cannon weapon not found",vehicle.getId());
            return;
        }
        Vec3 muzzle=vehicle.getShootPos("Cannon", 1f);
        Vec3 desired=aim.subtract(muzzle).normalize();
        Vec3 barrel=vehicle.getBarrelVector(1f).normalize();
        if (desired.dot(barrel) < 0.978) return; // within ~12 degrees
        // The player explicitly ordered this target: friendly units may be targeted.
        // Never pick alternative targets automatically.

        // Sandbox prototype: one real SBW round. The native gun decides if
        // reload/heat prevents firing and creates the genuine projectile.
        // Real cannon rounds are supplied by InfiniteVehicleAmmo.
        if (!gun.canShoot(vehicle)) {
            RonTacZ.LOGGER.warn("RTS tank {}: cannon not ready; ammo={} victim={}",
                vehicle.getId(),gun.ammo.get(),victim.getId());
            return;
        }
        vehicle.vehicleShoot(driver,"Cannon",target.getUUID(),aim);
        RonTacZ.LOGGER.info("RTS ordered tank fire: vehicle={}, target={}",
            vehicle.getId(),victim.getId());
    }

    private static void fireHelicopter(ServerLevel world, VehicleEntity heli,
                                       TankCrewUnit driver, LivingEntity target,
                                       Entity victim, Vec3 aim) {
        GunData rockets=heli.getGunData("Rocket");
        if (rockets == null) {
            RonTacZ.LOGGER.warn("Mi-28 {} missing pilot Rocket weapon",heli.getId());
            return;
        }
        Vec3 launch=heli.getShootPos("Rocket",1f);
        Vec3 desired=aim.subtract(launch).normalize();
        Vec3 actualRocketDirection=heli.getShootVec("Rocket",1f).normalize();
        // High-altitude shots require a downward pitch. Align the actual SBW rocket
        // direction rather than checking a level helicopter's forward direction.
        if (desired.dot(actualRocketDirection) < 0.80) {
            if (world.getGameTime() % 40 == 0)
                RonTacZ.LOGGER.info("Mi-28 aiming: vehicle={} alignment={} pitch={} yaw={}",
                    heli.getId(), desired.dot(actualRocketDirection), heli.getXRot(), heli.getYRot());
            return;
        }
        // Explicit orders may attack either side; do not block on faction.
        // Rocket magazine and native ammo box are replenished by InfiniteVehicleAmmo.
        if (!rockets.canShoot(heli)) {
            RonTacZ.LOGGER.warn("Mi-28 {} rockets not ready ammo={} target={}",
                heli.getId(),rockets.ammo.get(),victim.getId());
            return;
        }
        heli.vehicleShoot(driver,"Rocket",target.getUUID(),aim);
        RonTacZ.LOGGER.info("RTS ordered Mi-28 rockets: helicopter={}, target={}",
            heli.getId(),victim.getId());
    }

    /** A fired cannon/rocket can splash-damage friendlies near the target or path. */
    private static boolean clearFriendlyLine(ServerLevel level,TankCrewUnit driver,
                                             VehicleEntity shooter,Vec3 from,Vec3 to,
                                             double blastRadius) {
        AABB corridor=new AABB(from,to).inflate(blastRadius);
        for(LivingEntity other:level.getEntitiesOfClass(LivingEntity.class,corridor)) {
            if(other == driver || !other.isAlive() || other.getVehicle() == shooter)continue;
            if(!isFriendly(driver,other))continue;
            Entity body=other.getVehicle() instanceof VehicleEntity veh?veh:other;
            Vec3 p=body.getBoundingBox().getCenter();
            if(p.distanceToSqr(to) < blastRadius*blastRadius) return false;
            // shortest distance of friendly vehicle to projectile line
            Vec3 line=to.subtract(from);
            double len=line.lengthSqr();
            if(len<0.01) return false;
            double t=net.minecraft.util.Mth.clamp(p.subtract(from).dot(line)/len,0,1);
            if(from.add(line.scale(t)).distanceToSqr(p) < 4.0*4.0) return false;
        }
        return true;
    }

    private static boolean isFriendly(TankCrewUnit shooter, LivingEntity other) {
        if(other==shooter) return true;
        if(shooter.isAlliedTo(other)) return true;
        if(other instanceof RiflemanUnit unit) {
            String a=shooter.getOwnerName(), b=unit.getOwnerName();
            if(a!=null && a.equals(b)) return true;
            MilitaryFaction.Side sa=MilitaryFaction.sideOf(shooter.level(),a);
            MilitaryFaction.Side sb=MilitaryFaction.sideOf(unit.level(),b);
            return sa!=null && sa==sb;
        }
        return false;
    }

    /** Explicit A+click overrides team ownership but never allows self-targeting. */
    private static boolean isExplicitTargetValid(TankCrewUnit shooter, LivingEntity target) {
        if (target == shooter || !target.isAlive()) return false;
        Entity mounted=target.getVehicle();
        if (mounted == shooter.getVehicle()) return false;
        if (target instanceof Player player)
            return !player.isCreative() && !player.isSpectator();
        return true;
    }
}
