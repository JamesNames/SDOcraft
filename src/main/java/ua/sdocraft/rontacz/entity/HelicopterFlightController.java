package ua.sdocraft.rontacz.entity;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import ua.sdocraft.rontacz.RonTacZ;

/**
 * Experimental independent RTS helicopter autopilot for Superb Warfare 0.8.9.2.
 *
 * The native helicopter physics performs lift/thrust/yaw/roll; the RTS pilot
 * adjusts rotor power and simulated mouse input toward the target.
 * No terrain-following route planning or collision avoidance is claimed.
 */
public final class HelicopterFlightController {
    private HelicopterFlightController() { }

    /** Keep the rotor powered when stopped in mid-air (do not drop the aircraft). */
    public static void hold(VehicleEntity heli) {
        if (heli.isWreck()) return;
        heli.setEngineStart(true);
        heli.setEngineStartOver(true);
        heli.setHoverMode(false);
        // Native SBW helicopter vertical physics: rotor power ~0.12 balances gravity.
        float power = (float) Mth.clamp(
            0.122 - heli.getDeltaMovement().y * 0.22,
            0.07, 0.155
        );
        heli.setPower(power);
        float pitchInput = Mth.clamp(-heli.getXRot() * 0.5f, -4f, 4f);
        heli.mouseInput(0, pitchInput);
        heli.processInput((short) 0);
    }

    public static void flyTo(VehicleEntity heli, BlockPos destination, long tick) {
        if (heli.isWreck()) return;
        heli.setEngineStart(true);
        heli.setEngineStartOver(true);
        heli.setHoverMode(false);
        if (heli.getEnergy() < 20000) heli.setEnergy(heli.getMaxEnergy());

        double dx = destination.getX() + 0.5 - heli.getX();
        double dz = destination.getZ() + 0.5 - heli.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        // Keep air clearance above both current terrain and destination terrain.
        int terrainHere = heli.level().getHeight(
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, heli.blockPosition().getX(), heli.blockPosition().getZ());
        int terrainThere = heli.level().getHeight(
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, destination.getX(), destination.getZ());
        double targetAltitude = Math.min(
            Math.max(Math.max(terrainHere, terrainThere) + 18d, destination.getY() + 18d),
            heli.level().getMaxBuildHeight() - 8d
        );
        double altitudeError = targetAltitude - heli.getY();

        // Vertical feedback controller: stronger thrust to take off/ascend, then
        // reduce power near altitude. Clamp to avoid unreasonable acceleration.
        float power = (float) Mth.clamp(
            0.122 + altitudeError * 0.0027 - heli.getDeltaMovement().y * 0.19,
            0.065, 0.172
        );
        heli.setPower(power);

        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float yawError = Mth.wrapDegrees(desiredYaw - heli.getYRot());

        // Native heli code reads mouseMoveSpeedX to yaw; tank left/right
        // keys alter lateral roll instead, so do not reuse tracked steering.
        float yawInput = Mth.clamp(yawError * 0.42f, -5f, 5f);
        // When nearly at the destination, level off rather than overshooting.
        float desiredPitch = horizontalDistance > 10 && Math.abs(yawError) < 38f
            ? 12f : 0f;
        float pitchInput = Mth.clamp((desiredPitch - heli.getXRot()) * 0.48f, -4f, 4f);
        heli.mouseInput(yawInput, pitchInput);
        heli.processInput((short) 0);

        // On ground, SBW mouse yaw is deliberately slow. Point into the desired
        // takeoff heading without setting the position or bypassing collisions.
        if (heli.onGround() && horizontalDistance > 10)
            heli.setYRot(Mth.approachDegrees(heli.getYRot(), desiredYaw, 3f));

        if (tick % 80L == 0L) {
            RonTacZ.LOGGER.info(
                "Mi-28 RTS flight: id={} pos={} destination={} altTarget={} altError={} headingError={} power={} speed={} airborne={}",
                heli.getId(), heli.blockPosition(), destination, targetAltitude,
                altitudeError, yawError, power, heli.getDeltaMovement().length(), !heli.onGround()
            );
        }
    }
}
