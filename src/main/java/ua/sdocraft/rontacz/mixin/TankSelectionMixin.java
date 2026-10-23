package ua.sdocraft.rontacz.mixin;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.unit.NonUnitClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.sdocraft.rontacz.entity.TankCrewUnit;

import java.util.Comparator;

/** RoN normally skips all passengers. Make ONLY our tank driver selectable. */
@Mixin(value = UnitClientEvents.class, remap = false)
public abstract class TankSelectionMixin {
    @Shadow private static void markSelectedUnitsChanged() { throw new AssertionError(); }

    @Inject(method = "addPreselectedUnit", at = @At("HEAD"), cancellable = true, remap = false)
    private static void rontacz$hoverMountedCrew(LivingEntity unit, CallbackInfo ci) {
        if (!(unit instanceof TankCrewUnit) || !unit.isPassenger()) return;
        if (FogOfWarClientEvents.isInBrightChunk(unit)) {
            UnitClientEvents.getPreselectedUnits().add(unit);
            markSelectedUnitsChanged();
        }
        ci.cancel();
    }

    @Inject(method = {"addSelectedUnit", "addSelectedUnitNoSort"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void rontacz$selectMountedCrew(LivingEntity unit, CallbackInfo ci) {
        if (!(unit instanceof TankCrewUnit) || !unit.isPassenger()) return;
        CursorClientEvents.setLeftClickAction(null);
        if (FogOfWarClientEvents.isInBrightChunk(unit)) {
            UnitClientEvents.getSelectedUnits().add(unit);
            UnitClientEvents.getSelectedUnits().sort(Comparator.comparingInt(LivingEntity::getId));
            BuildingClientEvents.clearSelectedBuildings();
            NonUnitClientEvents.isMoveCheckpointGreen = true;
            markSelectedUnitsChanged();
        }
        ci.cancel();
    }
}
