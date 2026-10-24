package ua.sdocraft.rontacz.mixin;

import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.sdocraft.rontacz.entity.Mi28CrewUnit;

/**
 * RTS autopilot owns ALL movement of its aircraft. Native VehicleEntity.tick()
 * invokes both travel() and an additional gravity/move pass inside baseTick().
 * Prior versions canceled only travel(), causing an unwanted gravity step every tick.
 */
@Mixin(value = VehicleEntity.class, remap = false)
public abstract class RTSHelicopterPhysicsMixin {
    private static boolean isRTSMi28(VehicleEntity vehicle) {
        return vehicle.getVehicleType() == VehicleType.HELICOPTER
            && vehicle.getFirstPassenger() instanceof Mi28CrewUnit
            && !vehicle.isWreck();
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true, remap = false)
    private void rontacz$disableNativeRotorFlight(CallbackInfo ci) {
        VehicleEntity heli = (VehicleEntity)(Object)this;
        if (!isRTSMi28(heli)) return;
        ci.cancel();
        if (!heli.level().isClientSide()) {
            if (heli.getEnergy() > 0) {
                heli.setSynchedPropellerRot(0.085f);
                heli.setPropellerRot(heli.getPropellerRot() + 3.0f);
            } else heli.setSynchedPropellerRot(0f);
        }
    }

    /** Invoked for the inherited Entity.move call in SBW's baseTick(). */
    @Redirect(method = "baseTick", at = @At(value = "INVOKE",
        target = "Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
        remap = true), require = 0, remap = false)
    private void rontacz$skipSecondMovement(VehicleEntity heli, MoverType mover, Vec3 movement) {
        if (!isRTSMi28(heli)) heli.move(mover, movement);
        else heli.setDeltaMovement(Vec3.ZERO);
    }

    /** Some Forge runtime versions reference Entity.move() rather than VehicleEntity.move(). */
    @Redirect(method = "baseTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
        remap = true), require = 0, remap = false)
    private void rontacz$skipEntitySecondMovement(VehicleEntity heli, MoverType mover, Vec3 movement) {
        if (!isRTSMi28(heli)) heli.move(mover, movement);
        else heli.setDeltaMovement(Vec3.ZERO);
    }
}
