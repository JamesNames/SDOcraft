package ua.sdocraft.rontacz.entity;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solegendary.reignofnether.unit.goals.MoveToTargetBlockGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;
import ua.sdocraft.rontacz.RonTacZ;

import javax.annotation.Nullable;

/**
 * Reign of Nether move goal with TWO modes:
 * walking = native RoN ground navigation,
 * mounted = Superb Warfare controls with collision-aware fallback if the vehicle
 * refuses input for more than ~2 seconds.
 */
public final class TankMoveGoal extends MoveToTargetBlockGoal {
    private final TankCrewUnit commander;
    private long lastDriveTick = Long.MIN_VALUE;
    private Vec3 previousTankPosition = null;
    private int noProgressTicks = 0;
    private boolean warnedFallback = false;

    public TankMoveGoal(TankCrewUnit commander) {
        super(commander, false, 0);
        this.commander = commander;
    }

    private VehicleEntity tank() {
        return commander.getVehicle() instanceof VehicleEntity tank ? tank : null;
    }

    @Override
    public void setMoveTarget(@Nullable BlockPos target) {
        VehicleEntity tank = tank();
        if (tank == null) {
            super.setMoveTarget(target);
            return;
        }
        moveTarget = target;
        noProgressTicks = 0;
        previousTankPosition = tank.position();
        warnedFallback = false;
        if (target == null) tank.processInput((short) 0);
        else RonTacZ.LOGGER.info("M1A2 RTS move order: commander={}, target={}, vehicle={}, seat={}",
            commander.getId(), target, tank.getId(), tank.getSeatIndex(commander));
    }

    @Override public boolean canUse() { return tank() != null ? moveTarget != null : super.canUse(); }
    @Override public boolean canContinueToUse() { return tank() != null ? moveTarget != null : super.canContinueToUse(); }
    @Override public void start() { if (tank() == null) super.start(); }
    @Override public void stop() { if (tank() == null) super.stop(); }
    @Override
    public boolean isAtDestination() {
        VehicleEntity tank = tank();
        if (tank == null) return super.isAtDestination();
        if (moveTarget == null) return true;
        double dx = tank.getX() - (moveTarget.getX() + 0.5);
        double dz = tank.getZ() - (moveTarget.getZ() + 0.5);
        return dx * dx + dz * dz <= 25;
    }

    @Override
    public void stopMoving() {
        VehicleEntity tank = tank();
        if (tank == null) super.stopMoving();
        else {
            moveTarget = null;
            tank.processInput((short) 0);
            previousTankPosition = null;
            noProgressTicks = 0;
        }
    }

    @Override
    public void tick() {
        if (tank() == null) super.tick();
        // Server-level RTS driver ticks independently of passenger AI goals.
    }

    /** Called on the server every level tick; never relies on passenger AI ticking. */
    public void driveTank() {
        VehicleEntity tank = tank();
        if (tank == null) return;
        long worldTick = tank.level().getGameTime();
        if (lastDriveTick == worldTick) return;
        lastDriveTick = worldTick;
        if (moveTarget == null) {
            if (tank.getVehicleType() == VehicleType.HELICOPTER && !tank.onGround())
                HelicopterFlightController.hold(tank);
            else tank.processInput((short) 0);
            previousTankPosition = tank.position();
            noProgressTicks = 0;
            return;
        }
        if (!tank.getPassengers().contains(commander)) {
            RonTacZ.LOGGER.warn("M1A2 driver {} is no longer in tank {}", commander.getId(), tank.getId());
            return;
        }
        if (tank.isWreck()) {
            tank.processInput((short) 0);
            return;
        }

        // Helicopters use native rotor thrust, mouse yaw and pitch, not tank
        // grousers. The same selectable RTS crew delivers the destination.
        if (tank.getVehicleType() == VehicleType.HELICOPTER) {
            HelicopterFlightController.flyTo(tank, moveTarget, worldTick);
            double hx = tank.getX() - (moveTarget.getX() + 0.5);
            double hz = tank.getZ() - (moveTarget.getZ() + 0.5);
            if (hx * hx + hz * hz <= 36.0 && !tank.onGround()) {
                stopMoving();
                HelicopterFlightController.hold(tank);
            }
            return;
        }

        // Restore energy so the prototype can be tested without refuelling.
        if (tank.getEnergy() < 10000) tank.setEnergy(tank.getMaxEnergy());
        tank.setEngineStart(true);
        tank.setEngineStartOver(true);

        double dx = moveTarget.getX() + 0.5 - tank.getX();
        double dz = moveTarget.getZ() + 0.5 - tank.getZ();
        double d2 = dx * dx + dz * dz;
        if (d2 <= 25) {
            stopMoving();
            return;
        }

        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float deltaYaw = Mth.wrapDegrees(desiredYaw - tank.getYRot());
        short keys = 0;
        // Superb Warfare tracked engine applies NEGATIVE yaw for LEFT (bit 0),
        // and POSITIVE yaw for RIGHT (bit 1).  Previous versions reversed this.
        if (deltaYaw > 8f) keys |= 2;       // turn right -> yaw increases
        else if (deltaYaw < -8f) keys |= 1; // turn left -> yaw decreases
        if (Math.abs(deltaYaw) < 68f) keys |= 4; // drive forward only when aligned
        tank.processInput(keys);

        if (worldTick % 60L == 0L) {
            RonTacZ.LOGGER.info("M1A2 RTS drive: tank={} pos={} target={} yaw={} desiredYaw={} error={} keys={} speed={} onGround={} energy={}",
                tank.getId(), tank.blockPosition(), moveTarget, tank.getYRot(), desiredYaw, deltaYaw,
                (int) keys, tank.getDeltaMovement().horizontalDistance(), tank.onGround(), tank.getEnergy());
        }

        Vec3 current = tank.position();
        if (previousTankPosition != null
                && current.subtract(previousTankPosition).horizontalDistanceSqr() < 0.0004) {
            noProgressTicks++;
        } else noProgressTicks = 0;
        previousTankPosition = current;

        // Safety fallback for SBW builds where non-player driver inputs do not advance
        // the chassis. Respect SBW's collision-aware Entity.move implementation.
        if (noProgressTicks > 45) {
            if (!warnedFallback) {
                warnedFallback = true;
                RonTacZ.LOGGER.warn("M1A2 {} not moving despite RTS order, using collision-aware driver fallback; seat={} energy={} engineInfo={} target={}",
                    tank.getId(), tank.getSeatIndex(commander), tank.getEnergy(),
                    tank.getEngineInfo() != null, moveTarget);
            }
            tank.setYRot(Mth.approachDegrees(tank.getYRot(), desiredYaw, 4f));
            if (Math.abs(Mth.wrapDegrees(desiredYaw - tank.getYRot())) < 24f) {
                double invDist = 1.0 / Math.sqrt(d2);
                Vec3 step = new Vec3(dx * invDist * 0.10, 0, dz * invDist * 0.10);
                tank.move(MoverType.SELF, step);
                tank.setDeltaMovement(tank.getDeltaMovement().add(step.scale(0.04)));
                tank.hasImpulse = true;
            }
        }
    }
}
