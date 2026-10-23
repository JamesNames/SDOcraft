package ua.sdocraft.rontacz.entity;

import com.solegendary.reignofnether.unit.goals.MoveToTargetBlockGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;

import javax.annotation.Nullable;

/**
 * RTS move orders are stored on the RoN unit, while vehicle input is routed to SBW.
 * Simple direct bearing controller: good on open terrain, not yet a full vehicle pathfinder.
 */
public final class TankMoveGoal extends MoveToTargetBlockGoal {
    private final TankCrewUnit commander;

    public TankMoveGoal(TankCrewUnit commander) {
        super(commander, false, 0);
        this.commander = commander;
    }

    @Override public void setMoveTarget(@Nullable BlockPos target) {
        this.moveTarget = target;
    }
    @Override public boolean canUse() {
        return moveTarget != null && commander.getVehicle() instanceof VehicleEntity;
    }
    @Override public boolean canContinueToUse() {
        return canUse();
    }
    @Override public void start() { }
    @Override public void stop() { }
    @Override public boolean isAtDestination() {
        return moveTarget == null || commander.getVehicle().blockPosition().distSqr(moveTarget) <= 25;
    }
    @Override public void stopMoving() {
        this.moveTarget = null;
        if (commander.getVehicle() instanceof VehicleEntity tank) tank.processInput((short)0);
    }
    @Override public void tick() {
        if (!(commander.getVehicle() instanceof VehicleEntity tank) || moveTarget == null) return;
        double dx = moveTarget.getX()+0.5-tank.getX();
        double dz = moveTarget.getZ()+0.5-tank.getZ();
        double distSq = dx*dx+dz*dz;
        if (distSq <= 25) {
            stopMoving();
            return;
        }
        float desired = (float)Math.toDegrees(Math.atan2(-dx,dz));
        float diff = Mth.wrapDegrees(desired-tank.getYRot());
        short keys = 0;
        if (diff > 8) keys |= 2;        // right turn
        else if (diff < -8) keys |= 1;  // left turn
        if (Math.abs(diff) < 65) keys |= 4; // forward
        tank.processInput(keys);
    }
}
