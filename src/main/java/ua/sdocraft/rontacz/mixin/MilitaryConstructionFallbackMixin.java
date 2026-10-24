package ua.sdocraft.rontacz.mixin;

import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Unique;
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
 * Stuck workers often remain registered as builders even though they cannot
 * reach the expanded footprint. Accelerate both construction paths, with or
 * without a worker. Vanilla buildings and completed building repairs untouched.
 */
@Mixin(value=ProductionPlacement.class, remap=false)
public abstract class MilitaryConstructionFallbackMixin {
    @Unique private boolean rontacz$hangarQueueInitialized;

    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private void rontacz$ensurePaidMilitaryBuildingCanFinish(Level world, CallbackInfo ci) {
        if (world.isClientSide()) return;
        ProductionPlacement building=(ProductionPlacement)(Object)this;
        boolean military=building.getBuilding()==Buildings.BARRACKS
            || building.getBuilding()==Buildings.BLACKSMITH;
        if (!military) return;

        // The previous hangar blueprint had no blocks matching Blacksmith's
        // vanilla starter materials (oak planks / cobblestone). If an unfinished
        // hangar was saved before the fix, its queue may contain only scaffolds.
        // Seed the ENTIRE real blueprint once per placement. This retains
        // already placed blocks and any scaffolding still awaiting placement.
        // Works with existing incomplete hangars loaded from older worlds.
        if (building.getBuilding() == Buildings.BLACKSMITH && !building.isBuilt
                && !rontacz$hangarQueueInitialized && world instanceof ServerLevel server) {
            rontacz$hangarQueueInitialized = true;
            building.queueAllBlocks(server);
            building.maxBlocksPerTick = Math.max(building.maxBlocksPerTick, 4);
            building.selfBuilding = true;
            RonTacZ.LOGGER.info(
                "RTS hangar seeded real blueprint: owner={} origin={} existing={}/{}",
                building.ownerName, building.originPos,
                building.getBlocksPlaced(), building.getBlocksTotal());
        }

        if (building.isBuilt) {
            if (building.selfBuilding) building.selfBuilding=false;
            return;
        }
        // RoN's getBuilders() counts a worker with a building goal even if it
        // cannot path to the 23x21 hangar. v0.5.3 mistakenly waited for zero.
        if (building.tickAge > 60) {
            if (!building.selfBuilding) {
                RonTacZ.LOGGER.warn(
                    "RTS military auto-construction active: owner={} origin={} structure={} age={} workers={} built={}/{}",
                    building.ownerName, building.originPos,
                    building.getBuilding().getClass().getSimpleName(),building.tickAge,
                    building.getBuilders().size(),building.getBlocksPlaced(),
                    building.getBlocksTotal());
            }
            building.selfBuilding = true;
            // When workers are assigned, selfBuilding is not consulted by
            // RoN's building tick. Force its construction timer to expire,
            // allowing one real block of the blueprint every server tick.
            building.msToNextBuild = Math.min(building.msToNextBuild, 0f);
            building.maxBlocksPerTick = Math.max(building.maxBlocksPerTick, 2);
        }
    }
}
