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
 * Predictable RTS flight controller, applied once each server-level tick.
 * Native SBW helicopter travel is cancelled only while our Mi-28 RTS pilot
 * occupies the first seat. This controller handles all movement, with bounded
 * velocity, acceleration, altitude and yaw. No teleportation through blocks.
 */
public final class HelicopterFlightController {
    private HelicopterFlightController() { }

    private static final double CRUISE_SPEED = 0.30; // blocks/tick
    private static final double UP_SPEED = 0.19;
    private static final double DOWN_SPEED = 0.13;
    private static final double ACCELERATION = 0.024;
    private static final double MAX_WORLD_ALT_MARGIN = 12;
    private static final double ALT_CLEARANCE = 17;

    private static final Map<UUID, FlightState> flightStates = new HashMap<>();
    private static final class FlightState {
        Vec3 velocity=Vec3.ZERO;
        Vec3 anchor;
    }

    private static FlightState state(VehicleEntity heli) {
        return flightStates.computeIfAbsent(heli.getUUID(), ignored->new FlightState());
    }

    public static void release(VehicleEntity heli) {
        flightStates.remove(heli.getUUID());
        heli.setNoGravity(false);
        heli.setEngineStart(false);
        heli.setEngineStartOver(false);
        heli.setSynchedPropellerRot(0f);
        heli.setPower(0f);
        heli.mouseInput(0,0);
        heli.processInput((short)0);
    }

    public static void newOrder(VehicleEntity heli) {
        state(heli).anchor=null;
    }

    private static void prepare(VehicleEntity heli) {
        if (heli.isWreck()) return;
        if (heli.getEnergy() < 20000) heli.setEnergy(heli.getMaxEnergy());
        heli.setNoGravity(true);
        heli.setEngineStart(true);
        heli.setEngineStartOver(true);
        heli.setHoverMode(false);
        // SBW's native movement is cancelled by RTSHelicopterPhysicsMixin.
        // This power is for engine status only, never used to produce movement.
        heli.setPower(0.04f);
        heli.processInput((short)0);
        heli.mouseInput(0,0);
    }

    public static double targetAltitude(VehicleEntity heli, BlockPos destination) {
        int here = heli.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            heli.blockPosition().getX(), heli.blockPosition().getZ());
        int there = heli.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            destination.getX(), destination.getZ());
        double required=Math.max(Math.max(here,there)+ALT_CLEARANCE,
            destination.getY()+ALT_CLEARANCE);
        return Mth.clamp(required, heli.level().getMinBuildHeight()+5,
            heli.level().getMaxBuildHeight()-MAX_WORLD_ALT_MARGIN);
    }

    public static boolean reached(VehicleEntity heli, BlockPos destination) {
        double dx=heli.getX()-destination.getX()-0.5;
        double dz=heli.getZ()-destination.getZ()-0.5;
        return dx*dx+dz*dz <= 12.25
            && Math.abs(heli.getY()-targetAltitude(heli,destination)) <= 1.5;
    }

    /** Desired velocity is proportional to distance; smooth approach limits jerk. */
    private static double smooth(double current, double desired) {
        return Mth.clamp(desired,current-ACCELERATION,current+ACCELERATION);
    }

    private static void step(VehicleEntity heli, Vec3 dest, FlightState flight, boolean verticalOnly) {
        Vec3 pos=heli.position();
        Vec3 delta=dest.subtract(pos);
        double horizontal=Math.hypot(delta.x, delta.z);
        double desiredVx=verticalOnly ? 0 : Mth.clamp(delta.x*0.065,-CRUISE_SPEED,CRUISE_SPEED);
        double desiredVz=verticalOnly ? 0 : Mth.clamp(delta.z*0.065,-CRUISE_SPEED,CRUISE_SPEED);
        double desiredVy=Mth.clamp(delta.y*0.11,-DOWN_SPEED,UP_SPEED);
        if (horizontal>0.8 && !verticalOnly) {
            float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
            heli.setYRot(Mth.approachDegrees(heli.getYRot(),yaw,4.5f));
        }

        Vec3 vel=flight.velocity;
        vel=new Vec3(smooth(vel.x,desiredVx),smooth(vel.y,desiredVy),smooth(vel.z,desiredVz));
        // Avoid diagonal horizontal movement being faster than the speed cap.
        double speedXZ=Math.hypot(vel.x,vel.z);
        if (speedXZ>CRUISE_SPEED) vel=new Vec3(vel.x*CRUISE_SPEED/speedXZ,vel.y,
            vel.z*CRUISE_SPEED/speedXZ);
        // Stop near waypoint to avoid oscillation and 'bouncing off a ceiling'.
        if (horizontal<0.15 && Math.abs(delta.y)<0.15) vel=Vec3.ZERO;

        Vec3 before=heli.position();
        heli.move(MoverType.SELF,vel);
        Vec3 moved=heli.position().subtract(before);
        // Collision-aware: immediately cancel blocked velocity instead of
        // repeatedly pushing against a wall or ceiling.
        flight.velocity=moved;
        heli.setDeltaMovement(Vec3.ZERO);
        heli.hasImpulse=true;
        heli.setXRot(Mth.approach(heli.getXRot(), (float)(-moved.horizontalDistance()*13),1.3f));
        heli.setZRot(0f);
    }

    public static void hold(VehicleEntity heli) {
        if (heli.isWreck()) return;
        prepare(heli);
        FlightState s=state(heli);
        if (s.anchor == null) s.anchor=heli.position();
        // Don't preserve an anchor above the world ceiling.
        s.anchor=new Vec3(s.anchor.x,
            Math.min(s.anchor.y,heli.level().getMaxBuildHeight()-MAX_WORLD_ALT_MARGIN),
            s.anchor.z);
        step(heli,s.anchor,s,false);
    }

    public static void flyTo(VehicleEntity heli, BlockPos destination, long tick) {
        if (heli.isWreck()) return;
        prepare(heli);
        FlightState s=state(heli);
        s.anchor=null;
        double altitude=targetAltitude(heli,destination);
        double horizontal=Math.hypot(heli.getX()-destination.getX()-0.5,
            heli.getZ()-destination.getZ()-0.5);
        boolean ascending=heli.getY() < altitude-2.0;
        Vec3 waypoint=ascending
            ? new Vec3(heli.getX(),altitude,heli.getZ())
            : new Vec3(destination.getX()+0.5,altitude,destination.getZ()+0.5);
        step(heli,waypoint,s,ascending);

        if (tick % 60 == 0) RonTacZ.LOGGER.info(
            "Mi-28 RTS flight v0.4.3: id={} xyz={} altitude={} goal={} goalY={} ascending={} moving={} actualSpeed={}",
            heli.getId(),heli.blockPosition(),heli.getY(),destination,altitude,
            ascending,horizontal>3.5,s.velocity.length());
    }
}
