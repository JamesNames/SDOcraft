package ua.sdocraft.rontacz.mixin;

import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingBlockData;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.sdocraft.rontacz.building.MilitaryBarracksStructure;
import ua.sdocraft.rontacz.building.VehicleHangarStructure;

import java.util.ArrayList;

/** Swap RoN's barracks blueprint for a military base, on client and server. */
@Mixin(value=BuildingBlockData.class,remap=false)
public abstract class MilitaryBarracksMixin {
    @Inject(method="getBuildingBlocksFromNbt(Ljava/lang/String;Lnet/minecraft/world/level/LevelAccessor;)Ljava/util/ArrayList;",
        at=@At("HEAD"),cancellable=true,remap=false)
    private static void rontacz$militaryBase(String structureName, LevelAccessor world,
                                             CallbackInfoReturnable<ArrayList<BuildingBlock>> cir) {
        if ("barracks".equals(structureName)) cir.setReturnValue(MilitaryBarracksStructure.create());
        if ("blacksmith".equals(structureName)) cir.setReturnValue(VehicleHangarStructure.create(false));
        if ("blacksmith_superior".equals(structureName)) cir.setReturnValue(VehicleHangarStructure.create(true));
    }
}
