package ua.sdocraft.rontacz.production;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import com.solegendary.reignofnether.building.production.StopProductionButton;
import com.solegendary.reignofnether.building.production.IUnitProductionItem;
import com.solegendary.reignofnether.hud.buttons.UnitSpawnButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCosts;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import ua.sdocraft.rontacz.entity.TankCrewUnit;
import ua.sdocraft.rontacz.entity.ModEntities;
import ua.sdocraft.rontacz.entity.RiflemanUnit;
import ua.sdocraft.rontacz.faction.MilitaryFaction;
import ua.sdocraft.rontacz.RonTacZ;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ArmyUnitProd extends ProductionItem implements IUnitProductionItem {
    private final String display;
    private final String vehicleId;
    private final Supplier<? extends EntityType<?>> entityType;
    private static final ResourceLocation ICON =
        new ResourceLocation("reignofnether","textures/mobheads/pillager.png");

    public ArmyUnitProd(String display, Supplier<? extends EntityType<?>> entityType, boolean tank) {
        this(display, entityType, tank ? "m1a2" : null);
    }

    public ArmyUnitProd(String display, Supplier<? extends EntityType<?>> entityType, String vehicleId) {
        super(ResourceCosts.PILLAGER);
        this.display=display;
        this.entityType=entityType;
        this.vehicleId=vehicleId;
        this.onComplete=(Level world,ProductionPlacement placement)->{
            if (world.isClientSide()) return;
            ServerLevel server=(ServerLevel)world;
            @SuppressWarnings("unchecked")
            EntityType<? extends com.solegendary.reignofnether.unit.interfaces.Unit> type =
                (EntityType<? extends com.solegendary.reignofnether.unit.interfaces.Unit>)(Object)this.entityType.get();
            var unit=placement.produceUnit(server,type,placement.ownerName,true);
            if (unit instanceof RiflemanUnit soldier)
                MilitaryFaction.applyToUnit(server, soldier);
            if (vehicleId != null && unit instanceof TankCrewUnit crew)
                TankSpawner.spawnVehicle(server, crew, vehicleId);
        };
    }
    @Override public boolean canProduce(ProductionPlacement placement) {
        return (vehicleId == null || MilitaryFaction.canRecruit(placement.getLevel(), placement.ownerName, vehicleId))
            && super.canProduce(placement);
    }

    @Override public String getProduceErrorMsg(ProductionPlacement placement) {
        if (vehicleId != null && !MilitaryFaction.canRecruit(placement.getLevel(), placement.ownerName, vehicleId))
            return "error.rontacz.wrong_faction_vehicle";
        return super.getProduceErrorMsg(placement);
    }

    @Override public String getItemName() {return display;}
    @Override public UnitSpawnButton getPlaceButton() {
       return new UnitSpawnButton(display,ICON,List.of(Component.literal(display).getVisualOrderText()));
    }
    @Override public StartProductionButton getStartButton(ProductionPlacement building,Keybinding hotkey) {
        List<FormattedCharSequence> tooltip=new ArrayList<>(List.of(
           Component.literal(display).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
           ResourceCosts.getFormattedCost(ResourceCosts.PILLAGER),
           ResourceCosts.getFormattedPopAndTime(ResourceCosts.PILLAGER),
           FormattedCharSequence.EMPTY,
           Component.literal(vehicleId != null ? "Superb Warfare RTS vehicle: " + vehicleId : "TaCZ gun combat unit").getVisualOrderText()
        ));
        return new StartProductionButton(display,ICON,hotkey,()->false,
            () -> vehicleId == null || MilitaryFaction.canRecruit(building.getLevel(), building.ownerName, vehicleId),
            tooltip,this);
    }
    @Override public StopProductionButton getCancelButton(ProductionPlacement building,boolean first) {
       return new StopProductionButton(display,ICON,building,this,first);
    }
}
