package ua.sdocraft.rontacz.mixin;

import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.sdocraft.rontacz.entity.Mi28CrewUnit;

/**
 * Vanilla SBW rotor acceleration and the old RTS position controller were both
 * moving the Mi-28, leading to runaway altitude and jitter. While our RTS
 * pilot occupies the pilot seat, only our server flight controller moves the
 * aircraft. Native models, weapons and rendering remain untouched.
 */
@Mixin(value=VehicleEntity.class, remap=false)
public abstract class RTSHelicopterPhysicsMixin {
    @Inject(method="travel", at=@At("HEAD"), cancellable=true, remap=false)
    private void rontacz$rtspilotOwnsFlight(CallbackInfo ci) {
        VehicleEntity heli=(VehicleEntity)(Object)this;
        if (heli.getVehicleType() != VehicleType.HELICOPTER
            || !(heli.getFirstPassenger() instanceof Mi28CrewUnit)
            || heli.isWreck())
            return;
        ci.cancel();
        // Keep rotor/engine visuals without enabling native vertical thrust.
        if (!heli.level().isClientSide()) {
            if (heli.getEnergy() > 0) {
                heli.setSynchedPropellerRot(0.085f);
                heli.setPropellerRot(heli.getPropellerRot() + 3.0f);
            } else {
                heli.setSynchedPropellerRot(0f);
            }
        }
    }
}
