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
import ua.sdocraft.rontacz.RonTacZ;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ArmyUnitProd extends ProductionItem implements IUnitProductionItem {
    private final String display;
    private final boolean tank;
    private final Supplier<? extends EntityType<?>> entityType;
    private static final ResourceLocation ICON =
        new ResourceLocation("reignofnether","textures/mobheads/pillager.png");

    public ArmyUnitProd(String display, Supplier<? extends EntityType<?>> entityType, boolean tank) {
        super(ResourceCosts.PILLAGER);
        this.display=display;
        this.entityType=entityType;
        this.tank=tank;
        this.onComplete=(Level world,ProductionPlacement placement)->{
            if (world.isClientSide()) return;
            ServerLevel server=(ServerLevel)world;
            @SuppressWarnings("unchecked")
            EntityType<? extends com.solegendary.reignofnether.unit.interfaces.Unit> type =
                (EntityType<? extends com.solegendary.reignofnether.unit.interfaces.Unit>)(Object)this.entityType.get();
            var unit=placement.produceUnit(server,type,placement.ownerName,true);
            if (tank && unit instanceof TankCrewUnit crew) TankSpawner.spawnTank(server,crew);
        };
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
           Component.literal(tank?"M1A2 Abrams with RTS crew":"TaCZ gun combat unit").getVisualOrderText()
        ));
        return new StartProductionButton(display,ICON,hotkey,()->false,()->true,tooltip,this);
    }
    @Override public StopProductionButton getCancelButton(ProductionPlacement building,boolean first) {
       return new StopProductionButton(display,ICON,building,this,first);
    }
}
