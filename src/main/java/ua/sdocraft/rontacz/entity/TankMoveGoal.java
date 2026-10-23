package ua.sdocraft.rontacz.entity;

import com.solegendary.reignofnether.unit.goals.MoveToTargetBlockGoal;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import javax.annotation.Nullable;

/**
 * When mounted: direct steering of SBW tracked M1A2.
 * When dismounted: regular Reign of Nether infantry pathfinding.
 */
public final class TankMoveGoal extends MoveToTargetBlockGoal {
    private final TankCrewUnit commander;

    public TankMoveGoal(TankCrewUnit commander) {
        super(commander, false, 0);
        this.commander = commander;
    }

    private VehicleEntity vehicle() {
        return commander.getVehicle() instanceof VehicleEntity v ? v : null;
    }

    @Override public void setMoveTarget(@Nullable BlockPos target) {
        VehicleEntity tank = vehicle();
        if (tank != null) {
            moveTarget = target;
            if (target == null) tank.processInput((short) 0);
        } else super.setMoveTarget(target);
    }

    @Override public boolean canUse() {
        return vehicle() != null ? moveTarget != null : super.canUse();
    }

    @Override public boolean canContinueToUse() {
        return vehicle() != null ? moveTarget != null : super.canContinueToUse();
    }

    @Override public void start() {
        if (vehicle() == null) super.start();
    }

    @Override public void stop() {
        if (vehicle() == null) super.stop();
    }

    @Override public boolean isAtDestination() {
        VehicleEntity tank = vehicle();
        if (tank == null) return super.isAtDestination();
        if (moveTarget == null) return true;
        double dx = tank.getX() - (moveTarget.getX() + 0.5);
        double dz = tank.getZ() - (moveTarget.getZ() + 0.5);
        return dx * dx + dz * dz <= 25;
    }

    @Override public void stopMoving() {
        VehicleEntity tank = vehicle();
        if (tank == null) super.stopMoving();
        else {
            moveTarget = null;
            tank.processInput((short) 0);
        }
    }

    @Override public void tick() {
        if (vehicle() == null) super.tick();
    }

    /** Called by commander.tick(), so movement works even if mounted mob AI goals pause. */
    public void driveTank() {
        VehicleEntity tank = vehicle();
        if (tank == null) return;
        if (moveTarget == null || tank.getFirstPassenger() != commander) {
            tank.processInput((short) 0);
            return;
        }
        double dx = (moveTarget.getX() + 0.5) - tank.getX();
        double dz = (moveTarget.getZ() + 0.5) - tank.getZ();
        double d2 = dx * dx + dz * dz;
        if (d2 <= 25) { stopMoving(); return; }

        float desired = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float diff = Mth.wrapDegrees(desired - tank.getYRot());
        short input = 0;
        // SBW tracked engine: left -> yaw increases, right -> yaw decreases.
        if (diff > 8f) input |= 1;
        else if (diff < -8f) input |= 2;
        if (Math.abs(diff) < 65f) input |= 4;
        tank.processInput(input);
    }
}
