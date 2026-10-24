package ua.sdocraft.rontacz.entity;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import ua.sdocraft.rontacz.RonTacZ;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RTS experimental controlled-flight mode for the SBW Mi-28.
 * Its normal pilot flight model requires analog mouse inputs and has no
 * stable altitude PID; do NOT drive its unbounded rotor thrust directly.
 * Instead, apply small per-tick collision checked displacements on the server.
 * This is a prototype, not native aviation physics/pathfinding.
 */
public final class HelicopterFlightController {
    private HelicopterFlightController() { }
    private static final Map<UUID, Vec3> hoverTargets = new HashMap<>();

    public static void release(VehicleEntity heli) {
        hoverTargets.remove(heli.getUUID());
        heli.setNoGravity(false);
        heli.setPower(0f);
        heli.mouseInput(0, 0);
        heli.processInput((short)0);
    }

    private static void prepare(VehicleEntity heli) {
        if (heli.isWreck()) return;
        if (heli.getEnergy() < 20000) heli.setEnergy(heli.getMaxEnergy());
        heli.setNoGravity(true);
        heli.setEngineStart(true);
        heli.setEngineStartOver(true);
        heli.setHoverMode(false);
        // Keep the rotors visualized with only minimal native lift.
        // Never use power 0.12+; SBW converts that into sustained upward acceleration.
        heli.setPower(0.006f);
        heli.processInput((short)0);
        heli.mouseInput(0, 0);
        heli.setXRot(Mth.approach(heli.getXRot(), 0f, 2.5f));
        heli.setZRot(0f);
    }

    private static void applyTarget(VehicleEntity heli, Vec3 dest, double maxSpeed) {
        Vec3 delta = dest.subtract(heli.position());
        double horizontal = Math.hypot(delta.x, delta.z);
        if (horizontal > 1.0) {
            float desiredYaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
            heli.setYRot(Mth.approachDegrees(heli.getYRot(), desiredYaw, 4.0f));
        }
        double vx = horizontal > 0.1 ? Mth.clamp(delta.x, -maxSpeed, maxSpeed) : 0;
        double vz = horizontal > 0.1 ? Mth.clamp(delta.z, -maxSpeed, maxSpeed) : 0;
        double vy = Mth.clamp(delta.y, -0.18, 0.18);

        // End-of-level tick: native SBW physics has already run.
        // Entity.move checks collision rather than teleporting through blocks.
        heli.move(MoverType.SELF, new Vec3(vx, vy, vz));
        heli.setDeltaMovement(Vec3.ZERO);
        heli.hasImpulse = true;
    }

    public static void hold(VehicleEntity heli) {
        if (heli.isWreck()) return;
        prepare(heli);
        Vec3 anchor = hoverTargets.computeIfAbsent(heli.getUUID(), ignored -> heli.position());
        applyTarget(heli, anchor, 0.13);
    }

    /** Cruise level relative to the terrain and waypoint (never lower than 17 blocks above). */
    public static double targetAltitude(VehicleEntity heli, BlockPos destination) {
        int here = heli.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            heli.blockPosition().getX(), heli.blockPosition().getZ());
        int there = heli.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            destination.getX(), destination.getZ());
        return Math.min(
            Math.max(Math.max(here, there) + 17, destination.getY() + 17),
            heli.level().getMaxBuildHeight() - 10);
    }

    /** Stop is allowed only after both the horizontal AND vertical legs finish. */
    public static boolean reached(VehicleEntity heli, BlockPos destination) {
        double dx = heli.getX() - (destination.getX()+0.5);
        double dz = heli.getZ() - (destination.getZ()+0.5);
        return dx*dx + dz*dz <= 16.0
            && Math.abs(heli.getY()-targetAltitude(heli, destination)) <= 2.5;
    }

    public static void flyTo(VehicleEntity heli, BlockPos destination, long tick) {
        if (heli.isWreck()) return;
        prepare(heli);
        hoverTargets.remove(heli.getUUID());
        // First climb above obstacles. Horizontal movement starts only at safe altitude.
        double targetAlt = targetAltitude(heli, destination);
        boolean stillClimbing = heli.getY() < targetAlt - 2.5;
        Vec3 target = stillClimbing
            ? new Vec3(heli.getX(), targetAlt, heli.getZ())
            : new Vec3(destination.getX()+0.5, targetAlt, destination.getZ()+0.5);

        // Avoid rushing into tanks/buildings before takeoff is complete.
        applyTarget(heli, target, stillClimbing ? 0.0 : 0.19);
        if (tick % 60L == 0) {
            RonTacZ.LOGGER.info("Mi-28 RTS fixed flight: id={} pos={} destination={} altitude={} targetAltitude={} velocity={} onGround={} ascentPhase={}",
                heli.getId(), heli.blockPosition(), destination, heli.getY(), targetAlt,
                heli.getDeltaMovement().length(), heli.onGround(), stillClimbing);
        }
    }
}
