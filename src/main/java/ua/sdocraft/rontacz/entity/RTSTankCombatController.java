package ua.sdocraft.rontacz.entity;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ua.sdocraft.rontacz.RonTacZ;
import ua.sdocraft.rontacz.faction.MilitaryFaction;

import java.util.List;

/** Server RTS combat driver for real SBW M1A2/T-90A cannons (not magical damage). */
public final class RTSTankCombatController {
    private RTSTankCombatController() {}
    private static final double COMBAT_RANGE = 105;
    private static final int FIRE_INTERVAL = 120;

    public static void tick(TankCrewUnit driver) {
        if (!(driver.level() instanceof ServerLevel world) || !driver.isAlive()) return;
        if (!(driver.getVehicle() instanceof VehicleEntity tank)) return;
        if (tank.getVehicleType() != VehicleType.TANK) return;
        if (!(driver.getClass() == TankCrewUnit.class || driver instanceof T90CrewUnit)
            || tank.isWreck() || tank.getFirstPassenger() != driver) return;
        if (tank.getHealth() <= 0) return;

        LivingEntity target = driver.getTarget();
        if (target == null || !isEnemy(driver, target)) {
            // Search sparingly. RoN's RTS drivers may not acquire vanilla AI targets.
            if (world.getGameTime() % 20 == tank.getId() % 20) {
                target = findOpponent(world, driver, tank);
                driver.setTarget(target);
            } else target = null;
        }
        if (target == null) {
            tank.setAiTurretTargetUUID("undefined");
            return;
        }
        driver.setTarget(target); // SBW's native Mob driver shooting requires mob.target
        tank.setAiTurretTargetUUID(target.getStringUUID());

        // Keep turret trained on the real vehicle bounding box rather than rider head.
        tank.turretAutoAimFromUuid(target.getStringUUID(), driver);
        if (world.getGameTime() % FIRE_INTERVAL != (tank.getId() % FIRE_INTERVAL)) return;

        GunData cannon = tank.getGunData(0, 0);
        if (cannon == null) return;
        // Training/test mode: refill one genuine SBW AP cannon shell when empty.
        // GunData still applies its own shooting/reload logic and projectile physics.
        if (cannon.ammo.get() <= 0) cannon.ammo.set(1);
        if (!cannon.canShoot(tank)) {
            RonTacZ.LOGGER.warn("RTS tank {} cannot fire: ammo={} selectedWeapon={} health={} target={}",
                tank.getId(), cannon.ammo.get(), tank.getWeaponIndex(0), tank.getHealth(), target.getId());
            return;
        }

        Entity victim = target.getVehicle() != null ? target.getVehicle() : target;
        Vec3 aim = victim.getBoundingBox().getCenter();
        tank.vehicleShoot(driver, target.getUUID(), aim);
        RonTacZ.LOGGER.info("RTS tank cannon fired: tank={} health={} target={} targetVehicle={} owner={}",
            tank.getId(), tank.getHealth(), target.getId(), victim.getId(), driver.getOwnerName());
    }

    private static boolean isEnemy(TankCrewUnit driver, LivingEntity candidate) {
        if (candidate == driver || !candidate.isAlive()) return false;
        if (candidate instanceof Monster && !(candidate instanceof RiflemanUnit)) return true;
        if (!(candidate instanceof RiflemanUnit other)) return false;
        String owner=driver.getOwnerName();
        String foe=other.getOwnerName();
        if (owner == null || foe == null || owner.equals(foe)) return false;
        MilitaryFaction.Side mySide=MilitaryFaction.sideOf(driver.level(), owner);
        MilitaryFaction.Side theirSide=MilitaryFaction.sideOf(candidate.level(), foe);
        return mySide == null || theirSide == null || mySide != theirSide;
    }

    private static LivingEntity findOpponent(ServerLevel level, TankCrewUnit driver, VehicleEntity tank) {
        List<LivingEntity> near=level.getEntitiesOfClass(
            LivingEntity.class, new AABB(tank.blockPosition()).inflate(COMBAT_RANGE),
            soldier -> isEnemy(driver,soldier));
        // Engage opposing armor first.
        LivingEntity best=null;
        double bestDistance=COMBAT_RANGE*COMBAT_RANGE;
        for (LivingEntity other:near) {
            double dist=tank.position().distanceToSqr(other.position());
            if (other.getVehicle() instanceof VehicleEntity && dist<bestDistance) {
                best=other;
                bestDistance=dist;
            }
        }
        if (best!=null) return best;
        for (LivingEntity other:near) {
            double dist=tank.position().distanceToSqr(other.position());
            if (dist<bestDistance) {best=other;bestDistance=dist;}
        }
        return best;
    }
}
