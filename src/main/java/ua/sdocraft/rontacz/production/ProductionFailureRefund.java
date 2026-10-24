package ua.sdocraft.rontacz.production;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import ua.sdocraft.rontacz.RonTacZ;

/**
 * RoN charges the entire production cost when the item enters the queue.
 * Refund it if its onComplete callback cannot create an actual world entity.
 */
public final class ProductionFailureRefund {
    private ProductionFailureRefund() {}

    public static Entity spawn(ProductionPlacement placement, ServerLevel world,
                               EntityType<? extends Unit> type, String item) {
        try {
            // A spacious outdoor spawn avoids our custom barracks/hangar
            // foundations, interior blocks, and roof trapping newly trained units.
            Entity result=placement.produceUnit(world,type,placement.ownerName,false);
            if(result!=null && !result.isRemoved() && world.getEntity(result.getId())==result)
                return result;
            if(result!=null && !result.isRemoved()) result.discard();
            RonTacZ.LOGGER.error("RTS production failed: {} did not spawn; owner={} origin={}",
                item,placement.ownerName,placement.originPos);
        } catch (RuntimeException ex) {
            RonTacZ.LOGGER.error("RTS production failed: {} owner={} origin={}",
                item,placement.ownerName,placement.originPos,ex);
        }
        return null;
    }

    public static void refund(ProductionPlacement placement, ServerLevel world,
                              ResourceCost cost, String item) {
        // Cost was already debited when RoN started the training queue.
        ResourcesServerEvents.addSubtractResources(new Resources(
            placement.ownerName,cost.food,cost.wood,cost.ore,cost.emerald
        ));
        HudClientboundPacket.showTempMessageI18n(
            placement.ownerName,"error.rontacz.production_refunded"
        );
        RonTacZ.LOGGER.warn("Refunded failed RTS production: item={} owner={} origin={} food={} wood={} ore={} emerald={}",
            item,placement.ownerName,placement.originPos,
            cost.food,cost.wood,cost.ore,cost.emerald);
    }
}
