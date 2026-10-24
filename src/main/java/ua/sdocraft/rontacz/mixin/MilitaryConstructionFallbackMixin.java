package ua.sdocraft.rontacz.mixin;

import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.sdocraft.rontacz.RonTacZ;

/**
 * The 15x13 barracks and 23x21 hangar are larger than vanilla RoN footprints.
 * On difficult terrain, a worker can get stuck before reaching the outside
 * build perimeter, leaving a paid-for building at 0%.
 *
 * After six seconds, give these two custom buildings a slow fallback build
 * whenever no builder is actively constructing. Regular villager building and
 * normal repairs keep working unchanged.
 */
@Mixin(value=ProductionPlacement.class, remap=false)
public abstract class MilitaryConstructionFallbackMixin {
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private void rontacz$ensurePaidMilitaryBuildingCanFinish(Level world, CallbackInfo ci) {
        if (world.isClientSide()) return;
        ProductionPlacement building=(ProductionPlacement)(Object)this;
        boolean military=building.getBuilding()==Buildings.BARRACKS
            || building.getBuilding()==Buildings.BLACKSMITH;
        if (!military) return;

        if (building.isBuilt) {
            if (building.selfBuilding) building.selfBuilding=false;
            return;
        }
        if (building.tickAge > 120 && building.getBuilders().isEmpty()) {
            if (!building.selfBuilding) {
                building.selfBuilding=true;
                RonTacZ.LOGGER.warn("Military construction fallback started: owner={} origin={} building={} age={}",
                    building.ownerName,building.originPos,building.getBuilding().getClass().getSimpleName(),building.tickAge);
            }
        }
        if (building.selfBuilding && !building.getBuilders().isEmpty())
            building.selfBuilding=false;
    }
}
