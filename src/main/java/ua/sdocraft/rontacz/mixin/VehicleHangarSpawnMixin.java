package ua.sdocraft.rontacz.mixin;

import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.villagers.Blacksmith;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The old Blacksmith exit coordinates lie inside the expanded hangar wall.
 * Use the center of the large open hangar gate instead, respecting building rotation.
 */
@Mixin(value=Blacksmith.class,remap=false)
public abstract class VehicleHangarSpawnMixin {
    @Inject(method="getDefaultOutdoorSpawnPoint",at=@At("HEAD"),cancellable=true,remap=false)
    private void rontacz$hangarExit(BlockPos ignored, ProductionPlacement placement,
                                    CallbackInfoReturnable<BlockPos> cir) {
        cir.setReturnValue(placement.originPos.offset(
            BuildingUtils.altRotatePos(new BlockPos(11,1,0),placement.rotation)));
    }
}
