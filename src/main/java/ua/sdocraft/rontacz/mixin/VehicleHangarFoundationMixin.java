package ua.sdocraft.rontacz.mixin;

import com.solegendary.reignofnether.building.buildings.villagers.Blacksmith;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RoN's Blacksmith normally starts by queuing OAK_PLANKS / COBBLESTONE.
 * Our hangar blueprint instead has a SMOOTH_STONE / POLISHED_DEEPSLATE
 * foundation, so there were NO initial hangar blocks queued after scaffolds.
 * After scaffolds disappeared RoN could delete the never-started building.
 *
 * Preserve the original seed block types, adding our actual foundation blocks.
 */
@Mixin(value = Blacksmith.class, remap = false)
public abstract class VehicleHangarFoundationMixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void rontacz$startMilitaryFoundation(CallbackInfo ci) {
        Blacksmith blacksmith = (Blacksmith)(Object)this;
        if (!blacksmith.startingBlockTypes.contains(Blocks.SMOOTH_STONE))
            blacksmith.startingBlockTypes.add(Blocks.SMOOTH_STONE);
        if (!blacksmith.startingBlockTypes.contains(Blocks.POLISHED_DEEPSLATE))
            blacksmith.startingBlockTypes.add(Blocks.POLISHED_DEEPSLATE);
        if (!blacksmith.startingBlockTypes.contains(Blocks.YELLOW_CONCRETE))
            blacksmith.startingBlockTypes.add(Blocks.YELLOW_CONCRETE);
    }
}
