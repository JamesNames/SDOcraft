package ua.sdocraft.rontacz.production;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.IUnitProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import com.solegendary.reignofnether.building.production.StopProductionButton;
import com.solegendary.reignofnether.hud.buttons.UnitSpawnButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import ua.sdocraft.rontacz.entity.ModEntities;
import java.util.ArrayList;
import java.util.List;

public final class RiflemanProd extends ProductionItem implements IUnitProductionItem {
    public static final String ITEM_NAME = "Rifleman";
    public static final ResourceCost COST = ResourceCosts.PILLAGER;
    private static final ResourceLocation ICON =
         new ResourceLocation("reignofnether", "textures/mobheads/pillager.png");

    public RiflemanProd() {
        super(COST);
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide()) {
                placement.produceUnit((ServerLevel) level, ModEntities.RIFLEMAN.get(), placement.ownerName, true);
            }
        };
    }
    @Override
    public String getItemName() { return ITEM_NAME; }
    @Override
    public UnitSpawnButton getPlaceButton() { return null; }
    @Override
    public StartProductionButton getStartButton(ProductionPlacement placement, Keybinding hotkey) {
        List<FormattedCharSequence> tooltip = new ArrayList<>(List.of(
            Component.literal("Rifleman [TaCZ AK-47]").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
            ResourceCosts.getFormattedCost(COST),
            ResourceCosts.getFormattedPopAndTime(COST),
            FormattedCharSequence.forward("", Style.EMPTY),
            Component.literal("RTS-controlled soldier with AK-47").getVisualOrderText()
        ));
        return new StartProductionButton(ITEM_NAME, ICON, hotkey, () -> false, () -> true, tooltip, this);
    }
    @Override
    public StopProductionButton getCancelButton(ProductionPlacement placement, boolean first) {
        return new StopProductionButton(ITEM_NAME, ICON, placement, this, first);
    }
}
