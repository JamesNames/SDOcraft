package ua.sdocraft.rontacz.entity;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solegendary.reignofnether.unit.goals.MoveToTargetBlockGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;
import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;

public class TankCrewUnit extends RiflemanUnit {
    private TankMoveGoal tankMoveGoal;

    public TankCrewUnit(EntityType<? extends Pillager> type, Level level) { super(type,level); }

    @Override protected void registerGoals() {
        super.registerGoals();
        Goal old = super.getMoveGoal();
        if (old != null) this.goalSelector.removeGoal(old);
        this.tankMoveGoal = new TankMoveGoal(this);
        this.goalSelector.addGoal(3, tankMoveGoal);
    }
    @Override public MoveToTargetBlockGoal getMoveGoal() {
        return tankMoveGoal != null ? tankMoveGoal : super.getMoveGoal();
    }
    /** RTS hitbox on the occupied vehicle, not the hidden rider. */
    @Override public AABB getInflatedSelectionBox() {
        if (getVehicle() instanceof VehicleEntity tank)
            return tank.getBoundingBox().inflate(0.8, 0.8, 1.0);
        return super.getInflatedSelectionBox();
    }

    @Override protected float gunRange() { return 64f; }
    @Override protected float shotCooldown() { return 100f; }

    @Override public void performUnitRangedAttack(LivingEntity enemy, float velocity) {
        // SBW owns turret alignment, projectiles, ballistics and ammo handling.
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (!(getVehicle() instanceof VehicleEntity tank)) return;
        if (tank.getFirstPassenger() != this || tank.isRemoved()) return;
        if (tank.getVehicleType() != VehicleType.HELICOPTER && !tank.getEngineStart()) tank.setEngineStart(true);
        if (tank.getVehicleType() != VehicleType.HELICOPTER && !tank.getEngineStartOver()) tank.setEngineStartOver(true);
        // Energy is fuel, not health. No per-tick refueling or HP reset.
        // Driving is handled once per server level tick by TankServerEvents.
        // Combat targeting is owned exclusively by RTSTankCombatController.
        // No automatic target selection or ammunition changes here.
    }
}
